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
    void createsNothingUntilRecognitionSucceedsIncludingManualOpen() {
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
    void automaticExportDoesNotOpenDesktopAndExplicitOpenExportsFirst() throws Exception {
        intake(1L, "SP", "需求");
        doAnswer(invocation -> {
            Supplier<Path> supplier = invocation.getArgument(1);
            supplier.get();
            return null;
        }).when(exporter).scheduleAfterCommit(eq(1L), any());
        folders.scheduleMaterialExport(1L);
        verify(folders, never()).openPath(any());
        when(exporter.synchronize(eq(1L), any())).thenAnswer(invocation -> {
            Supplier<Path> supplier = invocation.getArgument(1);
            Path folder = supplier.get();
            Files.writeString(folder.resolve("导出完成"), "ok");
            return folder;
        });
        doAnswer(invocation -> {
            Path path = invocation.getArgument(0);
            assertTrue(Files.exists(path.resolve("导出完成")));
            return null;
        }).when(folders).openPath(any());
        var response = folders.open(1L);
        assertTrue(response.opened());
        assertEquals("SP-需求", response.folderName());
        assertEquals(root.resolve("SP-需求").toRealPath().toString(), response.folderPath());
        when(exporter.synchronize(eq(1L), any())).thenThrow(new IllegalStateException("导出失败"));
        assertThrows(IllegalStateException.class, () -> folders.open(1L));
        verify(folders, times(1)).openPath(any());
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
