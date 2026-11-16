package cn.aslight.workhub.controller.agent;

import cn.aslight.workhub.model.agent.AgentToolAdminResponse;
import cn.aslight.workhub.model.agent.AgentToolSaveRequest;
import cn.aslight.workhub.service.agent.AgentToolAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 核对HTTP路由、参数绑定与校验提示；权限与数据行为由Service的真实权限/H2测试覆盖。 */
class AgentToolControllerTest {
    private final AgentToolAdminService service = mock(AgentToolAdminService.class);
    private final UsernamePasswordAuthenticationToken actor = new UsernamePasswordAuthenticationToken("owner", "", List.of());
    private final AgentToolAdminResponse row =
            new AgentToolAdminResponse("workhub.bugs.list", "缺陷列表", "读取缺陷工作项", true, List.of("work-item:view"), true, false, 1);

    @Test void listBindsAuthenticatedUser() throws Exception {
        when(service.list("owner")).thenReturn(List.of(row));
        MockMvcBuilders.standaloneSetup(new AgentToolController(service)).build()
                .perform(get("/api/agent-tools").principal(actor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("workhub.bugs.list"))
                .andExpect(jsonPath("$.data[0].name").value("缺陷列表"))
                .andExpect(jsonPath("$.data[0].maintained").value(true));
        verify(service).list("owner");
    }

    @Test void updateBindsBodyAndRoutesToService() throws Exception {
        when(service.update(eq("workhub.bugs.list"), any(), eq("owner"))).thenReturn(row);
        MockMvcBuilders.standaloneSetup(new AgentToolController(service)).build()
                .perform(put("/api/agent-tools/workhub.bugs.list").principal(actor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0,\"name\":\"缺陷列表\",\"description\":\"读取缺陷工作项\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1));
        verify(service).update(eq("workhub.bugs.list"),
                argThat((AgentToolSaveRequest request) -> request.expectedVersion() == 0L
                        && request.name().equals("缺陷列表") && request.description().equals("读取缺陷工作项")), eq("owner"));
    }

    @Test void updateRejectsBlankNameOrMissingDescription() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new AgentToolController(service)).build();
        mvc.perform(put("/api/agent-tools/workhub.bugs.list").principal(actor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0,\"name\":\"  \",\"description\":\"读取缺陷工作项\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/agent-tools/workhub.bugs.list").principal(actor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0,\"name\":\"缺陷列表\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
