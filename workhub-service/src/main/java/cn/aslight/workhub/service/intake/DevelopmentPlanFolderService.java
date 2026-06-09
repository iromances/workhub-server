package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.dao.intake.IntakeMapper;
import cn.aslight.workhub.dao.project.ProjectMapper;
import cn.aslight.workhub.model.intake.IntakeRecordEntity;
import cn.aslight.workhub.model.intake.IntakeRequirementFolderResponse;
import cn.aslight.workhub.model.intake.IntakeStructuredData;
import cn.aslight.workhub.model.project.ProjectDetailResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 知识库开发方案文件夹服务。
 */
@Service
public class DevelopmentPlanFolderService {

    private final IntakeMapper intakeMapper;
    private final ProjectMapper projectMapper;
    private final ProjectKnowledgeBaseService projectKnowledgeBaseService;
    private final ObjectMapper objectMapper;
    private final PathOpener pathOpener;

    @Autowired
    public DevelopmentPlanFolderService(IntakeMapper intakeMapper,
                                        ProjectMapper projectMapper,
                                        ProjectKnowledgeBaseService projectKnowledgeBaseService,
                                        ObjectMapper objectMapper) {
        this(intakeMapper, projectMapper, projectKnowledgeBaseService, objectMapper, new SystemPathOpener());
    }

    DevelopmentPlanFolderService(IntakeMapper intakeMapper,
                                 ProjectMapper projectMapper,
                                 ProjectKnowledgeBaseService projectKnowledgeBaseService,
                                 ObjectMapper objectMapper,
                                 PathOpener pathOpener) {
        this.intakeMapper = intakeMapper;
        this.projectMapper = projectMapper;
        this.projectKnowledgeBaseService = projectKnowledgeBaseService;
        this.objectMapper = objectMapper;
        this.pathOpener = pathOpener;
    }

    public IntakeRequirementFolderResponse open(Long intakeId) {
        IntakeRecordEntity intake = intakeMapper.findById(intakeId);
        if (intake == null) {
            throw new IllegalArgumentException("需求不存在");
        }
        IntakeStructuredData structuredData = readStructuredData(intake.getStructuredDataJson());
        ProjectDetailResponse project = resolveProject(structuredData);
        ProjectKnowledgeBaseService.RequirementKnowledgeLocation location =
                projectKnowledgeBaseService.resolveRequirementIterationLocation(structuredData, project);
        Path folderPath = location.wikiFolderPath();
        try {
            Files.createDirectories(folderPath);
            pathOpener.open(folderPath);
        } catch (IOException ex) {
            throw new IllegalStateException("打开开发方案文件夹失败：" + folderPath, ex);
        }
        return new IntakeRequirementFolderResponse(
                intakeId,
                location.vaultRoot().toString(),
                folderPath.getFileName() == null ? folderPath.toString() : folderPath.getFileName().toString(),
                folderPath.toString(),
                true
        );
    }

    private ProjectDetailResponse resolveProject(IntakeStructuredData structuredData) {
        String businessLine = firstNonBlank(
                structuredData == null ? null : structuredData.businessLine(),
                structuredData == null ? null : structuredData.projectHint(),
                structuredData == null ? null : structuredData.department(),
                "未归类业务线"
        );
        ProjectDetailResponse project = projectMapper.findFirstDetailByBusinessLine(businessLine);
        if (project != null) {
            return project;
        }
        String projectName = firstNonBlank(structuredData == null ? null : structuredData.projectHint(), businessLine);
        return new ProjectDetailResponse(
                null,
                null,
                projectName,
                null,
                businessLine,
                null,
                null,
                null,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
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

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = trimToNull(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    interface PathOpener {
        void open(Path path) throws IOException;
    }

    private static class SystemPathOpener implements PathOpener {
        @Override
        public void open(Path path) throws IOException {
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
    }
}
