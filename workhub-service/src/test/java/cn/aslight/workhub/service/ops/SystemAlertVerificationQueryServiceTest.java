package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.dao.ops.*;
import cn.aslight.workhub.dao.notification.NotificationMapper;
import cn.aslight.workhub.model.ops.*;
import cn.aslight.workhub.model.agent.*;
import cn.aslight.workhub.model.autonomy.AgentObservationEntity;
import cn.aslight.workhub.observability.ElkAlertProperties;
import cn.aslight.workhub.service.agent.*;
import cn.aslight.workhub.service.autonomy.AgentObservationService;
import cn.aslight.workhub.service.notification.*;
import cn.aslight.workhub.service.system.SystemAuditService;
import org.apache.ibatis.session.Configuration;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.mybatis.spring.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import tools.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.sql.Connection;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 实际采集事务/Mapper/看板查询/脱敏/复查解释器贯通；外部来源仅为本地夹具。 */
class SystemAlertVerificationQueryServiceTest {
    private final ObjectMapper json=new ObjectMapper(); private final MutableClock clock=new MutableClock();
    private Connection connection; private JdbcTemplate jdbc; private SystemAlertCollectionMapper collections;
    private SystemAlertScopeIngestionMapper ingestion; private SystemAlertScopeMapper scopes;
    private SystemAlertCollectionStore store; private SystemAlertVerificationQueryService query;
    private ElkAlertRuntimeConfigService configs; private ElkAlertProperties properties;
    @BeforeEach void setup() throws Exception {
        var db=new JdbcDataSource();db.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL");connection=db.getConnection();jdbc=new JdbcTemplate(db);
        Path schema=Path.of("../workhub-bootstrap/src/main/resources/db/schema/mysql");
        String base=Files.readString(schema.resolve("V1__init.sql"));
        for(String table:List.of("ops_system_alert_event","ops_system_alert_subsystem","sys_notification")) create(base,table);
        jdbc.execute("ALTER TABLE ops_system_alert_event ADD event_category VARCHAR(32) DEFAULT 'SYSTEM_ERROR'");
        String scopeSql=Files.readString(schema.resolve("V20260821_113000__add_system_alert_business_line_scope.sql"));
        for(String table:List.of("ops_system_alert_scope","ops_system_alert_scope_service","ops_system_alert_scope_index","ops_system_alert_scope_sync_state"))create(scopeSql,table);
        String rules=Files.readString(schema.resolve("V20260805_180000__add_system_alert_filter_rules.sql"));
        create(rules,"ops_system_alert_rule");create(rules,"ops_system_alert_rule_keyword");
        jdbc.execute(Files.readString(schema.resolve("V20261104_155928__add_system_alert_collection_progress.sql")));
        jdbc.execute(Files.readString(schema.resolve("V20261112_140809__add_system_alert_collection_failure_escalation.sql")));
        jdbc.update("INSERT INTO ops_system_alert_scope(id,business_line_code,environment_code,watch_mode,enabled) VALUES(1,'BL000012','TEST','ALL',1)");
        jdbc.update("INSERT INTO ops_system_alert_scope_index(scope_id,index_pattern) VALUES(1,'workhub-*')");
        var cfg=new Configuration();cfg.setMapUnderscoreToCamelCase(true);
        for(var type:List.of(SystemAlertCollectionMapper.class,SystemAlertScopeIngestionMapper.class,SystemAlertScopeMapper.class,SystemAlertMapper.class,NotificationMapper.class))cfg.addMapper(type);
        var factory=new SqlSessionFactoryBean();factory.setDataSource(db);factory.setConfiguration(cfg);var sql=new SqlSessionTemplate(factory.getObject());
        collections=sql.getMapper(SystemAlertCollectionMapper.class);ingestion=sql.getMapper(SystemAlertScopeIngestionMapper.class);scopes=sql.getMapper(SystemAlertScopeMapper.class);
        var tx=new TransactionInterceptor();tx.setTransactionManager(new DataSourceTransactionManager(db));tx.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        store=proxy(new SystemAlertCollectionStore(collections,ingestion,json,clock,ZoneOffset.UTC),tx);
        properties=new ElkAlertProperties();properties.setEnabled(true);properties.setBaseUrl("http://127.0.0.1:1");configs=new ElkAlertRuntimeConfigService(properties);
        var alerts=new SystemAlertService(sql.getMapper(SystemAlertMapper.class),mock(SystemAuditService.class));
        query=proxy(new SystemAlertVerificationQueryService(alerts,scopes,ingestion,collections,configs,json,clock,ZoneOffset.UTC),tx);
    }
    @SuppressWarnings("unchecked") private <T> T proxy(T value,TransactionInterceptor tx) {var proxy=new ProxyFactory(value);proxy.setProxyTargetClass(true);proxy.addAdvice(tx);return (T)proxy.getProxy();}
    @AfterEach void close() throws Exception {connection.close();}

    @Test void persistedEmptyErrorWindowWithRealServiceActivitySuppliesUsableAgentEvidence() {
        commit(List.of(),true,activities("a")); var result=read("a");assertTrue(result.executionEvidence().complete(),result.executionEvidence().problems().toString());
        assertEquals(0,result.totalCount());assertEquals(1,result.executionEvidence().pageCount());
        assertTrue(inspect(result).problems().isEmpty(),inspect(result).problems().toString());
        assertEquals(clock.instant().minusSeconds(1),inspect(result).dataAt());
    }
    @Test void actualErrorRowsRemainVisibleForAgentJudgmentAndDeletedRowsNeverProveRecovery() {
        commit(List.of(event("one","a")),true,activities("a"));var result=read("a");
        assertTrue(result.executionEvidence().complete(),result.executionEvidence().problems().toString());assertEquals(1,result.totalCount());assertTrue(inspect(result).problems().isEmpty());
        jdbc.update("DELETE FROM ops_system_alert_event");var deleted=read("a");assertEquals(0,deleted.totalCount());assertProblem(deleted,"QUERY_CONTENT_DIFFERS_FROM_COLLECTION");
    }
    @Test void modifiedPayloadOrAdditionalUncollectedRowsAreNotConsistentEvidence() {
        commit(List.of(event("one","a")),true,activities("a"));jdbc.update("UPDATE ops_system_alert_event SET message='changed'");assertProblem(read("a"),"QUERY_CONTENT_DIFFERS_FROM_COLLECTION");
        jdbc.update("UPDATE ops_system_alert_event SET message='fixture'");
        ingestion.insertElkEvent(scope(),"a",event("extra","a"),now().minusSeconds(1),"SYSTEM_ERROR");assertProblem(read("a"),"QUERY_CONTENT_DIFFERS_FROM_COLLECTION");
    }
    @Test void changedDuplicateSourceContentIsRetainedButCannotCertify() {
        commit(List.of(event("one","a")),true,activities("a"));clock.advance(1);
        var changed=new ElkSystemAlertLog("one","a","ERROR","title","mutated",null,null,null,null,clock.instant().minusSeconds(2));
        commit(List.of(changed),true,activities("a"));assertEquals("PARTIAL",ingestion.findSyncState(1L).lastStatus());assertProblem(read("a"),"COLLECTION_CONTENT_UNPROVEN");
    }
    @Test void ignoredErrorsAreIncludedAndSanitizedInsteadOfDisappearing() {
        jdbc.update("INSERT INTO ops_system_alert_rule(id,rule_name,action,match_scope,match_mode) VALUES(1,'fixture','IGNORE','MESSAGE','ANY')");
        jdbc.update("INSERT INTO ops_system_alert_rule_keyword(rule_id,keyword) VALUES(1,'fixture')");
        var log=new ElkSystemAlertLog("one","a","ERROR","title","fixture Authorization: Bearer private-secret",null,null,null,null,clock.instant().minusSeconds(1));
        commit(List.of(log),true,activities("a"));var result=read("a");assertTrue(result.executionEvidence().complete(),result.executionEvidence().problems().toString());
        assertEquals(0,result.totalCount());assertEquals(1,result.executionEvidence().ignoredCount());assertEquals(log,result.executionEvidence().ignoredEvents().getFirst());
        var safe=new AgentToolData(json).sanitize(result);assertFalse(safe.toString().contains("private-secret"));assertTrue(inspect(result).problems().isEmpty());
        jdbc.update("UPDATE ops_system_alert_rule_keyword SET keyword='different'");assertProblem(read("a"),"COLLECTION_PAGE_CHAIN_INVALID");
    }
    @Test void pagesMustBeCompleteAndLinkedEvenWhenFinalPageIsEmpty() {
        commit(List.of(event("one","a")),false,List.of());var middle=read("a");assertProblem(middle,"COLLECTION_COVERAGE_UNPROVEN");
        commit(List.of(),true,activities("a"));assertTrue(read("a").executionEvidence().complete());
        jdbc.update("DELETE FROM ops_system_alert_collection_page WHERE page_number=1");assertProblem(read("a"),"COLLECTION_PAGE_CHAIN_INCOMPLETE");
    }
    @Test void receiptTamperingAndMissingLegacyFieldsAreExplicitlyUnproven() {
        commit(List.of(),true,activities("a"));var page=collections.latestComplete(1L);
        jdbc.update("UPDATE ops_system_alert_collection_page SET receipt_hash='bad'");assertProblem(read("a"),"COLLECTION_PAGE_CHAIN_INVALID");
        var legacy=(tools.jackson.databind.node.ObjectNode)json.readTree(page.receiptJson());legacy.remove("recordTimeZone");String old=json.writeValueAsString(legacy);
        jdbc.update("UPDATE ops_system_alert_collection_page SET receipt_json=?,receipt_hash=?",old,AccountingExecutionEvidenceService.hash(old));assertProblem(read("a"),"COLLECTION_DEFINITION_CHANGED");
    }
    @Test void selectedFullScopeRequiresActivityForEveryServiceButAllModeNeverInventsExpectedServices() {
        commit(List.of(),true,activities("a"));assertProblem(read(null),"SERVICE_ACTIVITY_COVERAGE_UNPROVEN");
        jdbc.update("UPDATE ops_system_alert_scope SET watch_mode='SELECTED'");
        for(String name:List.of("a","b")) jdbc.update("INSERT INTO ops_system_alert_scope_service(scope_id,subsystem_name,service_name) VALUES(1,?,?)",name,name);
        commit(List.of(),true,activities("a"));assertProblem(read(null),"SERVICE_ACTIVITY_COVERAGE_UNPROVEN");clock.advance(1);
        commit(List.of(),true,activities("a","b"));assertTrue(read(null).executionEvidence().complete(),read(null).executionEvidence().problems().toString());
        assertProblem(read("outside"),"SERVICE_ACTIVITY_COVERAGE_UNPROVEN");
    }
    @Test void changedTargetDisabledScopeAndUncoveredEndDoNotReuseOldCollection() {
        commit(List.of(),true,activities("a"));var before=read("a").executionEvidence();
        var later=query.query("BL000012","TEST","a",now().minusMinutes(5),now().plusSeconds(1),1,30);
        assertProblem(later,"COLLECTION_COVERAGE_UNPROVEN");assertEquals(before.availableThrough(),later.executionEvidence().availableThrough());
        properties.setBaseUrl("http://127.0.0.1:2");assertProblem(read("a"),"COLLECTION_EXECUTION_INVALID");
        jdbc.update("UPDATE ops_system_alert_scope SET enabled=0");assertProblem(read("a"),"COLLECTION_SCOPE_UNAVAILABLE");
    }
    @Test void truncatedAndPaginatedResultsCannotSupportPassingSample() {
        commit(List.of(event("one","a"),event("two","a")),true,activities("a"));
        assertProblem(query.query("BL000012","TEST","a",now().minusMinutes(5),now(),1,1),"QUERY_RESULTS_INCOMPLETE");
        assertProblem(query.query("BL000012","TEST","a",now().minusMinutes(5),now(),2,30),"QUERY_RESULTS_INCOMPLETE");
        var claim=store.claim(scope(),rules(),5,30);store.commit(claim,new SystemAlertLogSource.ErrorPage(List.of(),null,true,true,execution(activities("a"))));
        assertProblem(read("a"),"TRUNCATED_EVIDENCE");
    }
    @Test void freshQueryWithSameStoredWindowDoesNotCreateNewSourceEvent() {
        commit(List.of(),true,activities("a"));var first=read("a");var a=inspect(first);clock.advance(5);
        var same=query.query("BL000012","TEST","a",LocalDateTime.ofInstant(first.executionEvidence().from(),ZoneOffset.UTC),LocalDateTime.ofInstant(first.executionEvidence().to(),ZoneOffset.UTC),1,30);
        var b=inspect(same);assertTrue(b.problems().isEmpty());assertEquals(a.eventKey(),b.eventKey());assertEquals(a.dataAt(),b.dataAt());
    }
    @Test void previousCursorAndStoredContentHashAreValidatedSeparately() {
        commit(List.of(event("one","a")),false,List.of());commit(List.of(),true,activities("a"));
        var page=collections.latestComplete(1L);var raw=(tools.jackson.databind.node.ObjectNode)json.readTree(page.receiptJson());raw.put("previousPositionHash","0".repeat(64));
        String altered=json.writeValueAsString(raw);jdbc.update("UPDATE ops_system_alert_collection_page SET receipt_json=?,receipt_hash=? WHERE id=?",altered,AccountingExecutionEvidenceService.hash(altered),page.id());
        assertProblem(read("a"),"COLLECTION_DEFINITION_CHANGED");
    }
    @Test void oversizedReceiptWindowsAreRejectedBeforeLoadingTheirContent() {
        commit(List.of(),true,activities("a"));
        jdbc.update("UPDATE ops_system_alert_collection_page SET receipt_json=REPEAT('x',8388609)");
        assertProblem(read("a"),"COLLECTION_PROOF_LIMIT_EXCEEDED");
    }
    @Test void oversizedIgnoredContentIsExplicitlyIncompleteAndDoesNotBloatReceipts() {
        jdbc.update("INSERT INTO ops_system_alert_rule(id,rule_name,action,match_scope,match_mode) VALUES(1,'fixture','IGNORE','ALL_TEXT','ANY')");
        jdbc.update("INSERT INTO ops_system_alert_rule_keyword(rule_id,keyword) VALUES(1,'fixture')");
        var log=new ElkSystemAlertLog("one","a","ERROR","title","fixture",null,"x".repeat(20_000),null,null,clock.instant().minusSeconds(1));
        commit(List.of(log),true,activities("a"));var response=read("a");assertProblem(response,"TRUNCATED_EVIDENCE");
        assertTrue(collections.receiptBytes(collections.find(1L).windowId())<20_000);assertEquals("PARTIAL",ingestion.findSyncState(1L).lastStatus());
    }

    @Test void failedActivityQueryPreservesCollectedErrorsAndPartialState() {
        var claim=store.claim(scope(),rules(),5,30);
        var execution=new ElasticsearchSystemAlertClient.QueryReceipt(target(),clock.instant(),clock.instant(),"b".repeat(64),List.of(),"SERVICE_ACTIVITY_QUERY_FAILED");
        store.commit(claim,new SystemAlertLogSource.ErrorPage(List.of(event("one","a")),null,true,false,execution));
        assertEquals("PARTIAL",ingestion.findSyncState(1L).lastStatus());assertEquals(1,read("a").totalCount());assertProblem(read("a"),"SERVICE_ACTIVITY_QUERY_FAILED");
    }

    @Test void databaseSecondPrecisionAcceptsOnlyRealRoundingAndRejectsOtherTimeChanges() {
        var log=event("one","a");var scope=scope();var at=LocalDateTime.ofInstant(log.occurredAt(),ZoneOffset.UTC);
        var stored=new SystemAlertStoredEvent(1L,"BL000012","TEST","a","ERROR","SYSTEM_ERROR","title","fixture",null,null,null,null,at,"ELK","one");
        assertTrue(SystemAlertEventProof.matches(stored,scope,log,"SYSTEM_ERROR",ZoneOffset.UTC));
        var fraction=new ElkSystemAlertLog("one","a","ERROR","title","fixture",null,null,null,null,log.occurredAt().plusNanos(700_000_000));
        assertTrue(SystemAlertEventProof.matches(stored,scope,fraction,"SYSTEM_ERROR",ZoneOffset.UTC));
        var shifted=new ElkSystemAlertLog("one","a","ERROR","title","fixture",null,null,null,null,log.occurredAt().plusSeconds(2));
        assertFalse(SystemAlertEventProof.matches(stored,scope,shifted,"SYSTEM_ERROR",ZoneOffset.UTC));
    }

    private void assertProblem(SystemAlertDashboardResponse response,String problem) { assertFalse(response.executionEvidence().complete());assertTrue(response.executionEvidence().problems().contains(problem),response.executionEvidence().problems().toString()); }
    private SystemAlertDashboardResponse read(String service) {return query.query("BL000012","TEST",service,now().minusMinutes(5),now(),1,30);}
    private SystemAlertScopeEntity scope() { var value=scopes.findById(1L);value.setServices(ingestion.findServices(List.of(1L)));value.setIndexPatterns(List.of("workhub-*"));value.setIndexPatternExpression("workhub-*");return value; }
    private List<SystemAlertRuleEntity> rules() {var values=ingestion.findEnabledRules();for(var rule:values)rule.setKeywords(ingestion.findRuleKeywords(List.of(rule.getId())).stream().map(SystemAlertRuleKeywordEntity::keyword).toList());return values;}
    private void commit(List<ElkSystemAlertLog> events,boolean complete,List<BusinessLogQueryEvidence.Activity> activities) {
        var claim=store.claim(scope(),rules(),5,30);var next=complete?null:new SystemAlertLogSource.Position("private-pit","[1,2]",target());
        store.commit(claim,new SystemAlertLogSource.ErrorPage(events,next,complete,false,execution(activities)));
    }
    private String target() {return ElasticsearchSystemAlertClient.targetBinding(configs.current(null,"workhub-*"));}
    private ElasticsearchSystemAlertClient.QueryReceipt execution(List<BusinessLogQueryEvidence.Activity> activities) {return new ElasticsearchSystemAlertClient.QueryReceipt(target(),clock.instant(),clock.instant(),"b".repeat(64),activities);}
    private List<BusinessLogQueryEvidence.Activity> activities(String...names) {return Arrays.stream(names).map(name->new BusinessLogQueryEvidence.Activity(name,1,clock.instant().minusSeconds(1),"a".repeat(64))).toList();}
    private ElkSystemAlertLog event(String id,String service) {return new ElkSystemAlertLog(id,service,"ERROR","title","fixture",null,null,null,null,clock.instant().minusSeconds(1));}
    private LocalDateTime now(){return LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC);}
    private void create(String sql,String table){var match=Pattern.compile("(?s)CREATE TABLE (?:IF NOT EXISTS )?`"+table+"`.*?;",Pattern.CASE_INSENSITIVE).matcher(sql);assertTrue(match.find());jdbc.execute(match.group().replaceAll("(?i)ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", ""));}
    private AgentVerificationEvidence.Fact inspect(SystemAlertDashboardResponse result) {
        var scope=new AgentScope("BL000012","TEST");var at=clock.instant();var proof=result.executionEvidence();
        var context=new AgentToolContext("run","task","agent","owner",new AgentConfiguration("巡检","巡检",1L,null,List.of(scope),20,60,1),new AgentTaskSpecification("巡检","","判断恢复",List.of("REPORT"),List.of(scope)));
        String tool="monitor.system.alerts";
        String payload=json.writeValueAsString(new AgentToolData(json).sanitize(Map.of("scope",scope,"observedAt",at.toString(),"recordTimeZone","Z","query",Map.of("from",proof.from().toString(),"to",proof.to().toString()),"data",result)));
        var row=new AgentObservationEntity(UUID.randomUUID().toString(),"run","agent","key",tool,AgentObservationService.sourceType(tool,"BL000012"),"BL000012","TEST",payload,AccountingExecutionEvidenceService.hash(payload),now());
        var check=new AgentReleaseVerificationPlan.Check("health",scope,tool,"service a","已恢复",20,10,30,3,"完整证据");
        return new AgentVerificationEvidence(json).inspect(row,context,check,"/data",at.minusSeconds(120),at);
    }
    private static class MutableClock extends Clock {private Instant at=Instant.parse("2026-11-04T08:00:00Z");public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return at;}void advance(long seconds){at=at.plusSeconds(seconds);}}
}
