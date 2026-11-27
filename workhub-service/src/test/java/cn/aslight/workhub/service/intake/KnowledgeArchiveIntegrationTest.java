package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.model.intake.DevelopmentAnalysisDraft;
import cn.aslight.workhub.model.intake.IntakeRecordEntity;
import cn.aslight.workhub.model.intake.IntakeStructuredData;
import cn.aslight.workhub.model.project.ProjectDetailResponse;
import cn.aslight.workhub.service.attachment.AttachmentService;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class KnowledgeArchiveIntegrationTest {
    @TempDir Path vault;
    @TempDir Path repository;
    private final ObjectMapper json = new ObjectMapper();
    private final AttachmentService attachments = mock(AttachmentService.class);

    private SysConfigService config() {
        try { RepositoryDocumentPathsTest.repository(repository); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
        SysConfigService config = mock(SysConfigService.class);
        when(config.findPlainValue(ProjectKnowledgeBaseService.CONFIG_GROUP,
                ProjectKnowledgeBaseService.CONFIG_KEY_VAULT_PATH)).thenReturn(vault.toString());
        when(config.findPlainValue(ProjectKnowledgeBaseService.CONFIG_GROUP,
                RepositoryDocumentPaths.CONFIG_KEY_REPOSITORY_PATH)).thenReturn(repository.toString());
        when(config.findPlainValue(ProjectKnowledgeBaseService.CONFIG_GROUP,
                RepositoryDocumentPaths.CONFIG_KEY_PLAYBOOK_PATH)).thenReturn(repository.resolve("agents-playbook").toString());
        return config;
    }

    private PlatformTransactionManager transactions() {
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenAnswer(ignored -> new SimpleTransactionStatus());
        return transactions;
    }

    private ProjectKnowledgeBaseService service() {
        return new ProjectKnowledgeBaseService(config(), attachments, json, transactions());
    }

    private Path documents() throws Exception {
        return Files.createDirectories(repository.resolve("docs"));
    }

    /** 需求目录：协作业务线归档在 agents-playbook 仓库。 */
    private Path requirementFolder() {
        return service().resolveRequirementIterationLocation(input(), project(), 1L).wikiFolderPath();
    }

    /** 来源路径对应的版本目录，按归档代码使用的绝对路径形式给出。 */
    private Path versionFolder(String source) {
        return requirementFolder().resolve("原始材料").resolve("历史版本")
                .resolve(repository.resolve(source).getParent().getFileName());
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
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("status", "DRAFT");
        values.put("summary", summary);
        values.put("requirementRawPath", source);
        values.put("businessLine", "测试业务线");
        values.put("workItems", List.of());
        return json.readValue(json.writeValueAsString(values), DevelopmentAnalysisDraft.class);
    }

    @Test
    void inputVersionsAreImmutableAndUnchangedInputReusesTheLatestVersion() throws Exception {
        ProjectKnowledgeBaseService service = service();
        var first = service.upsertRequirementIterationNote(intake("来源 A"), input(), project());
        var second = service.upsertRequirementIterationNote(intake("来源 B"), input(), project());
        var third = service.upsertRequirementIterationNote(intake("来源 B"), input(), project());
        assertTrue(first.written());
        assertNull(first.failureReason());
        assertNotEquals(first.rawRelativePath(), second.rawRelativePath());
        assertEquals(second.rawRelativePath(), third.rawRelativePath());
        // 本测试把 agents-playbook 配在仓库目录内，相对路径因此不带 ../；生产布局为同级仓库
        assertTrue(second.rawRelativePath().startsWith(
                "agents-playbook/docs/业务线/测试业务线/需求/202610180001-归档测试/原始材料/历史版本/"), second.rawRelativePath());
        assertTrue(Files.readString(repository.resolve(first.rawRelativePath())).contains("来源 A"));
        assertTrue(Files.readString(repository.resolve(second.rawRelativePath())).contains("来源 B"));
        assertFalse(Files.readString(repository.resolve(first.rawRelativePath())).contains("来源 B"));
        Path materials = requirementFolder().resolve("原始材料");
        assertEquals(Files.readString(repository.resolve(third.rawRelativePath())),
                Files.readString(materials.resolve("source.md")));
        try (var versions = Files.list(materials.resolve("历史版本"))) { assertEquals(2, versions.count()); }
        assertNull(second.wikiRelativePath());
        assertFalse(Files.exists(documents().resolve("requirement-sources")));
        assertFalse(Files.exists(documents().resolve("notes.md")));
    }

    @Test
    void promptContextKeepsRequirementContentAndReportsTheInputVersion() {
        var note = service().upsertRequirementIterationNote(intake("需求原文"), input(), project());
        String markdown = note.markdown();
        assertTrue(markdown.startsWith("# 归档测试\n"));
        assertTrue(markdown.contains("- **需求ID**：1\n"));
        assertTrue(markdown.contains("- **审批编号**：202610180001\n"));
        assertTrue(markdown.contains("- **业务线**：测试业务线\n"));
        assertTrue(markdown.contains("- **项目**：测试项目\n"));
        assertTrue(markdown.contains("## 原始内容\n\n```text\n需求原文\n```\n"));
        assertTrue(markdown.contains("附件保存在数据库"));
        assertEquals("项目文档原始材料版本：" + note.rawRelativePath() + "\n\n" + markdown, note.asPromptContext());
    }

    @Test
    void concurrentArchivesOfTheSameInputShareOneVersion() throws Exception {
        ProjectKnowledgeBaseService service = service();
        var pool = Executors.newFixedThreadPool(6);
        try {
            var jobs = new java.util.ArrayList<Future<ProjectKnowledgeBaseService.RequirementKnowledgeNote>>();
            for (int i = 0; i < 24; i++) {
                jobs.add(pool.submit(() -> service.upsertRequirementIterationNote(intake("并发原文"), input(), project())));
            }
            String version = null;
            for (var job : jobs) {
                ProjectKnowledgeBaseService.RequirementKnowledgeNote note = job.get();
                assertTrue(note.written());
                if (version == null) { version = note.rawRelativePath(); }
                assertEquals(version, note.rawRelativePath());
            }
            try (var files = Files.list(versionFolder(version))) { assertEquals(2, files.count()); }
        } finally {
            pool.shutdown();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void inputVersionListsAttachmentDigestsWithoutCopyingAttachmentFiles() throws Exception {
        when(attachments.listIntakeMaterialDigests(1L)).thenReturn(List.of(
                new AttachmentService.AttachmentDigest(11L, "需求.docx", 2048L, "sha-11"),
                new AttachmentService.AttachmentDigest(12L, "截图.png", 64L, null)));
        var note = service().upsertRequirementIterationNote(intake("附件清单"), input(), project());
        Path version = versionFolder(note.rawRelativePath());
        Path source = version.resolve("source.md");
        var manifest = json.readTree(Files.readString(version.resolve("manifest.json")));
        assertEquals(1L, manifest.get("intakeId").asLong());
        assertEquals(version.getFileName().toString(), manifest.get("versionId").asText());
        assertEquals(KnowledgeArchiveFiles.digest(Files.readAllBytes(source)), manifest.get("sourceSha256").asText());
        assertEquals("需求.docx", manifest.get("attachments").get(0).get("fileName").asText());
        assertEquals(2048L, manifest.get("attachments").get(0).get("fileSize").asLong());
        assertEquals("sha-11", manifest.get("attachments").get(0).get("fileSha256").asText());
        assertEquals(12L, manifest.get("attachments").get(1).get("id").asLong());
        assertTrue(manifest.get("attachments").get(1).get("fileSha256").isNull());
        try (var files = Files.walk(version)) {
            assertEquals(List.of("manifest.json", "source.md"),
                    files.filter(Files::isRegularFile).map(path -> path.getFileName().toString()).sorted().toList());
        }
        try (var files = Files.walk(version.getParent().getParent())) {
            assertFalse(files.anyMatch(path -> path.toString().endsWith(".docx")));
        }
    }

    @Test
    void changedInputOrAttachmentsCreateANewVersion() {
        var first = new AttachmentService.AttachmentDigest(11L, "需求.docx", 2048L, "sha-11");
        var second = new AttachmentService.AttachmentDigest(12L, "补充.pdf", 4096L, "sha-12");
        when(attachments.listIntakeMaterialDigests(1L))
                .thenReturn(List.of(first), List.of(first, second), List.of(first, second));
        ProjectKnowledgeBaseService service = service();
        var initial = service.upsertRequirementIterationNote(intake("原文"), input(), project());
        var moreAttachments = service.upsertRequirementIterationNote(intake("原文"), input(), project());
        var edited = service.upsertRequirementIterationNote(intake("修订原文"), input(), project());
        assertNotEquals(initial.rawRelativePath(), moreAttachments.rawRelativePath());
        assertNotEquals(moreAttachments.rawRelativePath(), edited.rawRelativePath());
    }

    @Test
    void unavailableArchiveRootKeepsPromptMarkdownAndReportsFailure() throws Exception {
        SysConfigService config = config();
        when(config.findPlainValue(ProjectKnowledgeBaseService.CONFIG_GROUP,
                RepositoryDocumentPaths.CONFIG_KEY_PLAYBOOK_PATH)).thenReturn(repository.resolve("docs/agents-playbook").toString());
        var note = new ProjectKnowledgeBaseService(config, attachments, json, transactions())
                .upsertRequirementIterationNote(intake("归档不可用"), input(), project());
        assertFalse(note.written());
        assertNull(note.rawRelativePath());
        assertTrue(note.failureReason().contains("agents-playbook"));
        assertTrue(note.markdown().contains("## 原始内容\n\n```text\n归档不可用\n```\n"));
        assertEquals(note.markdown(), note.asPromptContext());
        assertFalse(Files.exists(repository.resolve("docs/agents-playbook")));
    }

    @Test
    void confirmationIsIdempotentAndAmendmentRetainsPreviousReport() throws Exception {
        ProjectKnowledgeBaseService service = service();
        String source = service.upsertRequirementIterationNote(intake("原始需求"), input(), project()).rawRelativePath();
        var location = service.publishConfirmedAnalysis(intake("原始需求"), input(), project(), 2L,
                draft(source, "第一轮结论"), "reviewer", "2026-10-18T12:00:00");
        String first = Files.readString(location.wikiPath());
        assertTrue(first.contains("- 来源版本：[来源版本](<" + versionFolder(source).resolve("source.md") + ">)"));
        assertFalse(first.contains("临时分析"));
        service.publishConfirmedAnalysis(intake("原始需求"), input(), project(), 2L,
                draft(source, "第一轮结论"), "reviewer", "2026-10-18T12:01:00");
        assertEquals(first, Files.readString(location.wikiPath()));
        assertFalse(Files.exists(location.wikiFolderPath().resolve("历史版本")));
        String index = Files.readString(repository.resolve("agents-playbook/docs/业务线/index.md"));
        assertEquals(1, index.split(java.util.regex.Pattern.quote("(<" + location.wikiPath() + ">)"), -1).length - 1);
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
    void linkedMaterialFoldersAndEscapingSourcesCannotTouchOutsideFiles() throws Exception {
        Path outside = Files.createDirectory(vault.resolve("outside"));
        Files.writeString(outside.resolve("保持原样.md"), "不得改写");
        ProjectKnowledgeBaseService service = service();
        Path folder = requirementFolder();
        Files.createDirectories(folder.getParent());
        Files.createSymbolicLink(folder, outside);
        var note = service.upsertRequirementIterationNote(intake("原文"), input(), project());
        assertFalse(note.written());
        assertNull(note.rawRelativePath());
        assertTrue(note.failureReason().contains("符号链接"));
        assertTrue(note.markdown().contains("原文"));
        try (var files = Files.list(outside)) { assertEquals(1, files.count()); }
        Files.delete(folder);
        assertThrows(IllegalStateException.class, () -> service.publishConfirmedAnalysis(intake("原文"), input(), project(), 1L,
                draft("raw/公司项目/requirements/../../../保持原样.md", "已确认"), "reviewer", "2026-10-30T12:00:00"));
        assertEquals("不得改写", Files.readString(outside.resolve("保持原样.md")));
        String unrecognized = Files.readString(service.publishConfirmedAnalysis(intake("原文"), input(), project(), 1L,
                draft("../../保持原样.md", "无法识别的来源"), "reviewer", "2026-10-30T12:01:00").wikiPath());
        assertTrue(unrecognized.contains("未留存（来源版本文件不存在：../../保持原样.md）"));
    }

    @Test
    void missingApprovalNumbersUseIntakeIds() throws Exception {
        ProjectKnowledgeBaseService service = service();
        IntakeStructuredData noCode = json.readValue("{\"requirementName\":\"相同名称\"}", IntakeStructuredData.class);
        IntakeRecordEntity other = intake("第二个需求");
        other.setId(2L);
        var first = service.upsertRequirementIterationNote(intake("第一个需求"), noCode, project());
        var second = service.upsertRequirementIterationNote(other, noCode, project());
        assertTrue(first.rawRelativePath().contains("/需求/INTAKE-1-相同名称/原始材料/历史版本/"));
        assertTrue(second.rawRelativePath().contains("/需求/INTAKE-2-相同名称/原始材料/历史版本/"));
        assertTrue(Files.readString(repository.resolve(first.rawRelativePath())).contains("第一个需求"));
        assertTrue(Files.readString(repository.resolve(second.rawRelativePath())).contains("第二个需求"));
    }

    @Test
    void entireArchiveLifecycleLeavesKnowledgeVaultUnchanged() throws Exception {
        Files.writeString(vault.resolve("notes.md"), "知识库仅保留手工知识笔记");
        Files.writeString(vault.resolve("规范.md"), "通用研发规范");
        ProjectKnowledgeBaseService service = service();
        var source = service.upsertRequirementIterationNote(intake("材料"), input(), project());
        var report = service.publishConfirmedAnalysis(intake("材料"), input(), project(), 9L,
                draft(source.rawRelativePath(), "确认结论"), "reviewer", "2026-10-30T12:00:00");
        assertTrue(report.rawPath().startsWith(report.wikiRoot()));
        assertTrue(report.wikiPath().startsWith(repository.resolve("agents-playbook/docs/业务线")));
        assertFalse(Files.exists(documents().resolve("requirement-sources")));
        assertFalse(Files.exists(documents().resolve("notes.md")));
        assertNull(report.wikiObsidianUrl());
        assertEquals("知识库仅保留手工知识笔记", Files.readString(vault.resolve("notes.md")));
        assertEquals("通用研发规范", Files.readString(vault.resolve("规范.md")));
        try (var files = Files.list(vault)) { assertEquals(2, files.count()); }
    }

    @Test
    void legacySourcePathsResolveReadOnlyAndMissingFilesAreMarkedUnretained() throws Exception {
        String legacy = "requirement-sources/测试业务线/测试项目/2026/202610180001/历史版本/old/source.md";
        Path legacyFile = documents().resolve(legacy);
        Files.createDirectories(legacyFile.getParent());
        Files.writeString(legacyFile, "旧来源快照");
        ProjectKnowledgeBaseService service = service();
        String migrated = Files.readString(service.publishConfirmedAnalysis(intake("材料"), input(), project(), 7L,
                draft("raw/公司项目/requirements/测试业务线/测试项目/2026/202610180001/历史版本/old/source.md", "迁移前缀"),
                "reviewer", "2026-10-30T12:01:00").wikiPath());
        assertTrue(migrated.contains("- 来源版本：[来源版本](<" + legacyFile + ">)"));
        String missing = Files.readString(service.publishConfirmedAnalysis(intake("材料"), input(), project(), 7L,
                draft("requirement-sources/不存在/source.md", "来源缺失"), "reviewer", "2026-10-30T12:02:00").wikiPath());
        assertTrue(missing.contains("未留存（来源版本文件不存在：requirement-sources/不存在/source.md）"));
        String unrecorded = Files.readString(service.publishConfirmedAnalysis(intake("材料"), input(), project(), 7L,
                draft(null, "未记录来源"), "reviewer", "2026-10-30T12:03:00").wikiPath());
        assertTrue(unrecorded.contains("未留存（历史分析未记录输入版本"));
        assertEquals("旧来源快照", Files.readString(legacyFile));
    }

    @Test
    void searchReadsCurrentRepositoryDocumentsAlongsideKnowledgeAndExcludesHistoricalFacts() throws Exception {
        ProjectKnowledgeBaseService service = service();
        Path system = documents().resolve("system/测试业务线");
        Files.createDirectories(system.resolve("历史版本/old"));
        Files.writeString(system.resolve("当前说明.md"), "归档测试 仓库系统已核实规则。");
        Files.writeString(system.resolve("历史版本/old/旧说明.md"), "归档测试 已废弃的规则。");
        Files.writeString(vault.resolve("测试业务线规范.md"), "归档测试 知识库通用规范。");
        Files.writeString(documents().resolve("notes.md"), "归档测试 临时推测。");
        String context = service.buildContext(input(), project());
        assertTrue(context.contains("仓库系统已核实规则"));
        assertTrue(context.contains("知识库通用规范"));
        assertFalse(context.contains("已废弃的规则"));
        assertFalse(context.contains("临时推测"));
        assertTrue(context.length() <= 6000);
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
