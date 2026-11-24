package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.dao.intake.*;
import cn.aslight.workhub.dao.project.*;
import cn.aslight.workhub.dao.system.UserMapper;
import cn.aslight.workhub.model.intake.*;
import cn.aslight.workhub.service.workitem.WorkItemService;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.*;
import tools.jackson.databind.ObjectMapper;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IntakeAnalysisTransactionTest {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void afterCommitCreatesVisibleRecordBeforeDispatch(boolean development) throws Exception { verify(development, false); }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void rejectedQueuePersistsFailureInNewTransaction(boolean development) throws Exception { verify(development, true); }

    private void verify(boolean development, boolean rejected) throws Exception {
        var ds = new JdbcDataSource(); ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        var jdbc = new JdbcTemplate(ds); jdbc.execute("CREATE TABLE analysis(id BIGINT PRIMARY KEY,status VARCHAR(20))");
        var manager = new DataSourceTransactionManager(ds);
        var intake = new IntakeRecordEntity(); intake.setId(1L); intake.setDemandStatus(development ? "待评估" : "待澄清");
        intake.setStructuredDataJson("{\"requirementType\":\"研发需求\",\"fields\":[],\"attachmentSummaries\":[]}");
        var intakes = mock(IntakeMapper.class); when(intakes.findById(1L)).thenReturn(intake);
        Executor executor = task -> {
            try (var connection = ds.getConnection(); var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT status FROM analysis WHERE id=1")) {
                assertTrue(rows.next(), "调度前记录必须已对其他连接可见"); assertEquals("PENDING", rows.getString(1));
            } catch (java.sql.SQLException ex) { throw new AssertionError(ex); }
            if (rejected) throw new RejectedExecutionException("fixture");
        };
        Runnable analyze;
        if (development) {
            var mapper = mock(DevelopmentAnalysisMapper.class); var saved = new AtomicReference<DevelopmentAnalysisEntity>();
            when(mapper.insert(any())).thenAnswer(call -> {
                DevelopmentAnalysisEntity entity = call.getArgument(0); entity.setId(1L); saved.set(entity);
                return jdbc.update("INSERT INTO analysis VALUES(1,'PENDING')");
            });
            when(mapper.findLatestByIntakeId(1L)).thenAnswer(call -> saved.get());
            when(mapper.findById(1L)).thenAnswer(call -> saved.get());
            when(mapper.updateExecutionState(any())).thenAnswer(call -> jdbc.update("UPDATE analysis SET status=?", ((DevelopmentAnalysisEntity)call.getArgument(0)).getAnalysisStatus()));
            var service = new DevelopmentAnalysisService(intakes, mock(IntakeHistoryMapper.class), mapper, mock(ProjectMapper.class),
                    mock(ProjectInvolvedSystemMapper.class), mock(UserMapper.class), mock(GitlabRepositoryService.class), mock(ProjectKnowledgeBaseService.class),
                    mock(CodexCliDevelopmentAnalysisGenerator.class), mock(WorkItemService.class), executor, new ObjectMapper(), manager);
            analyze = () -> service.analyze(1L, "admin");
        } else {
            var mapper = mock(IntakeClarificationAnalysisMapper.class); var saved = new AtomicReference<IntakeClarificationAnalysisEntity>();
            when(mapper.insert(any())).thenAnswer(call -> {
                IntakeClarificationAnalysisEntity entity = call.getArgument(0); entity.setId(1L); saved.set(entity);
                return jdbc.update("INSERT INTO analysis VALUES(1,'PENDING')");
            });
            when(mapper.findLatestByIntakeId(1L)).thenAnswer(call -> saved.get());
            when(mapper.findById(1L)).thenAnswer(call -> saved.get());
            when(mapper.updateExecutionState(any())).thenAnswer(call -> jdbc.update("UPDATE analysis SET status=?", ((IntakeClarificationAnalysisEntity)call.getArgument(0)).getAnalysisStatus()));
            var service = new IntakeClarificationAnalysisService(intakes, mock(IntakeHistoryMapper.class), mapper, mock(ProjectMapper.class),
                    mock(GitlabRepositoryService.class), mock(ProjectKnowledgeBaseService.class), mock(CodexCliClarificationAnalysisGenerator.class),
                    mock(DevelopmentAnalysisService.class), executor, new ObjectMapper(), manager);
            analyze = () -> service.analyze(1L, "admin");
        }
        new TransactionTemplate(manager).executeWithoutResult(tx -> TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                if (rejected) assertThrows(IllegalStateException.class, analyze::run); else analyze.run();
            }
        }));
        assertEquals(rejected ? "FAILED" : "PENDING", jdbc.queryForObject("SELECT status FROM analysis WHERE id=1", String.class));
    }
}
