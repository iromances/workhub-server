package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.model.project.ProjectDetailResponse;
import cn.aslight.workhub.dao.project.BusinessLineMapper;
import cn.aslight.workhub.service.system.SysConfigService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

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
}
