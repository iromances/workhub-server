package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.dao.attachment.AttachmentMapper;
import cn.aslight.workhub.model.attachment.AttachmentEntity;
import cn.aslight.workhub.service.attachment.AttachmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RequirementMaterialExportServiceTest {
    @TempDir Path root;
    private AttachmentMapper mapper;
    private RequirementMaterialExportService exporter;
    private PlatformTransactionManager transactions;
    private final List<AttachmentEntity> materials = new ArrayList<>();

    @BeforeEach
    void setUp() {
        mapper = mock(AttachmentMapper.class);
        transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenAnswer(ignored -> new SimpleTransactionStatus());
        when(mapper.findByBiz(eq(1L), anyList())).thenAnswer(ignored -> List.copyOf(materials));
        exporter = new RequirementMaterialExportService(new AttachmentService(mapper), transactions);
    }

    @Test
    void exportsValidatedDatabaseContentFlatAndSkipsDeliveryFiles() throws Exception {
        add(11L, AttachmentService.INTAKE_SCREENSHOT, "材料.png", "图片");
        add(12L, AttachmentService.INTAKE_ATTACHMENT, "材料.png", "正文");
        add(13L, AttachmentService.INTAKE_ATTACHMENT, "材料.png", "另一个同名附件");
        add(14L, AttachmentService.INTAKE_DELIVERY_FILE, "结果.csv", "不导出");
        Path folder = synchronize();
        assertEquals("图片", Files.readString(folder.resolve("11-材料.png")));
        assertEquals("正文", Files.readString(folder.resolve("12-材料.png")));
        assertEquals("另一个同名附件", Files.readString(folder.resolve("13-材料.png")));
        assertFalse(Files.exists(folder.resolve("数据文件")));
        assertFalse(Files.exists(folder.resolve("截图")));
        assertFalse(Files.exists(folder.resolve("附件")));
        try (var entries = Files.list(folder)) {
            assertEquals(3, entries.count());
        }
        verify(mapper, never()).findByIdWithContent(14L);
        verify(transactions, atLeastOnce()).getTransaction(argThat(definition -> definition.isReadOnly()
                && definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW));
    }

    @Test
    void repeatedExportSkipsIdenticalContentAndRepairsSameLengthCorruption() throws Exception {
        add(11L, AttachmentService.INTAKE_ATTACHMENT, "a.txt", "original");
        Path target = synchronize().resolve("11-a.txt");
        FileTime old = FileTime.fromMillis(1000);
        Files.setLastModifiedTime(target, old);
        synchronize();
        assertEquals(old, Files.getLastModifiedTime(target));
        Files.writeString(target, "corrupt!");
        synchronize();
        assertEquals("original", Files.readString(target));
    }

    @Test
    void appendReplaceRenameAndRemovalPreserveRetainedAndUserFiles() throws Exception {
        AttachmentEntity first = add(11L, AttachmentService.INTAKE_ATTACHMENT, "a.txt", "old");
        Path folder = synchronize();
        Files.writeString(folder.resolve("用户笔记.md"), "保留");
        add(12L, AttachmentService.INTAKE_SCREENSHOT, "b.png", "new");
        first.setFileContent("updated".getBytes(StandardCharsets.UTF_8));
        first.setFileSize(7L);
        synchronize();
        assertEquals("updated", Files.readString(folder.resolve("11-a.txt")));
        first.setFileName("new.txt");
        synchronize();
        materials.clear();
        synchronize();
        assertEquals("updated", Files.readString(folder.resolve("11-a.txt")));
        assertEquals("updated", Files.readString(folder.resolve("11-new.txt")));
        assertEquals("new", Files.readString(folder.resolve("12-b.png")));
        assertEquals("保留", Files.readString(folder.resolve("用户笔记.md")));
    }

    @Test
    void supportsHistoricalFilesAndRejectsMissingOrCorruptSources() throws Exception {
        AttachmentEntity item = add(11L, AttachmentService.INTAKE_ATTACHMENT, "历史.txt", "text");
        Path source = Files.writeString(root.resolve("source.txt"), "text");
        item.setFileContent(null);
        item.setStoragePath(source.toString());
        Path target = synchronize().resolve("11-历史.txt");
        assertEquals("text", Files.readString(target));
        item.setFileSha256("bad");
        assertThrows(IllegalStateException.class, this::synchronize);
        item.setFileSha256(null);
        item.setFileSize(100L);
        assertThrows(IllegalStateException.class, this::synchronize);
        Files.delete(source);
        assertThrows(IllegalArgumentException.class, this::synchronize);
        assertEquals("text", Files.readString(target));
    }

    @Test
    void sanitizesPathsAndLimitsUtf8FilenameBytesPreservingExtension() throws Exception {
        add(11L, AttachmentService.INTAKE_ATTACHMENT, "../../\\坏\n名字?.txt", "safe");
        add(12L, AttachmentService.INTAKE_ATTACHMENT, "中😀".repeat(150) + ".docx", "long");
        Path folder = synchronize();
        try (var files = Files.list(folder)) {
            var targets = files.toList();
            assertEquals(2, targets.size());
            assertTrue(targets.stream().allMatch(path -> path.getFileName().toString()
                    .getBytes(StandardCharsets.UTF_8).length <= 255));
            assertTrue(targets.stream().anyMatch(path -> path.toString().endsWith(".docx")));
        }
        assertEquals("附件", RequirementMaterialExportService.safeFileName("..."));
    }

    @Test
    void emptyMaterialsCreateOnlyRequirementDirectory() throws Exception {
        Path folder = synchronize();
        try (var entries = Files.list(folder)) {
            assertEquals(0, entries.count());
        }
        verify(mapper, never()).findByIdWithContent(anyLong());
    }

    @Test
    void rejectsLinkedDirectoryTargetAndDirectoryAsFileThenRecovers() throws Exception {
        add(11L, AttachmentService.INTAKE_ATTACHMENT, "a.txt", "safe");
        Path outside = Files.createDirectory(root.resolve("outside"));
        Path folder = Files.createSymbolicLink(root.resolve("需求"), outside);
        assertThrows(IllegalStateException.class, this::synchronize);
        Files.delete(folder);
        Files.createDirectory(folder);
        Path external = Files.writeString(outside.resolve("a.txt"), "untouched");
        Path target = Files.createSymbolicLink(folder.resolve("11-a.txt"), external);
        assertThrows(IllegalStateException.class, this::synchronize);
        assertEquals("untouched", Files.readString(external));
        Files.delete(target);
        Files.createDirectory(target);
        assertThrows(IllegalStateException.class, this::synchronize);
        Files.delete(target);
        synchronize();
        assertEquals("safe", Files.readString(target));
        try (var entries = Files.list(folder)) {
            assertEquals(1, entries.count());
        }
    }

    @Test
    void waitsForCommitAndDoesNotExportOnRollback() {
        add(11L, AttachmentService.INTAKE_ATTACHMENT, "a.txt", "text");
        TransactionSynchronizationManager.initSynchronization();
        try {
            exporter.scheduleAfterCommit(1L, () -> root.resolve("committed"));
            assertFalse(Files.exists(root.resolve("committed")));
            var callbacks = TransactionSynchronizationManager.getSynchronizations();
            TransactionSynchronizationManager.clearSynchronization();
            callbacks.forEach(TransactionSynchronization::afterCommit);
            assertTrue(Files.exists(root.resolve("committed/11-a.txt")));
            TransactionSynchronizationManager.initSynchronization();
            exporter.scheduleAfterCommit(1L, () -> root.resolve("rolled-back"));
            TransactionSynchronizationManager.getSynchronizations().forEach(callback ->
                    callback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            assertFalse(Files.exists(root.resolve("rolled-back")));
        } finally {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.clearSynchronization();
            }
        }
    }

    @Test
    void automaticFailureIsContainedAndManualRetrySucceeds() throws Exception {
        add(11L, AttachmentService.INTAKE_ATTACHMENT, "a.txt", "text");
        Path folder = Files.writeString(root.resolve("需求"), "blocked");
        assertDoesNotThrow(() -> exporter.scheduleAfterCommit(1L, () -> folder));
        assertThrows(IllegalStateException.class, this::synchronize);
        Files.delete(folder);
        assertEquals("text", Files.readString(synchronize().resolve("11-a.txt")));
    }

    @Test
    void serializesConcurrentExportsForSameRequirement() throws Exception {
        add(11L, AttachmentService.INTAKE_ATTACHMENT, "a.txt", "text");
        try (var executor = Executors.newFixedThreadPool(4)) {
            var futures = java.util.stream.IntStream.range(0, 12)
                    .mapToObj(ignored -> executor.submit(this::synchronize)).toList();
            for (var future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        }
        try (var files = Files.list(root.resolve("需求"))) {
            assertEquals(List.of("11-a.txt"), files.map(path -> path.getFileName().toString()).toList());
        }
    }

    private Path synchronize() {
        return exporter.synchronize(1L, () -> root.resolve("需求"));
    }

    private AttachmentEntity add(Long id, String type, String name, String text) {
        AttachmentEntity entity = new AttachmentEntity();
        entity.setId(id);
        entity.setBizId(1L);
        entity.setBizType(type);
        entity.setFileName(name);
        entity.setFileContent(text.getBytes(StandardCharsets.UTF_8));
        entity.setFileSize((long) entity.getFileContent().length);
        materials.add(entity);
        when(mapper.findByIdWithContent(id)).thenReturn(entity);
        return entity;
    }
}
