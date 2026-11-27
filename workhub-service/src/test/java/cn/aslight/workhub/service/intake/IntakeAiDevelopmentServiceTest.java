package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.dao.intake.DevelopmentAnalysisMapper;
import cn.aslight.workhub.dao.intake.IntakeAiDevelopmentMapper;
import cn.aslight.workhub.dao.intake.IntakeHistoryMapper;
import cn.aslight.workhub.dao.intake.IntakeMapper;
import cn.aslight.workhub.model.agent.AgentTaskCreateRequest;
import cn.aslight.workhub.model.agent.AgentTaskResponse;
import cn.aslight.workhub.model.intake.DevelopmentAnalysisEntity;
import cn.aslight.workhub.model.intake.IntakeRecordEntity;
import cn.aslight.workhub.service.agent.AgentWorkbenchService;
import cn.aslight.workhub.service.system.SysConfigService;
import org.apache.ibatis.session.Configuration;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IntakeAiDevelopmentServiceTest {
    private final Queue<Runnable> queued = new ArrayDeque<>();
    private final IntakeRecordEntity intake = new IntakeRecordEntity();
    private Connection keepAlive;
    private JdbcTemplate jdbc;
    private TransactionTemplate stageTransaction;
    private IntakeAiDevelopmentService service;
    private AgentWorkbenchService agents;
    private IntakeMapper intakes;
    private IntakeHistoryMapper history;

    @Test void designPreflightRejectsMissingAgentBeforeCreatingAnyExecution() {
        intake.setAiAgentId(null);
        assertTrue(assertThrows(IllegalArgumentException.class, () -> service.validateBeforeDesignConfirmed(277L, "admin")).getMessage().contains("请先选择数字员工"));
        assertTrue(queued.isEmpty());
        verifyNoInteractions(agents);
    }

    @Test void designPreflightChecksSelectedAgentsScopeWithoutCreatingTask() {
        service.validateBeforeDesignConfirmed(277L, "admin");
        verify(agents).requireAssignable(eq("agent"), any(), eq("admin"));
        verify(agents, never()).createTask(any(), any(), any());
        assertTrue(queued.isEmpty());
    }

    @BeforeEach
    void setUp() throws Exception {
        var dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=FALSE");
        keepAlive = dataSource.getConnection();
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE pm_intake_ai_development (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY, intake_id BIGINT UNIQUE NOT NULL,
                    development_branch VARCHAR(128), agent_id VARCHAR(64), agent_task_id VARCHAR(64),
                    request_key VARCHAR(64) NOT NULL, execution_status VARCHAR(32) NOT NULL,
                    execution_message VARCHAR(512), created_by VARCHAR(64), updated_by VARCHAR(64),
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("CREATE TABLE pm_agent_run (id VARCHAR(64) PRIMARY KEY, task_id VARCHAR(64), status VARCHAR(32), created_at DATETIME)");

        var configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(IntakeAiDevelopmentMapper.class);
        var factory = new SqlSessionFactoryBean();
        factory.setConfiguration(configuration);
        factory.setDataSource(dataSource);
        var mapper = new SqlSessionTemplate(factory.getObject()).getMapper(IntakeAiDevelopmentMapper.class);
        var manager = new DataSourceTransactionManager(dataSource);
        stageTransaction = new TransactionTemplate(manager);

        intake.setId(277L);
        intake.setDemandStatus(IntakeDemandStatusRules.IN_DEVELOPMENT);
        intake.setRequirementName("还款计划查询");
        intake.setDevelopmentBranchName("feature/repay-plan");
        intake.setAiAgentId("agent");
        intake.setBusinessLineCode("BL000011");
        intakes = mock(IntakeMapper.class);
        history = mock(IntakeHistoryMapper.class);
        when(intakes.findById(277L)).thenReturn(intake);
        var draft = new DevelopmentAnalysisEntity();
        draft.setDraftJson("""
                {"workItems":[{"title":"实现接口"}]}
                """);
        var drafts = mock(DevelopmentAnalysisMapper.class);
        when(drafts.findLatestByIntakeId(277L)).thenReturn(draft);
        agents = mock(AgentWorkbenchService.class);
        var task = mock(AgentTaskResponse.class);
        when(task.id()).thenReturn("task");
        when(agents.createTask(eq("agent"), any(), eq("admin"))).thenAnswer(invocation -> {
            AgentTaskCreateRequest request = invocation.getArgument(1);
            assertTrue(request.runImmediately());
            assertEquals("MANUAL", request.schedule().mode());
            assertFalse(request.schedule().enabled());
            return task;
        });

        service = new IntakeAiDevelopmentService(intakes, mapper, drafts, history,
                agents, mock(SysConfigService.class), new ObjectMapper(), command -> {
                    try (var connection = dataSource.getConnection();
                         var statement = connection.createStatement();
                         var rows = statement.executeQuery("SELECT execution_status FROM pm_intake_ai_development WHERE intake_id=277")) {
                        assertTrue(rows.next());
                        assertEquals("PENDING", rows.getString(1));
                    } catch (java.sql.SQLException ex) {
                        throw new AssertionError(ex);
                    }
                    queued.add(command);
                }, manager);
    }

    @AfterEach
    void tearDown() throws Exception {
        keepAlive.close();
    }

    @Test
    void designAfterCommitPersistsPendingBeforeSubmitting() {
        insertRecord("FAILED");
        stageTransaction.executeWithoutResult(status -> TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        service.triggerAfterDesignConfirmed(277L, "admin");
                    }
                }));
        queued.remove().run();
        var result = service.detail(277L);
        assertEquals("SUCCEEDED", result.executionStatus());
        assertEquals("task", result.agentTaskId());
    }

    @Test
    void existingPendingCanBeRetriedAndDuplicateSubmissionsCreateOneTask() {
        insertRecord("PENDING");
        service.retry(277L, "admin");
        service.retry(277L, "admin");
        queued.remove().run();
        queued.remove().run();

        assertEquals("SUCCEEDED", service.detail(277L).executionStatus());
        verify(agents, times(1)).createTask(eq("agent"), any(), eq("admin"));
    }

    @Test
    void taskCreationFailureIsReportedAndCanBeRetried() {
        insertRecord("PENDING");
        doThrow(new IllegalArgumentException("数字员工不可用"))
                .when(agents).createTask(eq("agent"), any(), eq("admin"));

        service.retry(277L, "admin");
        queued.remove().run();

        var result = service.detail(277L);
        assertEquals("FAILED", result.executionStatus());
        assertEquals("数字员工不可用", result.executionMessage());
        assertNull(result.agentTaskId());
        assertEquals("PENDING", service.retry(277L, "admin").executionStatus());
    }

    @Test
    void retryWithoutRecordStartsFirstAiDevelopment() {
        // 设计完成时选择人工开发：无执行记录，开发中手动补发首次 AI 开发
        service.retry(277L, "admin");
        queued.remove().run();

        var result = service.detail(277L);
        assertEquals("SUCCEEDED", result.executionStatus());
        assertEquals("task", result.agentTaskId());
        assertEquals("feature/repay-plan", result.developmentBranch());
        verify(agents, times(1)).createTask(eq("agent"), any(), eq("admin"));
    }

    @Test
    void retryWithoutRecordRejectsNonDevelopmentIntake() {
        intake.setDemandStatus(IntakeDemandStatusRules.TESTING);
        var error = assertThrows(IllegalArgumentException.class, () -> service.retry(277L, "admin"));
        assertTrue(error.getMessage().contains("仅开发中的需求可以启动 AI 开发"));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM pm_intake_ai_development WHERE intake_id=277", Integer.class));
        verifyNoInteractions(agents);
    }

    private void insertRecord(String status) {
        jdbc.update("""
                INSERT INTO pm_intake_ai_development (intake_id, request_key, execution_status, created_by)
                VALUES (277, 'intake-dev-277', ?, 'admin')
                """, status);
    }

    @Test
    void retriedAgentRunRestoresIntakeTrackingAndCompletesWithoutCreatingAnotherTask() {
        insertLinkedFailedRun();
        jdbc.update("INSERT INTO pm_agent_run VALUES ('retry', 'task', 'RUNNING', '2026-11-17 14:32:00')");
        service.watchProgress();
        assertEquals("SUCCEEDED", service.detail(277L).executionStatus());
        assertEquals("task", service.detail(277L).agentTaskId());
        jdbc.update("UPDATE pm_agent_run SET status='COMPLETED' WHERE id='retry'");
        service.watchProgress();
        service.watchProgress();
        assertEquals("COMPLETED", service.detail(277L).executionStatus());
        verify(intakes, times(1)).updateManagementFields(eq(277L), isNull(), isNull(), eq(IntakeDemandStatusRules.DEVELOPMENT_COMPLETED));
        verifyNoInteractions(agents);
    }

    @Test
    void retryCompletedBetweenPollsStillUpdatesFailedIntake() {
        insertLinkedFailedRun();
        jdbc.update("INSERT INTO pm_agent_run VALUES ('retry', 'task', 'COMPLETED', '2026-11-17 14:32:00')");
        service.watchProgress();
        assertEquals("COMPLETED", service.detail(277L).executionStatus());
    }

    @Test
    void unchangedCancelledRunDoesNotRewriteFailureOrHistory() {
        insertLinkedFailedRun();
        service.watchProgress();
        service.watchProgress();
        assertEquals("FAILED", service.detail(277L).executionStatus());
        verifyNoInteractions(history, agents);
    }

    private void insertLinkedFailedRun() {
        insertRecord("FAILED");
        jdbc.update("UPDATE pm_intake_ai_development SET agent_task_id='task', development_branch='feature/repay-plan' WHERE intake_id=277");
        jdbc.update("INSERT INTO pm_agent_run VALUES ('previous', 'task', 'CANCELLED', '2026-11-17 13:47:18')");
    }
}
