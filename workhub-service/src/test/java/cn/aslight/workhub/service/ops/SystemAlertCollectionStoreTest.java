package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.dao.ops.*;
import cn.aslight.workhub.dao.notification.NotificationMapper;
import cn.aslight.workhub.dao.system.UserMapper;
import cn.aslight.workhub.model.ops.*;
import cn.aslight.workhub.service.notification.*;
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
import java.sql.Connection;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** 实际新增DDL、Mapper、事务、事件和通知入库；ELK页面为本地明确夹具。 */
class SystemAlertCollectionStoreTest {
    private final ObjectMapper json=new ObjectMapper(); private final MutableClock clock=new MutableClock();
    private Connection connection; private JdbcTemplate jdbc; private SystemAlertCollectionMapper mapper,realMapper;
    private SystemAlertScopeIngestionMapper ingestion; private SystemAlertCollectionStore store;
    private SystemAlertScopeEntity scope;
    @BeforeEach void setup() throws Exception {
        var db=new JdbcDataSource(); db.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;LOCK_TIMEOUT=5000"); connection=db.getConnection();jdbc=new JdbcTemplate(db);
        Path schema=Path.of("../workhub-bootstrap/src/main/resources/db/schema/mysql");
        String base=Files.readString(schema.resolve("V1__init.sql")); create(base,"ops_system_alert_event");create(base,"sys_notification");
        jdbc.execute("ALTER TABLE ops_system_alert_event ADD event_category VARCHAR(32) DEFAULT 'SYSTEM_ERROR'");
        String scopes=Files.readString(schema.resolve("V20260821_113000__add_system_alert_business_line_scope.sql"));
        create(scopes,"ops_system_alert_scope_sync_state");create(scopes,"ops_system_alert_scope");
        jdbc.update("INSERT INTO ops_system_alert_scope(id,business_line_code,environment_code,watch_mode,enabled) VALUES(1,'BL000012','TEST','ALL',1)");
        jdbc.execute(Files.readString(schema.resolve("V20261104_155928__add_system_alert_collection_progress.sql")));
        var config=new Configuration();config.setMapUnderscoreToCamelCase(true);
        for(var type:List.of(SystemAlertCollectionMapper.class,SystemAlertScopeIngestionMapper.class,NotificationMapper.class))config.addMapper(type);
        var factory=new SqlSessionFactoryBean();factory.setDataSource(db);factory.setConfiguration(config);var sql=new SqlSessionTemplate(factory.getObject());
        realMapper=sql.getMapper(SystemAlertCollectionMapper.class);mapper=mock(SystemAlertCollectionMapper.class,delegates(realMapper));
        ingestion=sql.getMapper(SystemAlertScopeIngestionMapper.class);
        var notifications=new NotificationService(sql.getMapper(NotificationMapper.class),mock(NotificationSchemaInitializer.class));
        var users=mock(UserMapper.class);when(users.findBusinessLineMembers(anyString())).thenReturn(List.of());
        var target=new SystemAlertCollectionStore(mapper,ingestion,users,notifications,json,clock,ZoneOffset.UTC);
        var tx=new TransactionInterceptor();tx.setTransactionManager(new DataSourceTransactionManager(db));tx.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        var proxy=new ProxyFactory(target);proxy.setProxyTargetClass(true);proxy.addAdvice(tx);store=(SystemAlertCollectionStore)proxy.getProxy();
        scope=new SystemAlertScopeEntity();scope.setId(1L);scope.setEnabled(true);scope.setBusinessLineCode("BL000012");scope.setEnvironmentCode("TEST");scope.setWatchMode("ALL");scope.setIndexPatterns(List.of("workhub-*"));scope.setIndexPatternExpression("workhub-*");
    }
    private org.mockito.stubbing.Answer<Object> delegates(Object target) { return org.mockito.AdditionalAnswers.delegatesTo(target); }
    @AfterEach void close() throws Exception { connection.close(); }
    @Test void partialPageDoesNotAdvanceCoverageAndSameTimestampEventsResumeAfterRestart() {
        var one=claim();Instant same=clock.instant().minusSeconds(10);
        store.commit(one,page(List.of(event("one","a",same)),false,false));
        var middle=mapper.find(1L);assertNull(middle.coveredUntil());assertEquals("PARTIAL",ingestion.findSyncState(1L).lastStatus());assertEquals(List.of(1L),store.pending());
        clock.advance(1);var two=claim();assertEquals(one.state().windowId(),two.state().windowId());assertEquals(one.state().toTime(),two.state().toTime());assertNotNull(store.position(two.state()));
        store.commit(two,page(List.of(event("two","a",same)),true,false));
        assertEquals(2,count("ops_system_alert_event"));assertEquals(2,count("sys_notification"));assertEquals(2,mapper.pages(one.state().windowId()).size());
        var finalState=mapper.find(1L);assertEquals("COMPLETE",finalState.status());assertEquals(one.state().toTime(),finalState.coveredUntil());assertTrue(store.pending().isEmpty());
        assertEquals("SUCCESS",ingestion.findSyncState(1L).lastStatus());
        for(var receipt:mapper.pages(one.state().windowId())) { assertEquals(AccountingExecutionEvidenceService.hash(receipt.receiptJson()),receipt.receiptHash());assertFalse(receipt.receiptJson().contains("private-pit")); }
    }
    @Test void expiredSnapshotRestartsOriginalRangeAndDeduplicatesAlreadyCommittedEvents() {
        var first=claim();var event=event("one","a",clock.instant().minusSeconds(10));store.commit(first,page(List.of(event),false,false));
        var interrupted=claim();store.fail(interrupted,"expired",true);var restarted=claim();
        assertEquals(first.state().fromTime(),restarted.state().fromTime());assertEquals(first.state().toTime(),restarted.state().toTime());assertNotEquals(first.state().windowId(),restarted.state().windowId());assertNull(store.position(restarted.state()));
        store.commit(restarted,page(List.of(event),true,false));assertEquals(1,count("ops_system_alert_event"));assertEquals(1,count("sys_notification"));
        assertFalse(mapper.pages(first.state().windowId()).getFirst().complete());
    }
    @Test void failedReceiptRollsBackEventsNotificationsAndPosition() {
        var claim=claim();var once=new AtomicBoolean();
        doAnswer(call->{if(!once.getAndSet(true))throw new IllegalStateException("fixture write failure");return realMapper.insertPage(call.getArgument(0));}).when(mapper).insertPage(any());
        var page=page(List.of(event("one","a",clock.instant().minusSeconds(10))),false,false);
        assertThrows(IllegalStateException.class,()->store.commit(claim,page));assertEquals(0,count("ops_system_alert_event"));assertEquals(0,count("sys_notification"));assertEquals(0,count("ops_system_alert_collection_page"));
        assertNull(mapper.find(1L).positionJson());assertEquals(claim.state().version(),mapper.find(1L).version());
        store.commit(claim,page);assertEquals(1,count("ops_system_alert_event"));
    }
    @Test void nonDuplicateInsertFailureRollsBackTheWholePage() {
        var claim=claim();var events=List.of(event("valid","a",clock.instant().minusSeconds(1)),event("invalid","x".repeat(129),clock.instant().minusSeconds(1)));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->store.commit(claim,page(events,true,false)));
        assertEquals(0,count("ops_system_alert_event"));assertEquals(0,count("sys_notification"));assertTrue(mapper.pages(claim.state().windowId()).isEmpty());
        assertNull(mapper.find(1L).coveredUntil());
    }
    @Test void staleOwnerCannotCommitOrEraseSuccessor() {
        var old=claim();clock.advance(200);var current=claim();
        assertThrows(IllegalStateException.class,()->store.commit(old,page(List.of(),true,false)));
        store.fail(old,"late",true);assertEquals(current.state().ownerToken(),mapper.find(1L).ownerToken());
        store.commit(current,page(List.of(),true,false));assertEquals("COMPLETE",mapper.find(1L).status());
    }
    @Test void concurrentClaimsHaveOnlyOneOwner() throws Exception {
        try(var pool=Executors.newFixedThreadPool(2)) {
            var gate=new CountDownLatch(1);var a=pool.submit(()->{gate.await();return claim();});var b=pool.submit(()->{gate.await();return claim();});gate.countDown();
            var first=a.get(8,TimeUnit.SECONDS);var second=b.get(8,TimeUnit.SECONDS);assertTrue((first==null)!=(second==null));
        }
    }
    @Test void changedScopeRestartsDefinitionAndNeverReusesOldPosition() {
        var first=claim();store.commit(first,page(List.of(),false,false));scope.setEnvironmentCode("PROD");
        var changed=claim();assertNotEquals(first.state().definitionHash(),changed.state().definitionHash());assertNull(store.position(changed.state()));assertNull(changed.state().coveredUntil());
        assertEquals("PROD",changed.definition().scope().getEnvironmentCode());
    }
    @Test void disabledAndDeletedScopesDoNotOccupyContinuationQueue() {
        store.commit(claim(),page(List.of(),false,false));assertEquals(List.of(1L),store.pending());
        jdbc.update("UPDATE ops_system_alert_scope SET enabled=0 WHERE id=1");assertTrue(store.pending().isEmpty());
        jdbc.update("DELETE FROM ops_system_alert_scope WHERE id=1");assertTrue(store.pending().isEmpty());
    }
    @Test void completeRoundsOverlapToRecheckLateDataAndLegacyCursorIsNotProof() {
        ingestion.saveSuccess(1L,LocalDateTime.ofInstant(clock.instant().minusSeconds(1000),ZoneOffset.UTC),"legacy");
        var first=claim();assertNull(first.state().coveredUntil());assertTrue(first.state().fromTime().isBefore(now().minusMinutes(10)));
        store.commit(first,page(List.of(),true,false));clock.advance(60);var next=claim();assertEquals(first.state().toTime().minusMinutes(5),next.state().fromTime());
    }
    @Test void missingServiceOrTruncatedEarlierPageNeverBecomesSuccessfulEmptyFinalPage() {
        var first=claim();store.commit(first,page(List.of(event("unknown",null,clock.instant().minusSeconds(1))),false,true));
        store.commit(claim(),page(List.of(),true,false));assertEquals("PARTIAL",ingestion.findSyncState(1L).lastStatus());assertEquals(2,mapper.find(1L).issueCount());assertEquals(0,count("ops_system_alert_event"));
    }
    @Test void selectedScopeAndRuleClassificationKeepExistingNotificationBehavior() {
        scope.setWatchMode("SELECTED");scope.setServices(List.of(new SystemAlertScopeServiceEntity(1L,1L,"业务服务","a",true,0)));
        var rule=new SystemAlertRuleEntity();rule.setId(1L);rule.setAction("SLOW_SQL");rule.setMatchScope("ALL_TEXT");rule.setMatchMode("ANY");rule.setEnabled(true);rule.setPriority(1);rule.setKeywords(List.of("fixture"));
        var claim=store.claim(scope,List.of(rule),5,30);store.commit(claim,page(List.of(event("slow","a",clock.instant().minusSeconds(1))),true,false));
        assertEquals("SLOW_SQL",jdbc.queryForObject("SELECT event_category FROM ops_system_alert_event",String.class));assertEquals("业务服务",jdbc.queryForObject("SELECT subsystem_name FROM ops_system_alert_event",String.class));assertEquals(0,count("sys_notification"));
        clock.advance(1);scope.setServices(List.of());assertThrows(IllegalArgumentException.class,()->claim());
    }
    private SystemAlertCollectionStore.Claim claim() { return store.claim(scope,List.of(),5,30); }
    private SystemAlertLogSource.ErrorPage page(List<ElkSystemAlertLog> events,boolean complete,boolean truncated) {
        var position=complete?null:new SystemAlertLogSource.Position("private-pit","[1,2]","a".repeat(64));
        return new SystemAlertLogSource.ErrorPage(events,position,complete,truncated,new ElasticsearchSystemAlertClient.QueryReceipt("a".repeat(64),clock.instant(),clock.instant(),"b".repeat(64),List.of()));
    }
    private ElkSystemAlertLog event(String id,String service,Instant at) {return new ElkSystemAlertLog(id,service,"ERROR","title","fixture",null,null,null,null,at);}
    private LocalDateTime now(){return LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC);}
    private int count(String table){return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class);}
    private void create(String sql,String table){var match=Pattern.compile("(?s)CREATE TABLE (?:IF NOT EXISTS )?`"+table+"`.*?;",Pattern.CASE_INSENSITIVE).matcher(sql);assertTrue(match.find());jdbc.execute(match.group().replaceAll("(?i)ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", ""));}
    private static class MutableClock extends Clock {
        private volatile Instant at=Instant.parse("2026-11-04T08:00:00Z");public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return at;}void advance(long seconds){at=at.plusSeconds(seconds);}
    }
}
