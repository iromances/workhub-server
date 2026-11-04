package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.dao.ops.*;
import cn.aslight.workhub.dao.system.UserMapper;
import cn.aslight.workhub.model.ops.*;
import cn.aslight.workhub.model.system.UserOptionResponse;
import cn.aslight.workhub.service.notification.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** 分页事件、通知、不可变回执与续读位置同事务；外部ELK读取在事务之外。 */
@Service
public class SystemAlertCollectionStore {
    private final SystemAlertCollectionMapper mapper;
    private final SystemAlertScopeIngestionMapper ingestionMapper;
    private final UserMapper userMapper;
    private final NotificationService notificationService;
    private final ObjectMapper json;
    private final Clock clock;
    private final ZoneId zoneId;

    @Autowired
    public SystemAlertCollectionStore(SystemAlertCollectionMapper mapper, SystemAlertScopeIngestionMapper ingestionMapper,
            UserMapper userMapper, NotificationService notificationService, ObjectMapper json) {
        this(mapper,ingestionMapper,userMapper,notificationService,json,Clock.systemUTC(),ZoneId.systemDefault());
    }
    SystemAlertCollectionStore(SystemAlertCollectionMapper mapper, SystemAlertScopeIngestionMapper ingestionMapper,
            UserMapper userMapper, NotificationService notificationService, ObjectMapper json, Clock clock, ZoneId zoneId) {
        this.mapper=mapper; this.ingestionMapper=ingestionMapper; this.userMapper=userMapper;
        this.notificationService=notificationService; this.json=json; this.clock=clock; this.zoneId=zoneId;
    }
    public List<Long> pending() { return mapper.pending(now()); }

    @Transactional
    public Claim claim(SystemAlertScopeEntity scope,List<SystemAlertRuleEntity> rules,int lookbackMinutes,int timeoutSeconds) {
        if (!Boolean.TRUE.equals(scope.getEnabled()) || scope.getIndexPatterns().isEmpty()) throw new IllegalArgumentException("预警范围未启用或没有索引");
        var services=selected(scope);
        if ("SELECTED".equals(scope.getWatchMode()) && services.isEmpty()) throw new IllegalArgumentException("指定子系统模式下没有启用的子系统");
        String definition=json.writeValueAsString(new Definition(scope,List.copyOf(rules))), hash=hash(definition);
        LocalDateTime at=now(), from=at.minusMinutes(Math.max(1,lookbackMinutes));
        var legacy=ingestionMapper.findSyncState(scope.getId());
        if (legacy!=null && legacy.lastOccurredAt()!=null) {
            LocalDateTime prior=LocalDateTime.ofInstant(legacy.lastOccurredAt().atZone(zoneId).toInstant(),ZoneOffset.UTC).minusMinutes(Math.max(1,lookbackMinutes));
            if (prior.isBefore(from)) from=prior;
        }
        mapper.ensure(new SystemAlertCollectionState(scope.getId(),UUID.randomUUID().toString(),definition,hash,from,at,null,null,null,"ACTIVE",null,null,0,0,0,at));
        var row=mapper.lock(scope.getId());
        if (row.ownerToken()!=null && row.leaseUntil()!=null && row.leaseUntil().isAfter(at)) return null;
        if (!hash(row.definitionJson()).equals(row.definitionHash())) throw new IllegalStateException("采集定义摘要不一致");
        boolean changed=!hash.equals(row.definitionHash()), fresh=changed || "COMPLETE".equals(row.status());
        LocalDateTime covered=changed?null:row.coveredUntil();
        if (fresh) from=covered==null?at.minusMinutes(Math.max(1,lookbackMinutes)):covered.minusMinutes(Math.max(1,lookbackMinutes));
        else from=row.fromTime();
        var next=new SystemAlertCollectionState(row.scopeId(),fresh?UUID.randomUUID().toString():row.windowId(),definition,hash,
                from,fresh?at:row.toTime(),fresh?null:row.positionJson(),fresh?null:row.latestOccurredAt(),covered,"ACTIVE",
                UUID.randomUUID().toString(),at.plusSeconds(4L*Math.max(1,timeoutSeconds)+30),fresh?0:row.issueCount(),fresh?0:row.pageNumber(),row.version(),at);
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
        var batches=new LinkedHashMap<String,NotificationBatch>(); var records=new ArrayList<EventReference>();
        var names=new HashMap<String,SystemAlertScopeServiceEntity>(); scope.getServices().forEach(item->names.put(item.serviceName(),item));
        var allowed=selected(scope); long issues=row.issueCount()+(page.contentTruncated()?1:0)+(execution.activityIssue()==null?0:1); int ignored=0,missing=0;
        LocalDateTime latest=row.latestOccurredAt(); int ignoredBytes=0; boolean truncated=page.contentTruncated();
        for(var event:page.events()) {
            if(event.occurredAt().isBefore(row.fromTime().toInstant(ZoneOffset.UTC)) || event.occurredAt().isAfter(row.toTime().toInstant(ZoneOffset.UTC)))
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
                    if(inserted>0) batches.computeIfAbsent(event.serviceName(),key->new NotificationBatch()).record(event,category);
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
        for(var entry:batches.entrySet()) {
            var configured=names.get(entry.getKey());
            notifyRecipients(scope,configured==null?entry.getKey():configured.subsystemName(),entry.getValue(),recipients(scope.getBusinessLineCode()));
        }
        String nextPosition=page.next()==null?null:json.writeValueAsString(page.next());
        String receipt=json.writeValueAsString(new Receipt(definition,execution,List.copyOf(records),page.complete(),truncated,
                row.positionJson()==null?null:hash(row.positionJson()),nextPosition==null?null:hash(nextPosition),zoneId.getId()));
        mapper.insertPage(new SystemAlertCollectionMapper.Page(UUID.randomUUID().toString(),row.scopeId(),row.windowId(),row.pageNumber()+1,
                row.definitionHash(),row.fromTime(),row.toTime(),page.complete(),receipt,hash(receipt),now()));
        update(new SystemAlertCollectionState(row.scopeId(),row.windowId(),row.definitionJson(),row.definitionHash(),row.fromTime(),row.toTime(),nextPosition,
                latest,page.complete()?row.toTime():row.coveredUntil(),page.complete()?"COMPLETE":"ACTIVE",null,null,issues,row.pageNumber()+1,row.version(),now()));
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
                restart?0:row.issueCount(),restart?0:row.pageNumber(),row.version(),now()));
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

    private List<String> recipients(String businessLineCode) {
        List<String> users = userMapper.findBusinessLineMembers(businessLineCode).stream()
                .map(UserOptionResponse::userName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .toList();
        return users.isEmpty() ? List.of("admin") : users;
    }

    private void notifyRecipients(SystemAlertScopeEntity scope, String subsystemName, NotificationBatch batch,
                                  List<String> recipients) {
        if (batch.count() == 0 || batch.latest() == null) {
            return;
        }
        ElkSystemAlertLog latest = batch.latest();
        String title = subsystemName + " 出现 " + batch.count() + " 条错误";
        String content = """
                业务线：%s
                环境：%s
                子系统：%s
                服务：%s
                本轮新增：%s 条
                时间范围：%s 至 %s
                异常类型：%s
                Trace ID：%s
                最近错误摘要：%s
                """.formatted(
                scope.getBusinessLineCode(), scope.getEnvironmentCode(),
                subsystemName, latest.serviceName(), batch.count(),
                LocalDateTime.ofInstant(batch.firstOccurredAt(), zoneId),
                LocalDateTime.ofInstant(batch.lastOccurredAt(), zoneId),
                display(latest.errorType()), display(latest.traceId()),
                display(abbreviate(latest.message(), 2_000)));
        String dedupeKey = "ELK:SCOPE:BATCH:" + scope.getId() + ":" + latest.serviceName()
                + ":" + latest.sourceEventId();
        for (String recipient : recipients) {
            notificationService.createSystemNotification(recipient, "ELK_ERROR", title, content,
                    scope.getBusinessLineCode(), scope.getEnvironmentCode(), dedupeKey);
        }
    }

    private static final class NotificationBatch {
        private int count;
        private int slowSqlCount;
        private Instant firstOccurredAt;
        private Instant lastOccurredAt;
        private ElkSystemAlertLog latest;

        void record(ElkSystemAlertLog event, SystemAlertEventCategory eventCategory) {
            if (eventCategory == SystemAlertEventCategory.SLOW_SQL) {
                slowSqlCount++;
                return;
            }
            count++;
            if (firstOccurredAt == null || event.occurredAt().isBefore(firstOccurredAt)) {
                firstOccurredAt = event.occurredAt();
            }
            if (lastOccurredAt == null || event.occurredAt().isAfter(lastOccurredAt)) {
                lastOccurredAt = event.occurredAt();
                latest = event;
            }
        }

        int count() {
            return count;
        }

        int slowSqlCount() {
            return slowSqlCount;
        }

        Instant firstOccurredAt() {
            return firstOccurredAt;
        }

        Instant lastOccurredAt() {
            return lastOccurredAt;
        }

        ElkSystemAlertLog latest() {
            return latest;
        }
    }

    private String display(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        return value.substring(0, maxLength);
    }
}
