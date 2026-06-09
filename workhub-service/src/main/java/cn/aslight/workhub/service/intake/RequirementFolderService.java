package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.dao.intake.IntakeMapper;
import cn.aslight.workhub.model.intake.IntakeRecordEntity;
import cn.aslight.workhub.model.intake.IntakeRequirementFolderResponse;
import cn.aslight.workhub.model.intake.IntakeStructuredData;
import cn.aslight.workhub.service.system.SysConfigService;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * 需求本地文件夹服务。
 */
@Service
public class RequirementFolderService {

    private static final String CONFIG_GROUP = "intake.requirementFolder";
    private static final String CONFIG_KEY_BASE_PATH = "basePath";
    private static final String DEFAULT_BASE_PATH = "/Users/aslight/Desktop/进行中的需求";

    private final IntakeMapper intakeMapper;
    private final SysConfigService sysConfigService;
    private final ObjectMapper objectMapper;

    public RequirementFolderService(IntakeMapper intakeMapper,
                                    SysConfigService sysConfigService,
                                    ObjectMapper objectMapper) {
        this.intakeMapper = intakeMapper;
        this.sysConfigService = sysConfigService;
        this.objectMapper = objectMapper;
    }

    public IntakeRequirementFolderResponse open(Long intakeId) {
        IntakeRecordEntity intake = intakeMapper.findById(intakeId);
        if (intake == null) {
            throw new IllegalArgumentException("需求不存在");
        }
        IntakeStructuredData structuredData = readStructuredData(intake.getStructuredDataJson());
        Path basePath = resolveBasePath();
        String folderName = buildFolderName(intake, structuredData);
        Path folderPath = basePath.resolve(folderName).normalize();
        ensureInsideBasePath(basePath, folderPath);
        try {
            Files.createDirectories(folderPath);
            openPath(folderPath);
        } catch (IOException ex) {
            throw new IllegalStateException("打开需求文件夹失败：" + folderPath, ex);
        }
        return new IntakeRequirementFolderResponse(
                intakeId,
                basePath.toString(),
                folderName,
                folderPath.toString(),
                true
        );
    }

    private Path resolveBasePath() {
        String configured = trimToNull(sysConfigService.findPlainValue(CONFIG_GROUP, CONFIG_KEY_BASE_PATH));
        String rawPath = configured == null ? DEFAULT_BASE_PATH : configured;
        if (rawPath.startsWith("~/")) {
            rawPath = System.getProperty("user.home") + rawPath.substring(1);
        }
        return Path.of(rawPath).toAbsolutePath().normalize();
    }

    private String buildFolderName(IntakeRecordEntity intake, IntakeStructuredData structuredData) {
        String approvalCode = firstNonBlank(structuredData == null ? null : structuredData.approvalCode(), "需求" + intake.getId());
        String requirementName = firstNonBlank(
                structuredData == null ? null : structuredData.requirementName(),
                firstNonBlank(structuredData == null ? null : structuredData.approvalTitle(), "未命名需求")
        );
        return sanitizePathSegment(approvalCode + "-" + requirementName);
    }

    private void ensureInsideBasePath(Path basePath, Path folderPath) {
        if (!folderPath.startsWith(basePath)) {
            throw new IllegalArgumentException("需求文件夹路径非法");
        }
    }

    private void openPath(Path path) throws IOException {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            Desktop.getDesktop().open(path.toFile());
            return;
        }
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("mac")) {
            new ProcessBuilder("open", path.toString()).start();
            return;
        }
        if (osName.contains("win")) {
            new ProcessBuilder("explorer", path.toString()).start();
            return;
        }
        new ProcessBuilder("xdg-open", path.toString()).start();
    }

    private IntakeStructuredData readStructuredData(String structuredDataJson) {
        if (structuredDataJson == null || structuredDataJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(structuredDataJson, IntakeStructuredData.class);
        } catch (JacksonException ex) {
            throw new IllegalStateException("结构化需求解析失败", ex);
        }
    }

    private String sanitizePathSegment(String value) {
        String sanitized = value
                .replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_")
                .replaceAll("\\s+", " ")
                .trim();
        if (sanitized.isBlank()) {
            return "未命名需求";
        }
        return sanitized.length() > 180 ? sanitized.substring(0, 180).trim() : sanitized;
    }

    private String firstNonBlank(String first, String second) {
        String normalized = trimToNull(first);
        return normalized == null ? trimToNull(second) : normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
