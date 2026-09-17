package cn.aslight.workhub.service.attachment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 为必须使用文件路径的 AI CLI 调用临时物化数据库附件，并在调用结束后清理。
 */
public final class AttachmentTemporaryFileSet implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AttachmentTemporaryFileSet.class);

    private final Path temporaryDirectory;
    private final List<AttachmentService.AttachmentFileContext> contexts;

    private AttachmentTemporaryFileSet(Path temporaryDirectory,
                                       List<AttachmentService.AttachmentFileContext> contexts) {
        this.temporaryDirectory = temporaryDirectory;
        this.contexts = contexts;
    }

    public static AttachmentTemporaryFileSet materialize(
            List<AttachmentService.AttachmentFileContext> attachments) {
        List<AttachmentService.AttachmentFileContext> safeAttachments =
                attachments == null ? List.of() : attachments;
        if (safeAttachments.stream().noneMatch(AttachmentTemporaryFileSet::needsMaterialization)) {
            return new AttachmentTemporaryFileSet(null, safeAttachments);
        }

        Path temporaryDirectory = null;
        try {
            temporaryDirectory = Files.createTempDirectory("workhub-attachments-");
            List<AttachmentService.AttachmentFileContext> materialized = new ArrayList<>(safeAttachments.size());
            for (AttachmentService.AttachmentFileContext attachment : safeAttachments) {
                if (!needsMaterialization(attachment)) {
                    materialized.add(attachment);
                    continue;
                }
                String safeFileName = sanitizeFileName(attachment.fileName());
                Path temporaryFile = temporaryDirectory.resolve(attachment.id() + "-" + safeFileName).normalize();
                if (!temporaryFile.startsWith(temporaryDirectory)) {
                    throw new IllegalArgumentException("附件临时文件路径非法");
                }
                Files.write(temporaryFile, attachment.fileContent());
                materialized.add(new AttachmentService.AttachmentFileContext(
                        attachment.id(),
                        attachment.category(),
                        attachment.fileName(),
                        temporaryFile.toString(),
                        attachment.contentType(),
                        attachment.fileContent(),
                        attachment.fileSize(),
                        attachment.fileSha256()
                ));
            }
            return new AttachmentTemporaryFileSet(temporaryDirectory, List.copyOf(materialized));
        } catch (Exception ex) {
            deleteRecursively(temporaryDirectory);
            throw new IllegalStateException("附件临时文件创建失败", ex);
        }
    }

    public List<AttachmentService.AttachmentFileContext> contexts() {
        return contexts;
    }

    Path temporaryDirectory() {
        return temporaryDirectory;
    }

    @Override
    public void close() {
        deleteRecursively(temporaryDirectory);
    }

    private static String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "attachment.bin";
        }
        String sanitized = fileName.replace('\\', '_').replace('/', '_').trim();
        return sanitized.isEmpty() ? "attachment.bin" : sanitized;
    }

    private static boolean needsMaterialization(AttachmentService.AttachmentFileContext attachment) {
        if (attachment == null || attachment.fileContent() == null) {
            return false;
        }
        String contentType = attachment.contentType();
        if (contentType != null && contentType.toLowerCase().startsWith("image/")) {
            return true;
        }
        String fileName = attachment.fileName() == null ? "" : attachment.fileName().toLowerCase();
        return fileName.endsWith(".png")
                || fileName.endsWith(".jpg")
                || fileName.endsWith(".jpeg")
                || fileName.endsWith(".gif")
                || fileName.endsWith(".bmp")
                || fileName.endsWith(".webp");
    }

    private static void deleteRecursively(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ex) {
                    log.warn("附件临时文件清理失败. path={}", path, ex);
                }
            });
        } catch (IOException ex) {
            log.warn("附件临时目录遍历失败. directory={}", directory, ex);
        }
    }
}
