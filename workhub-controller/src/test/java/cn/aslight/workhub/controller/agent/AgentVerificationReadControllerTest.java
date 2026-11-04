package cn.aslight.workhub.controller.agent;

import cn.aslight.workhub.service.agent.AgentReleasePlanService;
import cn.aslight.workhub.service.agent.AgentVerificationService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 核对HTTP参数绑定和原始证据路由；数据权限由Service的真实权限/H2测试覆盖。 */
class AgentVerificationReadControllerTest {
    private final AgentVerificationService service=mock(AgentVerificationService.class);
    private final ObjectMapper json=new ObjectMapper();
    private final UsernamePasswordAuthenticationToken actor=new UsernamePasswordAuthenticationToken("owner","",List.of());
    @Test void sampleHistoryBindsAuthenticatedUserStableCursorAndPageSize() throws Exception {
        var mvc=MockMvcBuilders.standaloneSetup(new AgentReleaseController(mock(AgentReleasePlanService.class),service)).build();
        when(service.samplesForUser("plan","owner","health",10,30L,5)).thenReturn(json.readTree("{\"throughOrder\":30,\"items\":[],\"hasMore\":false}"));
        mvc.perform(get("/api/agent-release-plans/plan/verification/samples").principal(actor)
                .param("checkId","health").param("afterOrder","10").param("throughOrder","30").param("pageSize","5"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.throughOrder").value(30));
        verify(service).samplesForUser("plan","owner","health",10,30L,5);
        mvc.perform(get("/api/agent-release-plans/plan/verification/samples").principal(actor)).andExpect(status().isBadRequest());
    }
    @Test void evidenceRouteRequiresBothObservationAndExactPointerWithinSample() throws Exception {
        var mvc=MockMvcBuilders.standaloneSetup(new AgentReleaseController(mock(AgentReleasePlanService.class),service)).build();
        when(service.evidenceForUser("plan","owner","sample","observation","/data/results/0"))
                .thenReturn(json.readTree("{\"pointer\":\"/data/results/0\",\"payload\":{\"data\":{\"safe\":true}}}"));
        mvc.perform(get("/api/agent-release-plans/plan/verification/samples/sample/evidence").principal(actor)
                .param("observationId","observation").param("pointer","/data/results/0"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.payload.data.safe").value(true));
        verify(service).evidenceForUser("plan","owner","sample","observation","/data/results/0");
        mvc.perform(get("/api/agent-release-plans/plan/verification/samples/sample/evidence").principal(actor)
                .param("observationId","observation")).andExpect(status().isBadRequest());
    }
}
