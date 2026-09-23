package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.dao.intake.IntakeMapper;
import cn.aslight.workhub.model.intake.IntakeRecordEntity;
import cn.aslight.workhub.service.system.SysConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RequirementFolderServiceTest {
    @TempDir Path root;
    private IntakeMapper mapper;
    private RequirementFolderService folders;
    private RequirementMaterialExportService exporter;

    @BeforeEach
    void setUp() {
        mapper = mock(IntakeMapper.class);
        var config = mock(SysConfigService.class);
        when(config.findPlainValue("intake.requirementFolder", "basePath")).thenReturn(root.toString());
        exporter = mock(RequirementMaterialExportService.class);
        folders = spy(new RequirementFolderService(mapper, config, new ObjectMapper(), exporter));
    }

    @Test
    void completesLegacyPlaceholderNameAndPreservesAllFiles() throws Exception {
        var intake = intake(1L, null, null);
        Path initial = folders.ensureFolder(1L);
        assertEquals("需求1-未命名需求", initial.getFileName().toString());
        Files.writeString(initial.resolve("11-image.png"), "截图内容");
        Files.writeString(initial.resolve("笔记.md"), "用户笔记");
        intake.setApprovalCode("SP-001");
        intake.setRequirementName("识别后的需求");
        Path completed = folders.ensureFolder(1L);
        assertEquals("SP-001-识别后的需求", completed.getFileName().toString());
        assertFalse(Files.exists(initial));
        assertEquals("1", Files.readString(completed.resolve(".workhub-intake-id")));
        assertEquals("截图内容", Files.readString(completed.resolve("11-image.png")));
        assertEquals("用户笔记", Files.readString(completed.resolve("笔记.md")));
        assertEquals(completed, folders.ensureFolder(1L));
        verify(folders, never()).openPath(any());
    }

    @Test
    void automaticExportCreatesNothingUntilRecognitionSucceeds() {
        var intake = intake(1L, "SP", "需求名称");
        for (String status : java.util.List.of("PENDING", "RUNNING", "FAILED")) {
            intake.setEnrichmentStatus(status);
            folders.scheduleMaterialExport(1L);
            assertThrows(IllegalStateException.class, () -> folders.ensureFolder(1L));
            assertFalse(Files.exists(root.resolve("SP-需求名称")));
        }
        verifyNoInteractions(exporter);
        intake.setEnrichmentStatus("SUCCEEDED");
        folders.scheduleMaterialExport(1L);
        verify(exporter).scheduleAfterCommit(eq(1L), any());
        assertEquals("SP-需求名称", folders.ensureFolder(1L).getFileName().toString());
    }

    @Test
    void completesLegacyNameInStagesWithoutRegressingOrOverwritingAnotherDirectory() throws Exception {
        var intake = intake(1L, null, null);
        Path initial = folders.ensureFolder(1L);
        Files.writeString(initial.resolve("材料.txt"), "保留");
        intake.setRequirementName("正式名称");
        Path partiallyNamed = folders.ensureFolder(1L);
        assertEquals("需求1-正式名称", partiallyNamed.getFileName().toString());
        intake.setRequirementName(null);
        assertEquals(partiallyNamed, folders.ensureFolder(1L));
        intake.setRequirementName("正式名称");
        intake.setApprovalCode("SP");
        Path occupied = Files.createDirectory(root.resolve("SP-正式名称"));
        Files.writeString(occupied.resolve("材料.txt"), "其他文件");
        Path completed = folders.ensureFolder(1L);
        assertEquals("SP-正式名称-需求1", completed.getFileName().toString());
        assertEquals("保留", Files.readString(completed.resolve("材料.txt")));
        assertEquals("其他文件", Files.readString(occupied.resolve("材料.txt")));
        assertEquals(completed, folders.ensureFolder(1L));
    }

    @Test
    void preservesPlaceholderOnTargetConflictAndPreservesCustomNames() throws Exception {
        var intake = intake(1L, null, null);
        Path initial = folders.ensureFolder(1L);
        Files.writeString(initial.resolve("材料.txt"), "保留");
        intake.setApprovalCode("SP");
        intake.setRequirementName("正式名称");
        Files.createDirectory(root.resolve("SP-正式名称"));
        Files.createDirectory(root.resolve("SP-正式名称-需求1"));
        assertThrows(IllegalStateException.class, () -> folders.ensureFolder(1L));
        assertEquals("保留", Files.readString(initial.resolve("材料.txt")));
        Path custom = Files.move(initial, root.resolve("自定义名称"));
        assertEquals(custom.toRealPath(), folders.ensureFolder(1L));
    }

    @Test
    void reusesLegacyFolderAndSeparatesRequirementsWithSameName() throws Exception {
        intake(1L, "SP", "需求");
        intake(2L, "SP", "需求");
        Path legacy = Files.createDirectory(root.resolve("SP-需求"));
        Files.writeString(legacy.resolve("笔记.md"), "保留");
        assertEquals(legacy.toRealPath(), folders.ensureFolder(1L));
        Path second = folders.ensureFolder(2L);
        assertNotEquals(legacy.toRealPath(), second);
        assertEquals("2", Files.readString(second.resolve(".workhub-intake-id")));
        assertEquals("保留", Files.readString(legacy.resolve("笔记.md")));
    }

    @Test
    void rejectsSymlinkFolderAndMarker() throws Exception {
        intake(1L, "SP", "需求");
        Path outside = Files.createTempDirectory(root, "outside");
        Path folder = Files.createSymbolicLink(root.resolve("SP-需求"), outside);
        assertThrows(IllegalStateException.class, () -> folders.ensureFolder(1L));
        Files.delete(folder);
        Files.createDirectory(folder);
        Path owner = Files.writeString(root.resolve("owner"), "1");
        Files.createSymbolicLink(folder.resolve(".workhub-intake-id"), owner);
        assertThrows(IllegalStateException.class, () -> folders.ensureFolder(1L));
        assertEquals("1", Files.readString(owner));
    }

    @Test
    void cleansLongNamesAndUsesIndependentFieldsBeforeLegacyJson() throws Exception {
        var intake = intake(1L, "审批/1", "中😀".repeat(150));
        intake.setStructuredDataJson("{\"approvalCode\":\"旧编号\",\"requirementName\":\"旧标题\"}");
        Path folder = folders.ensureFolder(1L);
        assertEquals(root.toRealPath(), folder.getParent());
        assertTrue(folder.getFileName().toString().startsWith("审批_1-"));
        assertTrue(folder.getFileName().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 180);
    }

    @Test
    void absentRequirementCreatesNothing() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> folders.ensureFolder(404L));
        try (var entries = Files.list(root)) {
            assertEquals(0, entries.count());
        }
    }

    @Test
    void automaticExportDoesNotOpenDesktop() throws Exception {
        intake(1L, "SP", "需求");
        doAnswer(invocation -> {
            Supplier<Path> supplier = invocation.getArgument(1);
            supplier.get();
            return null;
        }).when(exporter).scheduleAfterCommit(eq(1L), any());
        folders.scheduleMaterialExport(1L);
        verify(folders, never()).openPath(any());
    }

    @Test
    void explicitOpenPreservesExistingFilesAndIgnoresMarkersAndUnrelatedJson() throws Exception {
        var intake = intake(1L, "SP", "需求");
        intake.setEnrichmentStatus("FAILED");
        intake.setStructuredDataJson("无效 JSON");
        Path folder = Files.createDirectory(root.resolve("SP-需求"));
        Path marker = Files.writeString(folder.resolve(".workhub-intake-id"), "无效标记");
        Path document = Files.writeString(folder.resolve("方案.docx"), "已有文件");
        Path unrelated = Files.createDirectory(root.resolve("其他需求"));
        Files.writeString(unrelated.resolve(".workhub-intake-id"), "同样无效");
        doNothing().when(folders).openPath(any());

        var response = folders.open(1L);

        assertTrue(response.opened());
        assertEquals(folder.toString(), response.folderPath());
        assertEquals("无效标记", Files.readString(marker));
        assertEquals("已有文件", Files.readString(document));
        verify(folders).openPath(folder);
        verifyNoInteractions(exporter);
    }

    @Test
    void explicitOpenCreatesMissingDirectoriesRegardlessOfRecognitionStatus() throws Exception {
        doAnswer(invocation -> {
            Path path = invocation.getArgument(0);
            assertTrue(Files.isDirectory(path));
            assertFalse(Files.exists(path.resolve(".workhub-intake-id")));
            return null;
        }).when(folders).openPath(any());
        long id = 1;
        for (String status : java.util.List.of("PENDING", "RUNNING", "FAILED", "SUCCEEDED")) {
            var intake = intake(id, "SP-" + id, "需求");
            intake.setEnrichmentStatus(status);

            var response = folders.open(id);

            assertTrue(response.opened());
            assertEquals("SP-" + id + "-需求", response.folderName());
            assertEquals(root.resolve(response.folderName()).toString(), response.folderPath());
            id++;
        }
        verify(folders, times(4)).openPath(any());
        verifyNoInteractions(exporter);
    }

    @Test
    void explicitOpenReportsMissingRequirementAndFilesystemFailures() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> folders.open(404L));
        intake(1L, "SP", "需求");
        Path path = Files.writeString(root.resolve("SP-需求"), "同名文件");
        assertThrows(IllegalStateException.class, () -> folders.open(1L));
        assertEquals("同名文件", Files.readString(path));
        verify(folders, never()).openPath(any());

        Files.delete(path);
        doThrow(new java.io.IOException("系统打开失败")).when(folders).openPath(any());
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> folders.open(1L));
        assertEquals("系统打开失败", failure.getCause().getMessage());
        verifyNoInteractions(exporter);
    }

    private IntakeRecordEntity intake(Long id, String code, String name) {
        var entity = new IntakeRecordEntity();
        entity.setId(id);
        entity.setApprovalCode(code);
        entity.setRequirementName(name);
        when(mapper.findById(id)).thenReturn(entity);
        return entity;
    }
}
