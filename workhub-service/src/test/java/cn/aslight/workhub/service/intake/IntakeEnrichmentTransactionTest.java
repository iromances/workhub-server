package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.dao.intake.IntakeHistoryMapper;
import cn.aslight.workhub.dao.intake.IntakeMapper;
import cn.aslight.workhub.dao.intake.IntakeStructuredFieldMapper;
import cn.aslight.workhub.model.intake.IntakeRecordEntity;
import cn.aslight.workhub.model.intake.IntakeUploadRequest;
import cn.aslight.workhub.service.attachment.AttachmentService;
import cn.aslight.workhub.service.attachment.AttachmentTextExtractionService;
import org.apache.ibatis.session.Configuration;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 用实际 Spring JDBC 事务和生产失败状态 SQL 验证保存、提交与失败回写的隔离。 */
class IntakeEnrichmentTransactionTest {

    private JdbcDataSource dataSource;
    private Connection keepAlive;
    private JdbcTemplate jdbc;
    private DataSourceTransactionManager transactions;
    private TransactionTemplate saveTransaction;
    private IntakeMapper mapper;
    private IntakeMapper stateSqlMapper;
    private AttachmentService attachments;
    private AttachmentTextExtractionService extraction;
    private CodexCliStructuredExtractor codex;
    private final List<Runnable> queued = new ArrayList<>();
    private static final byte[] IMAGE = {1, 2, 3};

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL");
        keepAlive = dataSource.getConnection();
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE pm_intake_record (
                  id BIGINT PRIMARY KEY, raw_content VARCHAR(4000), requirement_name VARCHAR(200),
                  demand_status VARCHAR(30), enrichment_status VARCHAR(30),
                  enrichment_error_summary VARCHAR(1000), enrichment_updated_at TIMESTAMP,
                  structured_data_json VARCHAR(8000), updated_at TIMESTAMP)
                """);
        jdbc.execute("CREATE TABLE intake_test_attachment (intake_id BIGINT, filename VARCHAR(200), file_content BLOB)");
        transactions = new DataSourceTransactionManager(dataSource);
        saveTransaction = new TransactionTemplate(transactions);
        var configuration = new Configuration();
        configuration.addMapper(IntakeMapper.class);
        var factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        stateSqlMapper = new SqlSessionTemplate(factory.getObject()).getMapper(IntakeMapper.class);

        // MySQL JSON 展示查询用精简读模型替代；失败状态 UPDATE 使用生产 MyBatis SQL。
        mapper = mock(IntakeMapper.class);
        when(mapper.findById(19L)).thenAnswer(invocation -> jdbc.query(
                "SELECT * FROM pm_intake_record WHERE id=19", (row, index) -> {
                    var entity = new IntakeRecordEntity();
                    entity.setId(19L);
                    entity.setSourceType("需求截图附件录入");
                    entity.setSourceChannel("需求录入");
                    entity.setSenderName("admin");
                    entity.setDevelopmentOwnerUserName("owner");
                    entity.setReceivedAt(LocalDateTime.now());
                    entity.setRawContent(row.getString("raw_content"));
                    entity.setRequirementName(row.getString("requirement_name"));
                    entity.setDemandStatus(row.getString("demand_status"));
                    entity.setEnrichmentStatus(row.getString("enrichment_status"));
                    entity.setEnrichmentErrorSummary(row.getString("enrichment_error_summary"));
                    entity.setStructuredDataJson(row.getString("structured_data_json"));
                    return entity;
                }).stream().findFirst().orElse(null));
        doAnswer(invocation -> {
            IntakeRecordEntity entity = invocation.getArgument(0);
            entity.setId(19L);
            return jdbc.update("INSERT INTO pm_intake_record (id, raw_content, requirement_name) VALUES (19, ?, ?)",
                    entity.getRawContent(), entity.getRequirementName());
        }).when(mapper).insert(any());
        doAnswer(invocation -> stateSqlMapper.updateEnrichmentState(
                invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2),
                invocation.getArgument(3), invocation.getArgument(4)))
                .when(mapper).updateEnrichmentState(anyLong(), anyString(), nullable(String.class),
                        nullable(String.class), any());
        doAnswer(invocation -> {
            IntakeRecordEntity entity = invocation.getArgument(0);
            return jdbc.update("UPDATE pm_intake_record SET requirement_name=? WHERE id=?",
                    entity.getRequirementName(), entity.getId());
        }).when(mapper).updateFormalFields(any());
        doAnswer(invocation -> jdbc.update("""
                UPDATE pm_intake_record SET structured_data_json=?, demand_status=?,
                  enrichment_status=?, enrichment_error_summary=? WHERE id=?
                """, invocation.getArgument(1), invocation.getArgument(2), invocation.getArgument(3),
                invocation.getArgument(4), invocation.getArgument(0)))
                .when(mapper).updateStructuredDataAndEnrichment(anyLong(), anyString(), nullable(String.class),
                        anyString(), nullable(String.class), any());
        attachments = mock(AttachmentService.class);
        doAnswer(invocation -> {
            jdbc.update("INSERT INTO intake_test_attachment VALUES (19, 'image.png', ?)", IMAGE);
            return null;
        }).when(attachments).saveIntakeFiles(eq(19L), anyList(), anyList());
        when(attachments.listIntakeFileContexts(19L)).thenAnswer(invocation -> jdbc.query(
                "SELECT filename FROM intake_test_attachment WHERE intake_id=19", (row, index) ->
                        new AttachmentService.AttachmentFileContext(1L, "截图", row.getString(1),
                                "/test/image.png", "image/png")));
        extraction = mock(AttachmentTextExtractionService.class);
        when(extraction.extractSummaries(anyList())).thenReturn(
                new AttachmentTextExtractionService.AttachmentExtractionBatch(List.of(), List.of()));
        codex = mock(CodexCliStructuredExtractor.class);
    }

    @AfterEach
    void tearDown() throws Exception {
        keepAlive.close();
    }

    @Test
    void uploadedMaterialsCommitBeforeTaskSubmission() {
        IntakeService intake = intakeService(service(task -> {
            assertEquals(1, committedCount("pm_intake_record"));
            assertEquals(1, committedCount("intake_test_attachment"));
            queued.add(task);
        }));

        var response = saveTransaction.execute(status -> {
            var detail = upload(intake);
            assertEquals(19L, detail.id());
            assertEquals(0, committedCount("pm_intake_record"));
            assertTrue(queued.isEmpty());
            return detail;
        });

        assertEquals(19L, response.id());
        assertEquals(1, queued.size());
        assertEquals("PENDING", state());
        verifyNoInteractions(codex);
        assertSavedMaterials();
    }

    @Test
    void rolledBackUploadNeverSubmitsRecognition() {
        var intake = intakeService(service(queued::add));
        saveTransaction.executeWithoutResult(status -> {
            upload(intake);
            status.setRollbackOnly();
        });
        assertEquals(0, committedCount("pm_intake_record"));
        assertEquals(0, committedCount("intake_test_attachment"));
        assertTrue(queued.isEmpty());
        verifyNoInteractions(codex);
    }

    @Test
    void attachmentSaveFailureStillRollsBackAndReportsFailure() {
        var intake = intakeService(service(queued::add));
        doThrow(new IllegalStateException("附件保存失败")).when(attachments)
                .saveIntakeFiles(eq(19L), anyList(), anyList());
        assertThrows(IllegalStateException.class, () ->
                saveTransaction.execute(status -> upload(intake)));
        assertEquals(0, committedCount("pm_intake_record"));
        assertTrue(queued.isEmpty());
        verifyNoInteractions(codex);
    }

    @Test
    void rejectedAfterCommitSubmissionPersistsFailedInNewTransactionAndCanRetry() {
        var enrichment = service(task -> { throw new RejectedExecutionException("executor stopped"); });
        var intake = intakeService(enrichment);

        var response = assertDoesNotThrow(() -> saveTransaction.execute(status -> upload(intake)));
        assertEquals(19L, response.id());
        assertEquals("FAILED", committedState());
        assertTrue(jdbc.queryForObject("SELECT enrichment_error_summary FROM pm_intake_record", String.class)
                .contains("请稍后重新识别"));
        assertSavedMaterials();

        var retry = assertDoesNotThrow(() -> saveTransaction.execute(status -> intake.retryEnrichment(19L, "admin")));
        assertEquals(19L, retry.id());
        assertEquals("FAILED", committedState());
        assertSavedMaterials();
        verify(mapper, times(1)).insert(any());
        verifyNoInteractions(codex);
    }

    @Test
    void rejectionWithoutCallerTransactionAlsoAllowsRetry() {
        saveTransaction.executeWithoutResult(status -> upload(intakeService(service(queued::add))));
        var enrichment = service(task -> { throw new RejectedExecutionException("executor stopped"); });
        assertDoesNotThrow(() -> enrichment.scheduleUploadedEnrichment(19L, "需求录入", "原文"));
        assertEquals("FAILED", committedState());
        assertSavedMaterials();
    }

    @Test
    void codexStartupExceptionAndRepeatedFailureKeepSameRecordUntilSuccessfulRetry() {
        var intake = intakeService(service(queued::add));
        saveTransaction.executeWithoutResult(status -> upload(intake));
        String original = jdbc.queryForObject("SELECT raw_content FROM pm_intake_record", String.class);
        doThrow(new IllegalStateException("Cannot run program codex"))
                .when(codex).extract(any(), any(), anyList(), anyList());
        queued.removeFirst().run();
        assertEquals("FAILED", committedState());
        assertSavedMaterials();

        saveTransaction.executeWithoutResult(status -> intake.retryEnrichment(19L, "admin"));
        doReturn(new CodexCliStructuredExtractor.CodexCliExtractionResult(true, null, "Codex CLI 解析超时"))
                .when(codex).extract(any(), any(), anyList(), anyList());
        queued.removeFirst().run();
        assertEquals("FAILED", committedState());
        assertSavedMaterials();

        saveTransaction.executeWithoutResult(status -> intake.retryEnrichment(19L, "admin"));
        doReturn(new CodexCliStructuredExtractor.CodexCliExtractionResult(true,
                new IntakeStructuredDataExtractor().extract("需求名称：识别后需求"), null))
                .when(codex).extract(any(), any(), anyList(), anyList());
        queued.removeFirst().run();
        assertEquals("SUCCEEDED", committedState());
        assertEquals("识别后需求", jdbc.queryForObject("SELECT requirement_name FROM pm_intake_record", String.class));
        assertEquals(original, jdbc.queryForObject("SELECT raw_content FROM pm_intake_record", String.class));
        assertSavedMaterials();
        verify(mapper, times(1)).insert(any());
        verify(codex, times(3)).extract(eq("需求录入"), eq(original),
                argThat(files -> files.size() == 1 && files.getFirst().fileName().equals("image.png")), anyList());
    }

    @Test
    void runningStateWriteFailureIsRecordedAndDoesNotLoseUpload() {
        var enrichment = service(queued::add);
        saveTransaction.executeWithoutResult(status -> upload(intakeService(enrichment)));
        doThrow(new IllegalStateException("RUNNING write failed")).when(mapper)
                .updateEnrichmentState(eq(19L), eq("RUNNING"), nullable(String.class), isNull(), any());
        assertDoesNotThrow(() -> queued.removeFirst().run());
        assertEquals("FAILED", committedState());
        assertSavedMaterials();
        verifyNoInteractions(codex);
    }

    @Test
    void failureStateDatabaseOutageDoesNotReportCommittedUploadAsUnsaved() {
        var enrichment = service(task -> { throw new RejectedExecutionException("executor stopped"); });
        doThrow(new IllegalStateException("database unavailable")).when(mapper)
                .updateEnrichmentState(eq(19L), eq("FAILED"), nullable(String.class), anyString(), any());
        assertDoesNotThrow(() -> saveTransaction.execute(status -> upload(intakeService(enrichment))));
        assertEquals("PENDING", committedState());
        assertSavedMaterials();
    }

    private IntakeEnrichmentService service(Executor executor) {
        return new IntakeEnrichmentService(mapper, mock(IntakeStructuredFieldMapper.class), attachments,
                extraction, new IntakeStructuredDataExtractor(), new IntakeStructuredFieldNormalizer(),
                codex, executor, new ObjectMapper(), null, transactions);
    }

    private IntakeService intakeService(IntakeEnrichmentService enrichment) {
        return new IntakeService(mapper, mock(IntakeHistoryMapper.class), attachments,
                new IntakeStructuredDataExtractor(), enrichment, mock(CodexCliSqlDraftGenerator.class), new ObjectMapper());
    }

    private cn.aslight.workhub.model.intake.IntakeDetailResponse upload(IntakeService intake) {
        var request = new IntakeUploadRequest();
        request.setSenderName("admin");
        request.setDevelopmentOwnerUserName("owner");
        request.setSourceChannel("需求录入");
        request.setRawContent("需求名称：原始需求");
        return intake.createUploaded(request,
                List.of(new MockMultipartFile("screenshots", "image.png", "image/png", IMAGE)), List.of());
    }

    private String state() {
        return jdbc.queryForObject("SELECT enrichment_status FROM pm_intake_record WHERE id=19", String.class);
    }

    private String committedState() {
        // 独立连接读取，确认 afterCommit 中的失败状态确已提交，不只是原连接可见。
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT enrichment_status FROM pm_intake_record WHERE id=19")) {
            assertTrue(rows.next());
            return rows.getString(1);
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }

    private int committedCount(String table) {
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            assertTrue(rows.next());
            return rows.getInt(1);
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }

    private void assertSavedMaterials() {
        assertEquals(1, committedCount("pm_intake_record"));
        assertEquals(1, committedCount("intake_test_attachment"));
        assertTrue(jdbc.queryForObject("SELECT raw_content FROM pm_intake_record", String.class).contains("原始需求"));
        assertArrayEquals(IMAGE, jdbc.queryForObject("SELECT file_content FROM intake_test_attachment", byte[].class));
    }
}
