package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.dao.intake.IntakeMapper;
import cn.aslight.workhub.dao.project.ProjectMapper;
import cn.aslight.workhub.model.intake.IntakeRecordEntity;
import cn.aslight.workhub.model.intake.IntakeRequirementFolderResponse;
import cn.aslight.workhub.model.intake.IntakeStructuredData;
import cn.aslight.workhub.model.project.ProjectDetailResponse;
import cn.aslight.workhub.service.system.SysConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DevelopmentPlanFolderServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void open_shouldCreateAndOpenDevelopmentPlanYearFolderInKnowledgeVault() throws Exception {
        Path vault = tempDir.resolve("Company Obsidian Vault");
        ObjectMapper objectMapper = new ObjectMapper();
        IntakeRecordEntity intake = new IntakeRecordEntity();
        intake.setId(101L);
        intake.setStructuredDataJson(objectMapper.writeValueAsString(structuredData()));

        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        when(intakeMapper.findById(101L)).thenReturn(intake);

        ProjectMapper projectMapper = mock(ProjectMapper.class);
        when(projectMapper.findFirstDetailByBusinessLine("账单管理")).thenReturn(project());

        SysConfigService sysConfigService = mock(SysConfigService.class);
        when(sysConfigService.findPlainValue(ProjectKnowledgeBaseService.CONFIG_GROUP, ProjectKnowledgeBaseService.CONFIG_KEY_VAULT_PATH))
                .thenReturn(vault.toString());

        RecordingPathOpener opener = new RecordingPathOpener();
        ProjectKnowledgeBaseService knowledgeBaseService = new ProjectKnowledgeBaseService(sysConfigService);
        DevelopmentPlanFolderService service = new DevelopmentPlanFolderService(
                intakeMapper,
                projectMapper,
                knowledgeBaseService,
                objectMapper,
                opener
        );

        IntakeRequirementFolderResponse response = service.open(101L);

        Path expectedFolder = vault.resolve("wiki")
                .resolve("projects")
                .resolve("账单管理")
                .resolve("需求迭代")
                .resolve("趣学呗")
                .resolve("2026");
        assertTrue(Files.isDirectory(expectedFolder));
        assertEquals(expectedFolder, opener.openedPath);
        assertEquals(101L, response.intakeId());
        assertEquals(vault.toString(), response.basePath());
        assertEquals("2026", response.folderName());
        assertEquals(expectedFolder.toString(), response.folderPath());
        assertTrue(response.opened());
    }

    private IntakeStructuredData structuredData() {
        return new IntakeStructuredData(
                "需求审批",
                "趣学呗平台账户展示切换",
                "周拓",
                null,
                "202604220007",
                "2026/4/22 10:00",
                "研发需求",
                null,
                null,
                "趣学呗平台账户切换",
                "趣学呗平台账户展示切换",
                "账单管理下趣学呗平台账户展示切换为新主体账户。",
                "账单管理",
                "账单管理",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "账单管理",
                List.of(),
                List.of(),
                null
        );
    }

    private ProjectDetailResponse project() {
        return new ProjectDetailResponse(
                2L,
                "QXB",
                "趣学呗",
                "研发",
                "账单管理",
                "owner",
                "ACTIVE",
                "账单管理相关系统",
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }

    private static class RecordingPathOpener implements DevelopmentPlanFolderService.PathOpener {
        private Path openedPath;

        @Override
        public void open(Path path) {
            openedPath = path;
        }
    }
}
