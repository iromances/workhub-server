package cn.aslight.workhub.controller.ai;

import cn.aslight.workhub.model.ai.AiProviderConnectionTestResponse;
import cn.aslight.workhub.service.ai.AiProviderConfigService;
import cn.aslight.workhub.service.ai.AiProviderConnectionTestService;
import cn.aslight.workhub.service.ai.AiProviderProbeService;
import cn.aslight.workhub.service.ai.AiUseCaseConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AiConfigControllerTest {

    @Test
    void testProviderConnection_shouldExposeNativeEndpoint() throws Exception {
        AiProviderConnectionTestService connectionTestService = mock(AiProviderConnectionTestService.class);
        when(connectionTestService.test(
                argThat(request -> "CLI".equals(request.getChannelType())
                        && "gpt-5.4".equals(request.getDefaultModel())),
                eq("admin"),
                eq("10.0.0.1")
        )).thenReturn(new AiProviderConnectionTestResponse(
                true, "CLI", "gpt-5.4", 121L, "连接成功"));

        AiConfigController controller = new AiConfigController(
                mock(AiProviderConfigService.class),
                mock(AiUseCaseConfigService.class),
                mock(AiProviderProbeService.class),
                connectionTestService
        );
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(post("/api/system/ai-providers/test-connection")
                        .principal(new UsernamePasswordAuthenticationToken("admin", ""))
                        .header("X-Forwarded-For", "10.0.0.1, 10.0.0.2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"providerCode\":\"codex-cli\",\"providerName\":\"Codex CLI\","
                                + "\"channelType\":\"CLI\",\"modelProvider\":\"OpenAI\","
                                + "\"defaultModel\":\"gpt-5.4\",\"cliCommand\":\"codex\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.channelType").value("CLI"))
                .andExpect(jsonPath("$.data.elapsedMillis").value(121));

        verify(connectionTestService).test(
                argThat(request -> "CLI".equals(request.getChannelType())
                        && "gpt-5.4".equals(request.getDefaultModel())),
                eq("admin"),
                eq("10.0.0.1")
        );
    }
}
