package cn.aslight.workhub.service.attachment;

import cn.aslight.workhub.config.StorageProperties;
import cn.aslight.workhub.model.attachment.AttachmentResponse;
import cn.aslight.workhub.dao.attachment.AttachmentMapper;
import cn.aslight.workhub.model.attachment.AttachmentEntity;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 附件存储与关联服务。
 */
@Service
public class AttachmentService {

    public static final String INTAKE_SCREENSHOT = "INTAKE_SCREENSHOT";
    public static final String INTAKE_ATTACHMENT = "INTAKE_ATTACHMENT";

    private static final DateTimeFormatter PATH_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final AttachmentMapper attachmentMapper;
    private final StorageProperties storageProperties;

    public AttachmentService(AttachmentMapper attachmentMapper, StorageProperties storageProperties) {
        this.attachmentMapper = attachmentMapper;
        this.storageProperties = storageProperties;
    }

    @Transactional
    public void saveIntakeFiles(Long intakeId, List<MultipartFile> screenshots, List<MultipartFile> attachments) {
        storeFiles(intakeId, screenshots, INTAKE_SCREENSHOT);
        storeFiles(intakeId, attachments, INTAKE_ATTACHMENT);
    }

    public List<AttachmentResponse> listIntakeAttachments(Long intakeId) {
        List<AttachmentEntity> entities = attachmentMapper.findByBiz(intakeId, List.of(INTAKE_SCREENSHOT, INTAKE_ATTACHMENT));
        List<AttachmentResponse> items = new ArrayList<>(entities.size());
        for (AttachmentEntity entity : entities) {
            items.add(toResponse(entity));
        }
        return items;
    }

    public List<AttachmentFileContext> listIntakeFileContexts(Long intakeId) {
        return attachmentMapper.findByBiz(intakeId, List.of(INTAKE_SCREENSHOT, INTAKE_ATTACHMENT)).stream()
                .map(entity -> new AttachmentFileContext(
                        entity.getId(),
                        INTAKE_SCREENSHOT.equals(entity.getBizType()) ? "截图" : "附件",
                        entity.getFileName(),
                        entity.getStoragePath(),
                        entity.getContentType()
                ))
                .toList();
    }

    public AttachmentResource loadAsResource(Long attachmentId) {
        AttachmentEntity entity = attachmentMapper.findById(attachmentId);
        if (entity == null) {
            throw new IllegalArgumentException("附件不存在");
        }
        Path path = Path.of(entity.getStoragePath());
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("附件文件不存在");
        }
        return new AttachmentResource(entity, new FileSystemResource(path));
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
        String originalFilename = sanitizeFileName(file.getOriginalFilename());
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("附件文件名不能为空");
        }
        String storedName = PATH_TIME_FORMATTER.format(LocalDateTime.now())
                + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12)
                + "-" + originalFilename;
        Path baseDir = Path.of(storageProperties.getLocalPath()).toAbsolutePath().normalize()
                .resolve("intake")
                .resolve(String.valueOf(intakeId));
        Path target = baseDir.resolve(storedName).normalize();
        try {
            Files.createDirectories(baseDir);
            file.transferTo(target);
        } catch (IOException ex) {
            throw new IllegalStateException("附件保存失败: " + originalFilename, ex);
        }

        AttachmentEntity entity = new AttachmentEntity();
        entity.setBizType(bizType);
        entity.setBizId(intakeId);
        entity.setFileName(originalFilename);
        entity.setStoragePath(target.toString());
        entity.setContentType(trimToNull(file.getContentType()));
        attachmentMapper.insert(entity);
    }

    private AttachmentResponse toResponse(AttachmentEntity entity) {
        String category = INTAKE_SCREENSHOT.equals(entity.getBizType()) ? "截图" : "附件";
        return new AttachmentResponse(
                entity.getId(),
                category,
                entity.getFileName(),
                entity.getContentType(),
                "/api/attachments/" + entity.getId() + "/download",
                isPreviewable(entity.getContentType()),
                entity.getCreatedAt()
        );
    }

    private boolean isPreviewable(String contentType) {
        return contentType != null && contentType.startsWith("image/");
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

    public record AttachmentFileContext(Long id,
                                        String category,
                                        String fileName,
                                        String storagePath,
                                        String contentType) {
    }
}
