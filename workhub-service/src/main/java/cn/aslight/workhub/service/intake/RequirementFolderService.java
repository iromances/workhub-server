package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.dao.intake.IntakeMapper;
import cn.aslight.workhub.model.intake.IntakeRecordEntity;
import cn.aslight.workhub.model.intake.IntakeRequirementFolderResponse;
import cn.aslight.workhub.model.intake.IntakeStructuredData;
import cn.aslight.workhub.service.system.SysConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.LinkOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.stream.Stream;
import java.util.Locale;

/**
 * 需求本地文件夹服务。
 */
@Service
public class RequirementFolderService {

    private static final Logger log = LoggerFactory.getLogger(RequirementFolderService.class);

    private static final String CONFIG_GROUP = "intake.requirementFolder";
    private static final String CONFIG_KEY_BASE_PATH = "basePath";
    private static final String DEFAULT_BASE_PATH = "/Users/aslight/Desktop/进行中的需求";

    private final IntakeMapper intakeMapper;
    private final SysConfigService sysConfigService;
    private static final String OWNER_FILE = ".workhub-intake-id";

    private final ObjectMapper objectMapper;
    private final RequirementMaterialExportService materialExportService;

    public RequirementFolderService(IntakeMapper intakeMapper,
                                    SysConfigService sysConfigService,
                                    ObjectMapper objectMapper,
                                    RequirementMaterialExportService materialExportService) {
        this.intakeMapper = intakeMapper;
        this.sysConfigService = sysConfigService;
        this.objectMapper = objectMapper;
        this.materialExportService = materialExportService;
    }

    public void scheduleMaterialExport(Long intakeId) {
        IntakeRecordEntity intake = intakeMapper.findById(intakeId);
        if (intake == null || isRecognitionIncomplete(intake)) {
            return;
        }
        materialExportService.scheduleAfterCommit(intakeId, () -> ensureFolder(intakeId));
    }

    public IntakeRequirementFolderResponse open(Long intakeId) {
        IntakeRecordEntity intake = intakeMapper.findById(intakeId);
        if (intake == null) {
            throw new IllegalArgumentException("需求不存在");
        }
        // 手动入口只创建并打开目录，不触发材料导出或目录归属校验。
        Path folderPath = resolveBasePath().resolve(buildFolderName(intake, null));
        try {
            Files.createDirectories(folderPath);
            openPath(folderPath);
        } catch (IOException ex) {
            throw new IllegalStateException("打开需求文件夹失败：" + folderPath, ex);
        }
        return new IntakeRequirementFolderResponse(
                intakeId,
                folderPath.getParent().toString(),
                folderPath.getFileName().toString(),
                folderPath.toString(),
                true
        );
    }

    /** 同一服务内串行分配目录，避免名称相同的不同需求竞争认领。 */
    synchronized Path ensureFolder(Long intakeId) {
        IntakeRecordEntity intake = intakeMapper.findById(intakeId);
        if (intake == null) {
            throw new IllegalArgumentException("需求不存在");
        }
        if (isRecognitionIncomplete(intake)) {
            throw new IllegalStateException("需求识别尚未成功，暂不自动导出需求材料");
        }
        Path configuredBase = resolveBasePath();
        try {
            Files.createDirectories(configuredBase);
            // 配置目录可以使用 ~/ 或系统路径别名，子目录必须位于解析后的真实基础路径内。
            Path basePath = configuredBase.toRealPath();
            IntakeStructuredData structuredData = readStructuredData(intake.getStructuredDataJson());
            String folderName = buildFolderName(intake, structuredData);
            List<Path> candidates;
            try (Stream<Path> entries = Files.list(basePath)) {
                candidates = entries.sorted().toList();
            }
            for (Path candidate : candidates) {
                if (Files.isDirectory(candidate, LinkOption.NOFOLLOW_LINKS)
                        && intakeId.toString().equals(readOwner(candidate))) {
                    return completePlaceholderName(candidate, folderName, intakeId);
                }
            }
            Path folder = basePath.resolve(folderName).normalize();
            ensureInsideBasePath(basePath, folder);
            String owner = readOwner(folder);
            if (owner != null && !intakeId.toString().equals(owner)) {
                folder = basePath.resolve(folderName + "-需求" + intakeId);
                owner = readOwner(folder);
                if (owner != null && !intakeId.toString().equals(owner)) {
                    throw new IOException("需求目录已被其他需求占用：" + folder);
                }
            }
            RequirementMaterialExportService.requireDirectory(folder);
            Path marker = folder.resolve(OWNER_FILE);
            if (owner == null) {
                Files.writeString(marker, intakeId.toString(), StandardOpenOption.CREATE_NEW);
            }
            return folder;
        } catch (IOException ex) {
            throw new IllegalStateException("创建需求文件夹失败：" + configuredBase, ex);
        }
    }

    private boolean isRecognitionIncomplete(IntakeRecordEntity intake) {
        String status = intake.getEnrichmentStatus();
        return IntakeEnrichmentStatus.PENDING.equals(status)
                || IntakeEnrichmentStatus.RUNNING.equals(status)
                || IntakeEnrichmentStatus.FAILED.equals(status);
    }

    /** 将识别前生成的兜底目录整体更名，避免材料永久停留在“未命名需求”下。 */
    private Path completePlaceholderName(Path existing, String folderName, Long intakeId) throws IOException {
        String currentName = existing.getFileName().toString();
        boolean missingCode = currentName.startsWith("需求" + intakeId + "-");
        boolean missingName = currentName.endsWith("-未命名需求")
                || currentName.endsWith("-未命名需求-需求" + intakeId);
        if ((!missingCode && !missingName) || currentName.equals(folderName)) {
            return existing;
        }
        // 不因识别字段暂时缺失而把已补齐的编号或名称退回兜底值。
        boolean nextMissingCode = folderName.startsWith("需求" + intakeId + "-");
        boolean nextMissingName = folderName.endsWith("-未命名需求");
        if ((!missingCode && nextMissingCode) || (!missingName && nextMissingName)) {
            return existing;
        }
        Path target = existing.getParent().resolve(folderName);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            target = existing.getParent().resolve(folderName + "-需求" + intakeId);
        }
        if (target.equals(existing)) {
            return existing;
        }
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("需求目录更名目标已存在，原目录已保留：" + target);
        }
        // 不使用 REPLACE_EXISTING，任何目标冲突均保留原目录供重试。
        Files.move(existing, target);
        log.info("需求兜底目录名称已补全。intakeId={}, from={}, to={}", intakeId, existing, target);
        return target;
    }

    private String readOwner(Path folder) throws IOException {
        if (Files.isSymbolicLink(folder)) {
            throw new IOException("需求目录不能是符号链接：" + folder);
        }
        Path marker = folder.resolve(OWNER_FILE);
        if (!Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) {
            return null;
        }
        if (!Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS) || Files.size(marker) > 32) {
            throw new IOException("需求目录归属标记无效：" + marker);
        }
        String owner = Files.readString(marker).trim();
        if (!owner.matches("[0-9]+")) {
            throw new IOException("需求目录归属标记无效：" + marker);
        }
        return owner;
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
        String approvalCode = firstNonBlank(intake.getApprovalCode(),
                firstNonBlank(structuredData == null ? null : structuredData.approvalCode(), "需求" + intake.getId()));
        String requirementName = firstNonBlank(
                firstNonBlank(intake.getRequirementName(), structuredData == null ? null : structuredData.requirementName()),
                firstNonBlank(structuredData == null ? null : structuredData.approvalTitle(), "未命名需求")
        );
        return sanitizePathSegment(approvalCode + "-" + requirementName);
    }

    private void ensureInsideBasePath(Path basePath, Path folderPath) {
        if (!folderPath.startsWith(basePath)) {
            throw new IllegalArgumentException("需求文件夹路径非法");
        }
    }

    void openPath(Path path) throws IOException {
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
        return RequirementMaterialExportService.truncateUtf8(sanitized, 180).trim();
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
