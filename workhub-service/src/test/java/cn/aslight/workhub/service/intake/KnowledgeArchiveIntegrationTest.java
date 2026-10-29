package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.model.intake.DevelopmentAnalysisDraft;
import cn.aslight.workhub.model.intake.IntakeRecordEntity;
import cn.aslight.workhub.model.intake.IntakeStructuredData;
import cn.aslight.workhub.model.project.ProjectDetailResponse;
import cn.aslight.workhub.service.system.SysConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class KnowledgeArchiveIntegrationTest {
    @TempDir Path vault;
    private final ObjectMapper json = new ObjectMapper();

    private ProjectKnowledgeBaseService service() {
        SysConfigService config = mock(SysConfigService.class);
        when(config.findPlainValue(ProjectKnowledgeBaseService.CONFIG_GROUP,
                ProjectKnowledgeBaseService.CONFIG_KEY_VAULT_PATH)).thenReturn(vault.toString());
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenAnswer(ignored -> new SimpleTransactionStatus());
        return new ProjectKnowledgeBaseService(config, mock(RequirementMaterialExportService.class), json, transactions);
    }

    private IntakeStructuredData input() {
        return json.readValue("""
                {"approvalCode":"202610180001","requirementName":"归档测试","fields":[],"attachmentSummaries":[]}
                """, IntakeStructuredData.class);
    }

    private ProjectDetailResponse project() {
        return new ProjectDetailResponse(1L, "test", "测试项目", null, "测试业务线", null,
                null, null, LocalDateTime.now(), LocalDateTime.now());
    }

    private IntakeRecordEntity intake(String content) {
        IntakeRecordEntity intake = new IntakeRecordEntity();
        intake.setId(1L);
        intake.setRawContent(content);
        return intake;
    }

    private DevelopmentAnalysisDraft draft(String source, String summary) {
        return json.readValue(json.writeValueAsString(Map.of("status", "DRAFT", "summary", summary,
                "requirementRawPath", source, "businessLine", "测试业务线", "workItems", List.of())), DevelopmentAnalysisDraft.class);
    }

    @Test
    void reevaluationKeepsImmutableInputsAndDoesNotCreateWikiMaterialCopies() throws Exception {
        ProjectKnowledgeBaseService service = service();
        var first = service.upsertRequirementIterationNote(intake("来源 A"), input(), project());
        var second = service.upsertRequirementIterationNote(intake("来源 B"), input(), project());
        var third = service.upsertRequirementIterationNote(intake("来源 B"), input(), project());
        assertNotEquals(first.rawRelativePath(), second.rawRelativePath());
        assertNotEquals(second.rawRelativePath(), third.rawRelativePath());
        assertTrue(Files.readString(vault.resolve(first.rawRelativePath())).contains("来源 A"));
        assertTrue(Files.readString(vault.resolve(second.rawRelativePath())).contains("来源 B"));
        var manifest = json.readTree(Files.readString(vault.resolve(first.rawRelativePath()).getParent().resolve("manifest.json")));
        assertEquals(KnowledgeArchiveFiles.digest(Files.readAllBytes(vault.resolve(first.rawRelativePath()))),
                manifest.get("filesSha256").get("source.md").asText());
        assertNull(second.wikiRelativePath());
        assertFalse(Files.exists(vault.resolve("wiki")));
    }

    @Test
    void concurrentNotesAppendPreservesThemesAndDeduplicatesCallbacks() throws Exception {
        Files.writeString(vault.resolve("notes.md"), "# 工作笔记\n\n## 投资\n保留投资\n\n## 公司项目\n旧记录\n\n## 研发\n保留研发\n");
        ProjectKnowledgeBaseService service = service();
        String source = service.upsertRequirementIterationNote(intake("原文"), input(), project()).rawRelativePath();
        var pool = Executors.newFixedThreadPool(6);
        try {
            var jobs = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (long i = 0; i < 24; i++) {
                long id = i;
                jobs.add(pool.submit(() -> service.appendAnalysisNote(1L, id, "生成评估", Map.of("summary", "分析 " + id), source, "tester")));
            }
            for (var job : jobs) { job.get(); }
        } finally {
            pool.shutdown();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
        service.appendAnalysisNote(1L, 0L, "生成评估", Map.of("summary", "分析 0"), source, "tester");
        String notes = Files.readString(vault.resolve("notes.md"));
        assertEquals(24, notes.split("<!-- workhub-analysis:", -1).length - 1);
        assertTrue(notes.startsWith("# 工作笔记\n\n## 投资\n保留投资"));
        assertTrue(notes.endsWith("\n## 研发\n保留研发\n"));
        assertTrue(notes.contains("旧记录"));
        assertFalse(Files.exists(vault.resolve("wiki")));
    }

    @Test
    void confirmationIsIdempotentAndAmendmentRetainsPreviousReport() throws Exception {
        ProjectKnowledgeBaseService service = service();
        String source = service.upsertRequirementIterationNote(intake("原始需求"), input(), project()).rawRelativePath();
        var location = service.publishConfirmedAnalysis(intake("原始需求"), input(), project(), 2L,
                draft(source, "第一轮结论"), "reviewer", "2026-10-18T12:00:00");
        String first = Files.readString(location.wikiPath());
        service.publishConfirmedAnalysis(intake("原始需求"), input(), project(), 2L,
                draft(source, "第一轮结论"), "reviewer", "2026-10-18T12:01:00");
        assertEquals(first, Files.readString(location.wikiPath()));
        assertFalse(Files.exists(location.wikiFolderPath().resolve("历史版本")));
        String index = Files.readString(vault.resolve("wiki/公司项目/研发需求/index.md"));
        assertEquals(1, index.split(java.util.regex.Pattern.quote(location.wikiRelativePath()), -1).length - 1);
        service.publishConfirmedAnalysis(intake("原始需求"), input(), project(), 2L,
                draft(source, "修订结论"), "reviewer", "2026-10-18T12:02:00");
        try (var files = Files.walk(location.wikiFolderPath().resolve("历史版本"))) {
            Path archived = files.filter(Files::isRegularFile).findFirst().orElseThrow();
            assertEquals(first, Files.readString(archived));
        }
        String current = Files.readString(location.wikiPath());
        assertTrue(current.contains("修订结论"));
        assertTrue(current.contains("已确认（不代表已开发或发布）"));
        assertFalse(current.contains("## 原始内容"));
    }

    @Test
    void rollbackNeverPublishesAndCommittedExportFailureIsReportedSeparately() throws Exception {
        ProjectKnowledgeBaseService service = service();
        AtomicReference<Exception> failure = new AtomicReference<>();
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.archiveAfterCommit(() -> { throw new AssertionError("回滚时不得归档"); }, failure::set);
            for (var callback : TransactionSynchronizationManager.getSynchronizations()) {
                callback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            }
            assertNull(failure.get());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.archiveAfterCommit(() -> { throw new IllegalStateException("磁盘不可写"); }, failure::set);
            assertNull(failure.get());
            for (var callback : TransactionSynchronizationManager.getSynchronizations()) { callback.afterCommit(); }
            assertEquals("磁盘不可写", failure.get().getMessage());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void failedErrorNotificationDoesNotEscapeAfterCommitCallback() {
        AtomicReference<Exception> reported = new AtomicReference<>();
        assertDoesNotThrow(() -> service().archiveAfterCommit(
                () -> { throw new IllegalStateException("归档失败"); },
                error -> { reported.set(error); throw new IllegalStateException("历史通知也失败"); }));
        assertEquals("归档失败", reported.get().getMessage());
    }

    @Test
    void linkedNotesAndEscapingSourcesCannotOverwriteOutsideFiles() throws Exception {
        ProjectKnowledgeBaseService service = service();
        Path outside = Files.createTempFile("archive-outside-", ".md");
        try {
            Files.writeString(outside, "不得改写");
            Files.createSymbolicLink(vault.resolve("notes.md"), outside);
            assertThrows(IllegalStateException.class, () -> service.appendAnalysisNote(1L, 1L, "生成评估", Map.of(), null, "tester"));
            assertEquals("不得改写", Files.readString(outside));
            Files.delete(vault.resolve("notes.md"));
            assertThrows(IllegalStateException.class, () -> service.appendAnalysisNote(1L, 1L, "生成评估", Map.of(), "../../outside.md", "tester"));
            Files.writeString(vault.resolve("其他文件.md"), "不是来源");
            assertThrows(IllegalStateException.class, () -> service.appendAnalysisNote(1L, 1L, "生成评估", Map.of(),
                    "raw/公司项目/requirements/../../../其他文件.md", "tester"));
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void clarificationReplyAndRetryReuseRecordedInputVersionWithoutDuplicateCallbacks() throws Exception {
        ProjectKnowledgeBaseService service = service();
        String source = service.upsertRequirementIterationNote(intake("澄清原文"), input(), project()).rawRelativePath();
        service.appendAnalysisNote(1L, 7L, "生成需求澄清项", Map.of("question", "需要确认"), source, "tester");
        service.appendAnalysisNote(1L, 7L, "回复需求澄清项", Map.of("response", "已经回复"), null, "tester");
        service.appendAnalysisNote(1L, 7L, "回复需求澄清项", Map.of("response", "已经回复"), null, "tester");
        String notes = Files.readString(vault.resolve("notes.md"));
        assertEquals(2, notes.split("<!-- workhub-analysis:", -1).length - 1);
        assertEquals(2, notes.split(java.util.regex.Pattern.quote("- 来源：[[" + source + "]]"), -1).length - 1);
        assertFalse(notes.contains("历史分析未记录输入版本"));
    }

    @Test
    void missingApprovalNumbersUseIntakeIdsAndUnsafeSegmentsCannotEscape() throws Exception {
        ProjectKnowledgeBaseService service = service();
        IntakeStructuredData noCode = json.readValue("{\"requirementName\":\"相同名称\"}", IntakeStructuredData.class);
        IntakeRecordEntity other = intake("第二个需求");
        other.setId(2L);
        var first = service.upsertRequirementIterationNote(intake("第一个需求"), noCode, project());
        var second = service.upsertRequirementIterationNote(other, noCode, project());
        assertTrue(first.rawRelativePath().contains("INTAKE-1/"));
        assertTrue(second.rawRelativePath().contains("INTAKE-2/"));
        assertTrue(Files.readString(vault.resolve(first.rawRelativePath())).contains("第一个需求"));
    }

    @Test
    void knowledgeSearchExcludesVersionHistoryAndTemporaryAnalysis() throws Exception {
        Path current = vault.resolve("测试业务线/当前说明.md");
        Files.createDirectories(current.getParent().resolve("历史版本/old"));
        Files.writeString(current, "归档测试 当前已核实的系统说明。");
        Files.writeString(current.getParent().resolve("历史版本/old/旧说明.md"), "归档测试 过期内容不能作为当前事实。");
        Files.writeString(vault.resolve("notes.md"), "归档测试 未确认机器分析。");
        String context = service().buildContext(input(), project());
        assertTrue(context.contains("当前已核实"));
        assertFalse(context.contains("过期内容"));
        assertFalse(context.contains("未确认机器分析"));
    }
}
