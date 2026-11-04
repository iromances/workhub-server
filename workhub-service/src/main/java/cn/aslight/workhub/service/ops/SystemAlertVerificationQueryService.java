package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.dao.ops.*;
import cn.aslight.workhub.model.ops.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;

/** 不改变普通看板；Agent查询在同一数据库快照中核对来源、完整分页和实际事件。 */
@Service
public class SystemAlertVerificationQueryService {
    private static final int MAX_PAGES=256, MAX_REFERENCES=20_000;
    private final SystemAlertService alerts;
    private final SystemAlertScopeMapper scopes;
    private final SystemAlertScopeIngestionMapper ingestion;
    private final SystemAlertCollectionMapper collections;
    private final ElkAlertRuntimeConfigService configs;
    private final ObjectMapper json;
    private final Clock clock;
    private final ZoneId zone;

    @Autowired
    public SystemAlertVerificationQueryService(SystemAlertService alerts,SystemAlertScopeMapper scopes,
            SystemAlertScopeIngestionMapper ingestion,SystemAlertCollectionMapper collections,
            ElkAlertRuntimeConfigService configs,ObjectMapper json) {
        this(alerts,scopes,ingestion,collections,configs,json,Clock.systemUTC(),ZoneId.systemDefault());
    }
    SystemAlertVerificationQueryService(SystemAlertService alerts,SystemAlertScopeMapper scopes,
            SystemAlertScopeIngestionMapper ingestion,SystemAlertCollectionMapper collections,
            ElkAlertRuntimeConfigService configs,ObjectMapper json,Clock clock,ZoneId zone) {
        this.alerts=alerts;this.scopes=scopes;this.ingestion=ingestion;this.collections=collections;
        this.configs=configs;this.json=json;this.clock=clock;this.zone=zone;
    }

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public SystemAlertDashboardResponse query(String line,String environment,String service,LocalDateTime from,LocalDateTime to,int page,int size) {
        if(line==null || environment==null || from==null || to==null || !from.isBefore(to)
                || Duration.between(from,to).compareTo(Duration.ofDays(31))>0 || page<1 || page>10_000 || size<1 || size>30)
            throw new IllegalArgumentException("预警查询范围或分页无效");
        service=service==null || service.isBlank()?null:service.trim();
        var started=clock.instant(); var state=new Evidence();
        var result=alerts.dashboard(line,environment,service,null,null,null,from,to,page,size);
        if(result.page()!=page || result.pageSize()!=size || page!=1 || result.totalCount()==null || result.totalCount()<0
                || result.events()==null || result.totalCount()!=result.events().size() || result.events().size()>size)
            state.problems.add("QUERY_RESULTS_INCOMPLETE");
        var scope=scopes.findByIdentity(line,environment);
        var expected=service==null?List.<String>of():List.of(service);
        String definitionHash=null;
        if(scope==null || !Boolean.TRUE.equals(scope.getEnabled())) state.problems.add("COLLECTION_SCOPE_UNAVAILABLE");
        else {
            var definition=definition(scope); definitionHash=hash(json.writeValueAsString(definition)); state.rules=definition.rules();
            if(service==null) expected=SystemAlertCollectionStore.selected(scope);
            if(expected.isEmpty() || "SELECTED".equals(scope.getWatchMode()) && !SystemAlertCollectionStore.selected(scope).containsAll(expected))
                state.problems.add("SERVICE_ACTIVITY_COVERAGE_UNPROVEN");
            var latest=collections.latestComplete(scope.getId());
            if(latest!=null) { state.availableFrom=utc(latest.fromTime()); state.availableThrough=utc(latest.toTime()); }
            var current=collections.find(scope.getId());
            var end=collections.covering(scope.getId(),utcLocal(from),utcLocal(to),current==null?null:current.windowId());
            if(end==null) state.problems.add("COLLECTION_COVERAGE_UNPROVEN");
            else {
                state.windowId=end.windowId();
                try {
                    var config=configs.current(null,scope.getIndexPatternExpression());
                    if(!config.enabled()) state.problems.add("COLLECTOR_DISABLED");
                    state.target=ElasticsearchSystemAlertClient.targetBinding(config);
                    verify(state,end,definitionHash,expected,from,to,result,size);
                } catch(InvalidReceipt ex) { state.problems.add(ex.getMessage()); }
                catch(Exception ex) { state.problems.add("COLLECTION_RECEIPT_UNREADABLE"); }
            }
        }
        var definition=new SystemAlertQueryEvidence.Definition(scope==null?null:scope.getId(),line,environment,
                scope==null?null:scope.getWatchMode(),scope==null?List.of():scope.getIndexPatterns(),expected,definitionHash,zone.getId());
        var proof=new SystemAlertQueryEvidence(1,UUID.randomUUID().toString(),definition,hash(json.writeValueAsString(definition)),
                state.windowId,state.target,state.chainHash,state.pages,started,clock.instant(),state.started,state.finished,
                from.atZone(zone).toInstant(),to.atZone(zone).toInstant(),state.availableFrom,state.availableThrough,state.problems.isEmpty(),
                List.copyOf(state.problems),state.activities,state.ignoredCount,List.copyOf(state.ignored),state.rules);
        return new SystemAlertDashboardResponse(result.totalCount(),result.page(),result.pageSize(),result.subsystems(),result.summaries(),result.events(),proof);
    }

    private void verify(Evidence state,SystemAlertCollectionMapper.Page end,String definitionHash,List<String> expected,
            LocalDateTime from,LocalDateTime to,SystemAlertDashboardResponse dashboard,int size) {
        require(end.pageNumber()>0 && end.pageNumber()<=MAX_PAGES && collections.receiptBytes(end.windowId())<=8_388_608L,"COLLECTION_PROOF_LIMIT_EXCEEDED");
        var pages=collections.boundedPages(end.windowId(),MAX_PAGES+1);
        require(!pages.isEmpty() && pages.size()<=MAX_PAGES && pages.size()==end.pageNumber(),"COLLECTION_PAGE_CHAIN_INCOMPLETE");
        var expectedRows=new LinkedHashMap<Long,String>(); var sourceIds=new HashSet<String>();
        var hashes=new ArrayList<String>(); String previous=null; int references=0;
        for(int i=0;i<pages.size();i++) {
            var page=pages.get(i);
            require(page.pageNumber()==i+1 && Objects.equals(page.scopeId(),end.scopeId()) && Objects.equals(page.windowId(),end.windowId())
                    && Objects.equals(page.fromTime(),end.fromTime()) && Objects.equals(page.toTime(),end.toTime())
                    && page.complete()==(i==pages.size()-1) && Objects.equals(page.definitionHash(),definitionHash)
                    && hash(page.receiptJson()).equals(page.receiptHash()),"COLLECTION_PAGE_CHAIN_INVALID");
            var receipt=json.readValue(page.receiptJson(),SystemAlertCollectionStore.Receipt.class);
            require(receipt.definition()!=null && hash(json.writeValueAsString(receipt.definition())).equals(definitionHash)
                    && Objects.equals(receipt.recordTimeZone(),zone.getId()) && receipt.complete()==page.complete()
                    && Objects.equals(previous,receipt.previousPositionHash())
                    && (receipt.complete()?receipt.nextPositionHash()==null:digest(receipt.nextPositionHash())),"COLLECTION_DEFINITION_CHANGED");
            if(receipt.contentTruncated()) state.problems.add("TRUNCATED_EVIDENCE");
            var execution=receipt.execution();
            require(execution!=null && Objects.equals(state.target,execution.targetBindingHash()) && digest(execution.snapshotHash())
                    && execution.startedAt()!=null && execution.finishedAt()!=null && !execution.finishedAt().isBefore(execution.startedAt())
                    && !execution.startedAt().isBefore(utc(end.toTime())) && !execution.finishedAt().isAfter(clock.instant())
                    && (state.finished==null || !execution.startedAt().isBefore(state.finished)),"COLLECTION_EXECUTION_INVALID");
            if(execution.activityIssue()!=null) state.problems.add(execution.activityIssue());
            if(state.started==null) state.started=execution.startedAt(); state.finished=execution.finishedAt();
            if(receipt.complete()) state.activities=execution.activities()==null?List.of():execution.activities().stream().filter(a->expected.contains(a.serviceName())).toList();
            require(receipt.events()!=null,"COLLECTION_CONTENT_UNPROVEN"); references+=receipt.events().size();
            require(references<=MAX_REFERENCES,"COLLECTION_PROOF_LIMIT_EXCEEDED");
            for(var event:receipt.events()) {
                require(event.sourceEventId()!=null && !event.sourceEventId().isBlank() && sourceIds.add(event.sourceEventId())
                        && digest(event.payloadHash()) && event.occurredAt()!=null
                        && !event.occurredAt().isBefore(utc(end.fromTime())) && !event.occurredAt().isAfter(utc(end.toTime())),"COLLECTION_EVENT_INVALID");
                if(event.serviceName()==null || event.serviceName().isBlank()) { state.problems.add("UNRESOLVED_SERVICE"); continue; }
                if(!expected.contains(event.serviceName())) continue;
                LocalDateTime at=event.storedOccurredAt()==null?LocalDateTime.ofInstant(event.occurredAt(),zone):event.storedOccurredAt();
                if(at.isBefore(from) || at.isAfter(to)) continue;
                if("IGNORE".equals(event.action())) {
                    state.ignoredCount++;
                    require(event.ignoredEvent()!=null && hash(json.writeValueAsString(event.ignoredEvent())).equals(event.payloadHash())
                            && Objects.equals(event.sourceEventId(),event.ignoredEvent().sourceEventId())
                            && Objects.equals(event.serviceName(),event.ignoredEvent().serviceName())
                            && Objects.equals(event.occurredAt(),event.ignoredEvent().occurredAt()),"FILTERED_CONTENT_UNPROVEN");
                    if(state.ignored.size()<size) state.ignored.add(event.ignoredEvent());
                } else {
                    require(event.sourceMatchesStored() && event.storedId()!=null && event.storedOccurredAt()!=null && digest(event.storedHash())
                            && Set.of("SYSTEM_ERROR","SLOW_SQL").contains(event.action()),"COLLECTION_CONTENT_UNPROVEN");
                    require(expectedRows.put(event.storedId(),event.storedHash())==null,"COLLECTION_EVENT_INVALID");
                }
            }
            previous=receipt.nextPositionHash();hashes.add(page.receiptHash());
        }
        state.pages=pages.size();state.chainHash=hash(json.writeValueAsString(hashes));
        var actual=new LinkedHashMap<Long,String>();
        for(var event:dashboard.events()) {
            if(!"ELK".equals(event.sourceType()) || actual.put(event.id(),SystemAlertEventProof.hash(json,event))!=null)
                state.problems.add("QUERY_CONTENT_NOT_IN_COLLECTION");
        }
        if(!expectedRows.equals(actual) || dashboard.totalCount()!=expectedRows.size()) state.problems.add("QUERY_CONTENT_DIFFERS_FROM_COLLECTION");
        if(state.ignoredCount!=state.ignored.size() || dashboard.totalCount()+state.ignoredCount>size) state.problems.add("QUERY_RESULTS_INCOMPLETE");
        if(expected.isEmpty() || state.activities.size()!=expected.size()
                || !new HashSet<>(expected).equals(new HashSet<>(state.activities.stream().map(BusinessLogQueryEvidence.Activity::serviceName).toList())))
            state.problems.add("SERVICE_ACTIVITY_COVERAGE_UNPROVEN");
        for(var activity:state.activities) {
            if(activity.count()<=0 || activity.latestAt()==null || !digest(activity.latestEventId())) state.problems.add("NO_SERVICE_ACTIVITY");
            else if(activity.latestAt().isBefore(from.atZone(zone).toInstant()) || activity.latestAt().isAfter(to.atZone(zone).toInstant()))
                state.problems.add("ACTIVITY_OUTSIDE_QUERY_WINDOW");
        }
    }

    private SystemAlertCollectionStore.Definition definition(SystemAlertScopeEntity scope) {
        var ids=List.of(scope.getId());scope.setServices(ingestion.findServices(ids));
        scope.setIndexPatterns(ingestion.findIndexPatterns(ids).stream().map(SystemAlertScopeIndexEntity::indexPattern).toList());
        scope.setIndexPatternExpression(String.join(",",scope.getIndexPatterns()));
        var rules=ingestion.findEnabledRules();
        if(!rules.isEmpty()) {
            var keywords=ingestion.findRuleKeywords(rules.stream().map(SystemAlertRuleEntity::getId).toList());
            for(var rule:rules) rule.setKeywords(keywords.stream().filter(k->Objects.equals(k.ruleId(),rule.getId())).map(SystemAlertRuleKeywordEntity::keyword).toList());
        }
        return new SystemAlertCollectionStore.Definition(scope,List.copyOf(rules));
    }
    private String hash(String value) { return AccountingExecutionEvidenceService.hash(value); }
    private boolean digest(String value) { return value!=null && value.matches("[a-f0-9]{64}"); }
    private Instant utc(LocalDateTime value) { return value.toInstant(ZoneOffset.UTC); }
    private LocalDateTime utcLocal(LocalDateTime value) { return LocalDateTime.ofInstant(value.atZone(zone).toInstant(),ZoneOffset.UTC); }
    private void require(boolean valid,String code) { if(!valid) throw new InvalidReceipt(code); }
    private static final class InvalidReceipt extends RuntimeException { InvalidReceipt(String code) { super(code); } }
    private static final class Evidence {
        final Set<String> problems=new LinkedHashSet<>(); final List<ElkSystemAlertLog> ignored=new ArrayList<>();
        String windowId,target,chainHash; int pages,ignoredCount; Instant started,finished,availableFrom,availableThrough;
        List<BusinessLogQueryEvidence.Activity> activities=List.of();List<SystemAlertRuleEntity> rules=List.of();
    }
}
