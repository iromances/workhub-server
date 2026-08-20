package cn.aslight.workhub.controller.ai;

import cn.aslight.workhub.model.ai.AiProviderProbeResponse;
import cn.aslight.workhub.model.ai.AiProviderConnectionTestResponse;
import cn.aslight.workhub.service.ai.AiGatewayConfigService;
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

class AiConfigCompatibilityControllerTest {

    @Test
    void probeProvider_shouldExposeCompatibilityEndpoint() throws Exception {
        AiProviderProbeService probeService = mock(AiProviderProbeService.class);
        when(probeService.probe(
                eq(7L),
                argThat(request -> "gpt-5.4".equals(request.getModel())),
                eq("admin"),
                eq("10.0.0.1")
        )).thenReturn(new AiProviderProbeResponse(
                7L, "openai-api", "gpt-5.4", true, 128L, "AI Provider 连接探针通过"));

        AiConfigCompatibilityController controller = new AiConfigCompatibilityController(
                mock(AiGatewayConfigService.class),
                mock(AiProviderConfigService.class),
                probeService,
                mock(AiProviderConnectionTestService.class),
                mock(AiUseCaseConfigService.class)
        );
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(post("/api/manage/system/ai-provider/7/probe")
                        .principal(new UsernamePasswordAuthenticationToken("admin", ""))
                        .header("X-Forwarded-For", "10.0.0.1, 10.0.0.2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"model\":\"gpt-5.4\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.providerId").value(7))
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.durationMs").value(128));

        verify(probeService).probe(
                eq(7L),
                argThat(request -> "gpt-5.4".equals(request.getModel())),
                eq("admin"),
                eq("10.0.0.1")
        );
    }

    @Test
    void testProviderConnection_shouldExposeCurrentFormCompatibilityEndpoint() throws Exception {
        AiProviderConnectionTestService connectionTestService = mock(AiProviderConnectionTestService.class);
        when(connectionTestService.test(
                argThat(request -> request.getId() == null
                        && "API".equals(request.getChannelType())
                        && "gpt-5.4".equals(request.getDefaultModel())),
                eq("admin"),
                eq("10.0.0.1")
        )).thenReturn(new AiProviderConnectionTestResponse(
                true, "API", "gpt-5.4", 96L, "连接成功"));

        AiConfigCompatibilityController controller = new AiConfigCompatibilityController(
                mock(AiGatewayConfigService.class),
                mock(AiProviderConfigService.class),
                mock(AiProviderProbeService.class),
                connectionTestService,
                mock(AiUseCaseConfigService.class)
        );
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(post("/api/manage/system/ai-provider/test-connection")
                        .principal(new UsernamePasswordAuthenticationToken("admin", ""))
                        .header("X-Forwarded-For", "10.0.0.1, 10.0.0.2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"providerCode\":\"openai-api\",\"providerName\":\"OpenAI\","
                                + "\"channelType\":\"API\",\"modelProvider\":\"OpenAI\","
                                + "\"defaultModel\":\"gpt-5.4\",\"apiKey\":\"sk-test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.channelType").value("API"))
                .andExpect(jsonPath("$.data.elapsedMillis").value(96))
                .andExpect(jsonPath("$.data.message").value("连接成功"));

        verify(connectionTestService).test(
                argThat(request -> request.getId() == null
                        && "API".equals(request.getChannelType())
                        && "gpt-5.4".equals(request.getDefaultModel())),
                eq("admin"),
                eq("10.0.0.1")
        );
    }
}
