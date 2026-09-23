package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.model.attachment.AttachmentResponse;
import cn.aslight.workhub.service.attachment.AttachmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.function.Supplier;

/** 数据库附件的本地留存副本；失败可重试，不作为附件下载的数据源。 */
@Service
public class RequirementMaterialExportService {

    private static final Logger log = LoggerFactory.getLogger(RequirementMaterialExportService.class);
    private final AttachmentService attachmentService;
    private final TransactionTemplate readTransaction;
    // 有界锁条带，串行化同一需求的自动导出与手动打开，不长期保存需求 ID。
    private final Object[] locks = new Object[64];

    public RequirementMaterialExportService(AttachmentService attachmentService,
                                           PlatformTransactionManager transactionManager) {
        this.attachmentService = attachmentService;
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.readTransaction.setReadOnly(true);
        Arrays.setAll(locks, ignored -> new Object());
    }

    public void scheduleAfterCommit(Long intakeId, Supplier<Path> folderSupplier) {
        Runnable export = () -> {
            try {
                synchronize(intakeId, folderSupplier);
            } catch (RuntimeException ex) {
                // 数据库已提交，不能把副本导出失败报告为需求录入失败，也不能阻断异步识别。
                log.warn("需求材料自动导出失败。intakeId={}", intakeId, ex);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    export.run();
                }
            });
        } else {
            export.run();
        }
    }

    public Path synchronize(Long intakeId, Supplier<Path> folderSupplier) {
        synchronized (locks[Math.floorMod(intakeId.hashCode(), locks.length)]) {
            // afterCommit 中不能沿用已提交事务的连接和快照。
            Path folder = readTransaction.execute(status -> folderSupplier.get());
            return exportMaterials(intakeId, folder);
        }
    }

    private Path exportMaterials(Long intakeId, Path folder) {
        int written = 0;
        int skipped = 0;
        try {
            requireDirectory(folder);
            var items = readTransaction.execute(status -> attachmentService.listIntakeAttachments(intakeId));
            for (AttachmentResponse item : items) {
                if (!"截图".equals(item.category()) && !"附件".equals(item.category())) {
                    continue;
                }
                // 一次只读一个附件，复用数据库优先、历史路径回退及大小/SHA-256 校验。
                // 每个 BLOB 使用短事务，避免 MyBatis 会话缓存累积整个需求的附件内容。
                AttachmentService.AttachmentResource material = readTransaction.execute(
                        status -> attachmentService.loadAsResource(item.id()));
                if (!intakeId.equals(material.entity().getBizId())) {
                    throw new IllegalStateException("附件不属于当前需求");
                }
                String bizType = material.entity().getBizType();
                if (!AttachmentService.INTAKE_SCREENSHOT.equals(bizType)
                        && !AttachmentService.INTAKE_ATTACHMENT.equals(bizType)) {
                    throw new IllegalStateException("附件不是需求原始材料");
                }
                Path target = folder.resolve(item.id() + "-" + safeFileName(material.entity().getFileName()));
                byte[] content = material.resource().getContentAsByteArray();
                if (writeMaterial(target, content)) {
                    written++;
                } else {
                    skipped++;
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("需求材料导出失败", ex);
        }
        log.info("需求材料导出完成。intakeId={}, written={}, skipped={}", intakeId, written, skipped);
        return folder;
    }

    static void requireDirectory(Path directory) throws IOException {
        if (Files.isSymbolicLink(directory)) {
            throw new IOException("需求材料目录不能是符号链接：" + directory);
        }
        Files.createDirectories(directory);
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("需求材料目录不可用：" + directory);
        }
    }

    private boolean writeMaterial(Path target, byte[] content) throws IOException {
        requireRegularTarget(target);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)
                && Files.size(target) == content.length
                && MessageDigest.isEqual(digest(target), sha256().digest(content))) {
            return false;
        }
        Path temporary = Files.createTempFile(target.getParent(), ".workhub-material-", ".tmp");
        try {
            Files.write(temporary, content);
            requireRegularTarget(target);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void requireRegularTarget(Path target) throws IOException {
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)
                && !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("需求材料目标不能是目录或符号链接：" + target);
        }
    }

    private byte[] digest(Path path) throws IOException {
        MessageDigest digest = sha256();
        try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
            }
        }
        return digest.digest();
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256", ex);
        }
    }

    static String safeFileName(String original) {
        String name = original == null ? "附件" : original
                .replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").strip().replaceAll("[. ]+$", "");
        if (name.isBlank()) {
            name = "附件";
        }
        int dot = name.lastIndexOf('.');
        String extension = dot > 0 ? truncateUtf8(name.substring(dot), 40) : "";
        String stem = dot > 0 ? name.substring(0, dot) : name;
        // 为附件 ID 和分隔符预留空间，避免中文文件名超出文件系统的字节限制。
        return truncateUtf8(stem, 220 - extension.getBytes(StandardCharsets.UTF_8).length) + extension;
    }

    static String truncateUtf8(String value, int limit) {
        int bytes = 0;
        int end = 0;
        while (end < value.length()) {
            int codePoint = value.codePointAt(end);
            int length = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8).length;
            if (bytes + length > limit) {
                break;
            }
            bytes += length;
            end += Character.charCount(codePoint);
        }
        return value.substring(0, end);
    }
}
