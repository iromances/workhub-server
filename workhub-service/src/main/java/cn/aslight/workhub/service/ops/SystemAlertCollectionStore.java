package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.dao.ops.*;
import cn.aslight.workhub.model.ops.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** 分页事件、不可变回执与续读位置同事务；外部ELK读取在事务之外。REQ-172f8541：预警站内信已删除（改由数字员工巡检通知）。 */
@Service
public class SystemAlertCollectionStore {
    /** 同一窗口连续失败达到该次数后，下次领取按新窗口重开，避免确定性失败无限重试。 */
    private static final int ESCALATE_AFTER_FAILURES = 5;
    private final SystemAlertCollectionMapper mapper;
    private final SystemAlertScopeIngestionMapper ingestionMapper;
    private final ObjectMapper json;
    private final Clock clock;
    private final ZoneId zoneId;

    @Autowired
    public SystemAlertCollectionStore(SystemAlertCollectionMapper mapper, SystemAlertScopeIngestionMapper ingestionMapper,
            ObjectMapper json) {
        this(mapper,ingestionMapper,json,Clock.systemUTC(),ZoneId.systemDefault());
    }
    SystemAlertCollectionStore(SystemAlertCollectionMapper mapper, SystemAlertScopeIngestionMapper ingestionMapper,
            ObjectMapper json, Clock clock, ZoneId zoneId) {
        this.mapper=mapper; this.ingestionMapper=ingestionMapper; this.json=json; this.clock=clock; this.zoneId=zoneId;
    }
    public List<Long> pending() { return mapper.pending(now()); }

    @Transactional
    public Claim claim(SystemAlertScopeEntity scope,List<SystemAlertRuleEntity> rules,int lookbackMinutes,int timeoutSeconds) {
        if (!Boolean.TRUE.equals(scope.getEnabled()) || scope.getIndexPatterns().isEmpty()) throw new IllegalArgumentException("预警范围未启用或没有索引");
        var services=selected(scope);
        if ("SELECTED".equals(scope.getWatchMode()) && services.isEmpty()) throw new IllegalArgumentException("指定子系统模式下没有启用的子系统");
        String definition=json.writeValueAsString(new Definition(scope,List.copyOf(rules))), hash=hash(definition);
        // 窗口边界对齐到整毫秒，避免ES毫秒级date字段对range边界取整后返回亚毫秒越界文档。
        LocalDateTime at=now(), to=at.truncatedTo(ChronoUnit.MILLIS), from=to.minusMinutes(Math.max(1,lookbackMinutes));
        var legacy=ingestionMapper.findSyncState(scope.getId());
        if (legacy!=null && legacy.lastOccurredAt()!=null) {
            LocalDateTime prior=LocalDateTime.ofInstant(legacy.lastOccurredAt().atZone(zoneId).toInstant(),ZoneOffset.UTC)
                    .minusMinutes(Math.max(1,lookbackMinutes)).truncatedTo(ChronoUnit.MILLIS);
            if (prior.isBefore(from)) from=prior;
        }
        mapper.ensure(new SystemAlertCollectionState(scope.getId(),UUID.randomUUID().toString(),definition,hash,from,to,null,null,null,"ACTIVE",null,null,0,0,0,at,0,null));
        var row=mapper.lock(scope.getId());
        if (row.ownerToken()!=null && row.leaseUntil()!=null && row.leaseUntil().isAfter(at)) return null;
        if (!hash(row.definitionJson()).equals(row.definitionHash())) throw new IllegalStateException("采集定义摘要不一致");
        boolean changed=!hash.equals(row.definitionHash()), fresh=changed || "COMPLETE".equals(row.status())
                || ("ERROR".equals(row.status()) && row.consecutiveFailures()>=ESCALATE_AFTER_FAILURES);
        LocalDateTime covered=changed?null:row.coveredUntil();
        if (fresh) from=covered==null?to.minusMinutes(Math.max(1,lookbackMinutes)):covered.minusMinutes(Math.max(1,lookbackMinutes)).truncatedTo(ChronoUnit.MILLIS);
        else from=row.fromTime();
        var next=new SystemAlertCollectionState(row.scopeId(),fresh?UUID.randomUUID().toString():row.windowId(),definition,hash,
                from,fresh?to:row.toTime(),fresh?null:row.positionJson(),fresh?null:row.latestOccurredAt(),covered,"ACTIVE",
                UUID.randomUUID().toString(),at.plusSeconds(4L*Math.max(1,timeoutSeconds)+30),fresh?0:row.issueCount(),fresh?0:row.pageNumber(),
                row.version(),at,fresh?0:row.consecutiveFailures(),fresh?null:row.lastFailureMessage());
        update(next);
        return new Claim(mapper.find(row.scopeId()),json.readValue(definition,Definition.class));
    }

    @Transactional
    public void commit(Claim claim,SystemAlertLogSource.ErrorPage page) {
        var row=owned(claim); var definition=claim.definition(); var scope=definition.scope();
        if (page==null || page.execution()==null || page.complete()!=(page.next()==null)) throw new IllegalArgumentException("缺少完整分页回执");
        var execution=page.execution();
        if (execution.targetBindingHash()==null || !execution.targetBindingHash().matches("[a-f0-9]{64}")
                || execution.startedAt()==null || execution.finishedAt()==null || execution.finishedAt().isBefore(execution.startedAt()))
            throw new IllegalArgumentException("分页执行目标或时间无效");
        var previous=position(row);
        if ((previous!=null && !previous.targetHash().equals(execution.targetBindingHash()))
                || (page.next()!=null && !page.next().targetHash().equals(execution.targetBindingHash())))
            throw new IllegalArgumentException("分页执行目标变化，不能拼接区间");
        var records=new ArrayList<EventReference>();
        var names=new HashMap<String,SystemAlertScopeServiceEntity>(); scope.getServices().forEach(item->names.put(item.serviceName(),item));
        var allowed=selected(scope); long issues=row.issueCount()+(page.contentTruncated()?1:0)+(execution.activityIssue()==null?0:1); int ignored=0,missing=0;
        LocalDateTime latest=row.latestOccurredAt(); int ignoredBytes=0; boolean truncated=page.contentTruncated();
        var windowFrom=row.fromTime().toInstant(ZoneOffset.UTC).minusMillis(SystemAlertLogSource.BOUNDARY_TOLERANCE_MS);
        var windowTo=row.toTime().toInstant(ZoneOffset.UTC).plusMillis(SystemAlertLogSource.BOUNDARY_TOLERANCE_MS);
        for(var event:page.events()) {
            if(event.occurredAt().isBefore(windowFrom) || event.occurredAt().isAfter(windowTo))
                throw new IllegalArgumentException("日志不属于固定采集区间");
            var utc=LocalDateTime.ofInstant(event.occurredAt(),ZoneOffset.UTC);
            if(latest==null || utc.isAfter(latest)) latest=utc;
            String action; SystemAlertStoredEvent stored=null; boolean matched=false;
            if(event.serviceName()==null || event.serviceName().isBlank()) { action="UNRESOLVED_SERVICE"; missing++; issues++; }
            else {
                if("SELECTED".equals(scope.getWatchMode()) && !allowed.contains(event.serviceName())) throw new IllegalArgumentException("日志服务超出采集范围");
                var classified=SystemAlertEventClassifier.classify(event,definition.rules()); action=classified.name();
                if(classified==SystemAlertRuleAction.IGNORE) ignored++;
                else {
                    var category=classified==SystemAlertRuleAction.SLOW_SQL?SystemAlertEventCategory.SLOW_SQL:SystemAlertEventCategory.SYSTEM_ERROR;
                    var configured=names.get(event.serviceName()); String name=configured==null?event.serviceName():configured.subsystemName();
                    int inserted;
                    try { inserted=ingestionMapper.insertElkEvent(scope,name,event,LocalDateTime.ofInstant(event.occurredAt(),zoneId),category.name()); }
                    catch (org.springframework.dao.DuplicateKeyException duplicate) { inserted=0; }
                    stored=ingestionMapper.findStoredEvent(scope.getBusinessLineCode(),scope.getEnvironmentCode(),event.serviceName(),event.sourceEventId());
                    matched=SystemAlertEventProof.matches(stored,scope,event,category.name(),zoneId);
                    if (!matched) issues++;
                    // 只忽略已核实唯一键的重复，其他入库错误必须使整页回滚。
                }
            }
            String eventJson=json.writeValueAsString(event); ElkSystemAlertLog ignoredEvent=null;
            if("IGNORE".equals(action)) {
                int bytes=eventJson.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
                if(bytes<=16_384 && ignoredBytes+bytes<=524_288) { ignoredEvent=event; ignoredBytes+=bytes; }
                else { truncated=true; issues++; }
            }
            records.add(new EventReference(event.sourceEventId(),event.serviceName(),event.occurredAt(),hash(eventJson),action,
                    stored==null?null:stored.id(),stored==null?null:stored.occurredAt(),stored==null?null:SystemAlertEventProof.hash(json,stored),matched,
                    ignoredEvent));
        }
        String nextPosition=page.next()==null?null:json.writeValueAsString(page.next());
        String receipt=json.writeValueAsString(new Receipt(definition,execution,List.copyOf(records),page.complete(),truncated,
                row.positionJson()==null?null:hash(row.positionJson()),nextPosition==null?null:hash(nextPosition),zoneId.getId()));
        mapper.insertPage(new SystemAlertCollectionMapper.Page(UUID.randomUUID().toString(),row.scopeId(),row.windowId(),row.pageNumber()+1,
                row.definitionHash(),row.fromTime(),row.toTime(),page.complete(),receipt,hash(receipt),now()));
        update(new SystemAlertCollectionState(row.scopeId(),row.windowId(),row.definitionJson(),row.definitionHash(),row.fromTime(),row.toTime(),nextPosition,
                latest,page.complete()?row.toTime():row.coveredUntil(),page.complete()?"COMPLETE":"ACTIVE",null,null,issues,row.pageNumber()+1,row.version(),now(),0,null));
        String message="本页处理"+page.events().size()+"条，过滤"+ignored+"条，缺少服务名"+missing+"条；"+(page.complete()?"区间已读完":"区间未读完，等待续读")+"，累计证据缺项"+issues;
        if(page.complete() && issues==0) ingestionMapper.saveSuccess(row.scopeId(),latest==null?null:LocalDateTime.ofInstant(latest.toInstant(ZoneOffset.UTC),zoneId),message);
        else ingestionMapper.savePartial(row.scopeId(),message);
    }

    @Transactional
    public void fail(Claim claim,String message,boolean restart) {
        var row=mapper.lock(claim.state().scopeId());
        if(!matches(row,claim)) return;
        update(new SystemAlertCollectionState(row.scopeId(),restart?UUID.randomUUID().toString():row.windowId(),row.definitionJson(),row.definitionHash(),
                row.fromTime(),row.toTime(),restart?null:row.positionJson(),restart?null:row.latestOccurredAt(),row.coveredUntil(),"ERROR",null,null,
                restart?0:row.issueCount(),restart?0:row.pageNumber(),row.version(),now(),row.consecutiveFailures()+1,abbreviate(message,1_000)));
        ingestionMapper.saveFailure(row.scopeId(),message);
    }
    private SystemAlertCollectionState owned(Claim claim) {
        var row=mapper.lock(claim.state().scopeId());
        if(!matches(row,claim) || row.leaseUntil()==null || !row.leaseUntil().isAfter(now())) throw new IllegalStateException("采集所有者或租约已失效");
        return row;
    }
    private boolean matches(SystemAlertCollectionState row,Claim claim) {
        return row!=null && row.version()==claim.state().version() && Objects.equals(row.ownerToken(),claim.state().ownerToken()) && row.ownerToken()!=null;
    }
    private void update(SystemAlertCollectionState value) { if(mapper.update(value)!=1) throw new IllegalStateException("采集进度已变化"); }
    public SystemAlertLogSource.Position position(SystemAlertCollectionState row) { return row.positionJson()==null?null:json.readValue(row.positionJson(),SystemAlertLogSource.Position.class); }
    public static List<String> selected(SystemAlertScopeEntity scope) {
        return "SELECTED".equals(scope.getWatchMode())?scope.getServices().stream().filter(item->Boolean.TRUE.equals(item.enabled())).map(SystemAlertScopeServiceEntity::serviceName).toList():List.of();
    }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant().truncatedTo(ChronoUnit.MICROS),ZoneOffset.UTC); }
    private String hash(String value) { return AccountingExecutionEvidenceService.hash(value); }
    public record Definition(SystemAlertScopeEntity scope,List<SystemAlertRuleEntity> rules) { }
    public record Claim(SystemAlertCollectionState state,Definition definition) { }
    public record EventReference(String sourceEventId,String serviceName,Instant occurredAt,String payloadHash,String action,
            Long storedId,LocalDateTime storedOccurredAt,String storedHash,boolean sourceMatchesStored,ElkSystemAlertLog ignoredEvent) { }
    public record Receipt(Definition definition,ElasticsearchSystemAlertClient.QueryReceipt execution,List<EventReference> events,
                          boolean complete,boolean contentTruncated,String previousPositionHash,String nextPositionHash,String recordTimeZone) { }


    private String display(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        return value.substring(0, maxLength);
    }
}
