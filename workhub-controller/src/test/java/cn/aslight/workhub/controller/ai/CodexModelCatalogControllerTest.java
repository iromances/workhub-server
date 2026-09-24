package cn.aslight.workhub.controller.ai;

import cn.aslight.workhub.model.ai.CodexModelCatalogRequest;
import cn.aslight.workhub.common.exception.GlobalExceptionHandler;
import cn.aslight.workhub.model.ai.CodexModelCatalogResponse;
import cn.aslight.workhub.service.ai.CodexModelCatalogService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CodexModelCatalogControllerTest {
    @Test
    void endpointReturnsRawCodesAndValidatesCommand() throws Exception {
        var service = mock(CodexModelCatalogService.class);
        when(service.query(any())).thenReturn(new CodexModelCatalogResponse(List.of(
                new CodexModelCatalogResponse.Model("CaseModel", "Model", true, "futureEffort",
                        List.of(new CodexModelCatalogResponse.ReasoningEffort("futureEffort", "new")))), Instant.now(), false));
        var mvc = MockMvcBuilders.standaloneSetup(new CodexModelCatalogController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/api/manage/system/ai-provider/codex-models")
                .contentType(MediaType.APPLICATION_JSON).content("{\"cliCommand\":\"codex\",\"forceRefresh\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.models[0].model").value("CaseModel"))
                .andExpect(jsonPath("$.data.models[0].defaultReasoningEffort").value("futureEffort"));
        mvc.perform(post("/api/manage/system/ai-provider/codex-models")
                .contentType(MediaType.APPLICATION_JSON).content("{\"cliCommand\":\"codex\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/manage/system/ai-provider/codex-models")
                .contentType(MediaType.APPLICATION_JSON).content("{\"cliCommand\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("cliCommand CLI 命令不能为空"));
        verify(service).query(new CodexModelCatalogRequest("codex", null, true));
        verify(service).query(new CodexModelCatalogRequest("codex", null, null));
        when(service.query(any())).thenThrow(new IllegalStateException("查询 Codex 模型超时，请重试"));
        mvc.perform(post("/api/manage/system/ai-provider/codex-models")
                .contentType(MediaType.APPLICATION_JSON).content("{\"cliCommand\":\"codex\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("查询 Codex 模型超时，请重试"));
    }

    @Test
    void methodSecurityAllowsOnlyConfigurationEditors() {
        try (var context = new AnnotationConfigApplicationContext(SecurityConfig.class)) {
            var controller = context.getBean(CodexModelCatalogController.class);
            var request = new CodexModelCatalogRequest("codex", null, false);
            SecurityContextHolder.clearContext();
            assertThrows(AuthenticationCredentialsNotFoundException.class, () -> controller.models(request));
            authenticate("system:ai-config:view");
            assertThrows(AccessDeniedException.class, () -> controller.models(request));
            for (String permission : List.of("create", "update", "manage")) {
                authenticate("system:ai-config:" + permission);
                assertDoesNotThrow(() -> controller.models(request));
            }
            verify(context.getBean(CodexModelCatalogService.class), times(3)).query(request);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void authenticate(String authority) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "tester", "", List.of(new SimpleGrantedAuthority(authority))));
    }

    @Configuration
    @EnableMethodSecurity
    static class SecurityConfig {
        @Bean CodexModelCatalogService service() { return mock(CodexModelCatalogService.class); }
        @Bean CodexModelCatalogController controller(CodexModelCatalogService service) {
            return new CodexModelCatalogController(service);
        }
    }
}
