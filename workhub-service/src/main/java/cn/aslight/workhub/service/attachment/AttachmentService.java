package cn.aslight.workhub.service.attachment;

import cn.aslight.workhub.model.attachment.AttachmentResponse;
import cn.aslight.workhub.dao.attachment.AttachmentMapper;
import cn.aslight.workhub.model.attachment.AttachmentEntity;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * 附件存储与关联服务。
 */
@Service
public class AttachmentService {

    public static final String INTAKE_SCREENSHOT = "INTAKE_SCREENSHOT";
    public static final String INTAKE_ATTACHMENT = "INTAKE_ATTACHMENT";
    public static final String INTAKE_DELIVERY_FILE = "INTAKE_DELIVERY_FILE";

    private final AttachmentMapper attachmentMapper;

    public AttachmentService(AttachmentMapper attachmentMapper) {
        this.attachmentMapper = attachmentMapper;
    }

    @Transactional
    public void saveIntakeFiles(Long intakeId, List<MultipartFile> screenshots, List<MultipartFile> attachments) {
        storeFiles(intakeId, screenshots, INTAKE_SCREENSHOT);
        storeFiles(intakeId, attachments, INTAKE_ATTACHMENT);
    }

    /**
     * 保存需求补充材料。
     *
     * @param intakeId 待整理需求 ID
     * @param screenshots 需求截图
     * @param attachments 需求附件
     */
    @Transactional
    public void appendIntakeFiles(Long intakeId, List<MultipartFile> screenshots, List<MultipartFile> attachments) {
        saveIntakeFiles(intakeId, screenshots, attachments);
    }

    /**
     * 保存需求阶段推进过程中补充上传的数据文件。
     *
     * @param intakeId 待整理需求 ID
     * @param deliveryFiles 数据文件列表
     */
    @Transactional
    public void saveIntakeDeliveryFiles(Long intakeId, List<MultipartFile> deliveryFiles) {
        storeFiles(intakeId, deliveryFiles, INTAKE_DELIVERY_FILE);
    }

    public List<AttachmentResponse> listIntakeAttachments(Long intakeId) {
        List<AttachmentEntity> entities = attachmentMapper.findByBiz(
                intakeId,
                List.of(INTAKE_SCREENSHOT, INTAKE_ATTACHMENT, INTAKE_DELIVERY_FILE)
        );
        List<AttachmentResponse> items = new ArrayList<>(entities.size());
        for (AttachmentEntity entity : entities) {
            items.add(toResponse(entity));
        }
        return items;
    }

    public List<AttachmentFileContext> listIntakeFileContexts(Long intakeId) {
        return attachmentMapper.findByBizWithContent(intakeId, List.of(INTAKE_SCREENSHOT, INTAKE_ATTACHMENT)).stream()
                .map(entity -> new AttachmentFileContext(
                        entity.getId(),
                        INTAKE_SCREENSHOT.equals(entity.getBizType()) ? "截图" : "附件",
                        entity.getFileName(),
                        entity.getStoragePath(),
                        entity.getContentType(),
                        entity.getFileContent(),
                        entity.getFileSize(),
                        entity.getFileSha256()
                ))
                .toList();
    }

    public AttachmentResource loadAsResource(Long attachmentId) {
        AttachmentEntity entity = attachmentMapper.findByIdWithContent(attachmentId);
        if (entity == null) {
            throw new IllegalArgumentException("附件不存在");
        }
        byte[] content = resolveFileContent(entity);
        validateIntegrity(entity, content);
        return new AttachmentResource(entity, new ByteArrayResource(content));
    }

    /**
     * 删除需求截图或需求附件。
     *
     * <p>只允许删除需求原始材料，不删除阶段产出的数据文件。</p>
     *
     * @param intakeId 待整理需求 ID
     * @param attachmentId 附件 ID
     * @return 被删除的附件信息
     */
    @Transactional
    public AttachmentResponse deleteIntakeMaterial(Long intakeId, Long attachmentId) {
        AttachmentEntity entity = attachmentMapper.findById(attachmentId);
        if (entity == null
                || !intakeId.equals(entity.getBizId())
                || (!INTAKE_SCREENSHOT.equals(entity.getBizType()) && !INTAKE_ATTACHMENT.equals(entity.getBizType()))) {
            throw new IllegalArgumentException("需求附件不存在或不属于当前需求");
        }
        AttachmentResponse response = toResponse(entity);
        attachmentMapper.deleteById(attachmentId);
        deletePhysicalFile(entity.getStoragePath());
        return response;
    }

    /**
     * 替换需求截图或需求附件文件。
     *
     * <p>替换时保留附件 ID 和原附件类型，只更新文件内容和文件名。</p>
     *
     * @param intakeId 待整理需求 ID
     * @param attachmentId 附件 ID
     * @param file 新文件
     * @return 替换前后的附件信息
     */
    @Transactional
    public AttachmentReplaceResult replaceIntakeMaterial(Long intakeId, Long attachmentId, MultipartFile file) {
        AttachmentEntity entity = attachmentMapper.findById(attachmentId);
        if (entity == null
                || !intakeId.equals(entity.getBizId())
                || (!INTAKE_SCREENSHOT.equals(entity.getBizType()) && !INTAKE_ATTACHMENT.equals(entity.getBizType()))) {
            throw new IllegalArgumentException("需求附件不存在或不属于当前需求");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("替换文件不能为空");
        }

        AttachmentResponse before = toResponse(entity);
        String oldStoragePath = entity.getStoragePath();
        StoredFile storedFile = readFile(file);
        attachmentMapper.updateFile(
                attachmentId,
                storedFile.fileName(),
                storedFile.contentType(),
                storedFile.fileContent(),
                storedFile.fileSize(),
                storedFile.fileSha256()
        );
        deletePhysicalFile(oldStoragePath);
        AttachmentEntity updated = attachmentMapper.findById(attachmentId);
        return new AttachmentReplaceResult(before, toResponse(updated));
    }

    private void storeFiles(Long intakeId, List<MultipartFile> files, String bizType) {
        if (files == null || files.isEmpty()) {
            return;
        }
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            storeSingleFile(intakeId, file, bizType);
        }
    }

    private void storeSingleFile(Long intakeId, MultipartFile file, String bizType) {
        StoredFile storedFile = readFile(file);

        AttachmentEntity entity = new AttachmentEntity();
        entity.setBizType(bizType);
        entity.setBizId(intakeId);
        entity.setFileName(storedFile.fileName());
        entity.setStoragePath(null);
        entity.setContentType(storedFile.contentType());
        entity.setFileContent(storedFile.fileContent());
        entity.setFileSize(storedFile.fileSize());
        entity.setFileSha256(storedFile.fileSha256());
        attachmentMapper.insert(entity);
    }

    private StoredFile readFile(MultipartFile file) {
        String originalFilename = sanitizeFileName(file.getOriginalFilename());
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("附件文件名不能为空");
        }
        try {
            byte[] content = file.getBytes();
            if (content.length == 0) {
                throw new IllegalArgumentException("附件文件不能为空");
            }
            return new StoredFile(
                    originalFilename,
                    trimToNull(file.getContentType()),
                    content,
                    (long) content.length,
                    sha256(content)
            );
        } catch (IOException ex) {
            throw new IllegalStateException("附件读取失败: " + originalFilename, ex);
        }
    }

    private byte[] resolveFileContent(AttachmentEntity entity) {
        byte[] databaseContent = entity.getFileContent();
        if (databaseContent != null) {
            return databaseContent;
        }
        String storagePath = trimToNull(entity.getStoragePath());
        if (storagePath == null) {
            throw new IllegalArgumentException("附件文件不存在");
        }
        Path path = Path.of(storagePath);
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("附件文件不存在");
        }
        try {
            return Files.readAllBytes(path);
        } catch (IOException ex) {
            throw new IllegalStateException("附件文件读取失败", ex);
        }
    }

    private void validateIntegrity(AttachmentEntity entity, byte[] content) {
        if (entity.getFileSize() != null && entity.getFileSize() != content.length) {
            throw new IllegalStateException("附件内容完整性校验失败");
        }
        String expectedSha256 = trimToNull(entity.getFileSha256());
        if (expectedSha256 != null && !expectedSha256.equalsIgnoreCase(sha256(content))) {
            throw new IllegalStateException("附件内容完整性校验失败");
        }
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256", ex);
        }
    }

    private void deletePhysicalFile(String storagePath) {
        String normalized = trimToNull(storagePath);
        if (normalized == null) {
            return;
        }
        try {
            Files.deleteIfExists(Path.of(normalized));
        } catch (IOException ex) {
            throw new IllegalStateException("附件文件删除失败", ex);
        }
    }

    private AttachmentResponse toResponse(AttachmentEntity entity) {
        String category = switch (entity.getBizType()) {
            case INTAKE_SCREENSHOT -> "截图";
            case INTAKE_DELIVERY_FILE -> "数据文件";
            default -> "附件";
        };
        return new AttachmentResponse(
                entity.getId(),
                category,
                entity.getFileName(),
                entity.getContentType(),
                "/api/attachments/" + entity.getId() + "/download",
                isPreviewable(entity.getFileName(), entity.getContentType()),
                entity.getCreatedAt()
        );
    }

    private boolean isPreviewable(String fileName, String contentType) {
        String normalizedContentType = contentType == null ? "" : contentType.toLowerCase();
        if (normalizedContentType.startsWith("image/") || normalizedContentType.equals("application/pdf")) {
            return true;
        }
        String normalizedFileName = fileName == null ? "" : fileName.toLowerCase();
        return normalizedFileName.endsWith(".png")
                || normalizedFileName.endsWith(".jpg")
                || normalizedFileName.endsWith(".jpeg")
                || normalizedFileName.endsWith(".gif")
                || normalizedFileName.endsWith(".webp")
                || normalizedFileName.endsWith(".bmp")
                || normalizedFileName.endsWith(".svg")
                || normalizedFileName.endsWith(".pdf");
    }

    private String sanitizeFileName(String value) {
        if (value == null) {
            return null;
        }
        return value.replace("\\", "_").replace("/", "_").trim();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record AttachmentResource(AttachmentEntity entity, Resource resource) {
    }

    public record AttachmentReplaceResult(AttachmentResponse before, AttachmentResponse after) {
    }

    public record AttachmentFileContext(Long id,
                                        String category,
                                        String fileName,
                                        String storagePath,
                                        String contentType,
                                        byte[] fileContent,
                                        Long fileSize,
                                        String fileSha256) {

        public AttachmentFileContext(Long id,
                                     String category,
                                     String fileName,
                                     String storagePath,
                                     String contentType) {
            this(id, category, fileName, storagePath, contentType, null, null, null);
        }
    }

    private record StoredFile(String fileName,
                              String contentType,
                              byte[] fileContent,
                              Long fileSize,
                              String fileSha256) {
    }
}
