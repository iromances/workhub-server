package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.model.project.ProjectDetailResponse;
import cn.aslight.workhub.dao.project.BusinessLineMapper;
import cn.aslight.workhub.model.project.BusinessLineEntity;
import cn.aslight.workhub.service.system.SysConfigService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GitlabRepositoryServiceTest {

    @Test
    void chooseBestProject_shouldPreferProjectCodeAndNameMatch() {
        GitlabRepositoryService service = new GitlabRepositoryService(
                mock(SysConfigService.class),
                mock(BusinessLineMapper.class),
                new ObjectMapper()
        );
        ProjectDetailResponse project = new ProjectDetailResponse(
                8L,
                "WCH-PAY",
                "沃橙支付项目",
                "研发",
                "供应链科技",
                "admin",
                "ACTIVE",
                null,
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        GitlabRepositoryService.GitlabProject selected = service.chooseBestProject(
                project,
                List.of(
                        new GitlabRepositoryService.GitlabProject("1", "公共组件", "common", "platform/common", "http://gitlab/common.git", "http://gitlab/common"),
                        new GitlabRepositoryService.GitlabProject("2", "沃橙支付项目", "wch-pay", "supply/wch-pay", "http://gitlab/supply/wch-pay.git", "http://gitlab/supply/wch-pay")
                )
        );

        assertEquals("http://gitlab/supply/wch-pay.git", selected.httpUrlToRepo());
    }

    @Test
    void repositoryDirectoryName_shouldKeepGitlabProjectPathAsIs() {
        GitlabRepositoryService service = new GitlabRepositoryService(
                mock(SysConfigService.class),
                mock(BusinessLineMapper.class),
                new ObjectMapper()
        );
        GitlabRepositoryService.GitlabProject project = new GitlabRepositoryService.GitlabProject(
                "3",
                "Foo Service",
                "foo service (new)",
                "platform/foo service (new)",
                "http://gitlab/platform/foo-service.git",
                "http://gitlab/platform/foo-service"
        );

        assertEquals("foo service (new)", service.repositoryDirectoryName(project, project.httpUrlToRepo()));
    }

    @Test
    void repositoryDirectoryName_shouldKeepInferredRepositoryNameAsIs() {
        GitlabRepositoryService service = new GitlabRepositoryService(
                mock(SysConfigService.class),
                mock(BusinessLineMapper.class),
                new ObjectMapper()
        );
        GitlabRepositoryService.GitlabProject project = new GitlabRepositoryService.GitlabProject(
                "4",
                "Foo Service",
                null,
                "platform/foo service",
                "http://gitlab/platform/foo service.git",
                "http://gitlab/platform/foo service"
        );

        assertEquals("foo service", service.repositoryDirectoryName(project, project.httpUrlToRepo()));
    }

    @Test
    void groupCacheRoot_shouldUseGitlabGroupNameAsDirectoryName() {
        GitlabRepositoryService service = new GitlabRepositoryService(
                mock(SysConfigService.class),
                mock(BusinessLineMapper.class),
                new ObjectMapper()
        );

        assertEquals(
                Path.of("data", "git-cache", "ca-assets").toAbsolutePath().normalize(),
                service.groupCacheRoot("ca-assets")
        );
    }

    @Test
    void findGitlabGroupName_shouldResolveBusinessLineCode() throws Exception {
        BusinessLineMapper businessLineMapper = mock(BusinessLineMapper.class);
        BusinessLineEntity businessLine = new BusinessLineEntity();
        businessLine.setBusinessLineCode("BL000003");
        businessLine.setBusinessLineName("嘉泰资产平台");
        businessLine.setGitlabGroupName("th-jiatai-amp");
        businessLine.setEnabled(true);
        when(businessLineMapper.findByCode("BL000003")).thenReturn(businessLine);

        GitlabRepositoryService service = new GitlabRepositoryService(
                mock(SysConfigService.class),
                businessLineMapper,
                new ObjectMapper()
        );

        Method method = GitlabRepositoryService.class.getDeclaredMethod("findGitlabGroupName", String.class);
        method.setAccessible(true);

        assertEquals("th-jiatai-amp", method.invoke(service, "BL000003"));
    }
}
