package cn.aslight.workhub.controller.intake;

import cn.aslight.workhub.common.exception.GlobalExceptionHandler;
import cn.aslight.workhub.model.intake.IntakeProcessInfoResponse;
import cn.aslight.workhub.model.intake.IntakeProcessInfoUpdateRequest;
import cn.aslight.workhub.service.intake.DevelopmentAnalysisService;
import cn.aslight.workhub.service.intake.IntakeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class IntakeProcessInfoControllerTest {
    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void exposesReadWriteContractAndPreservesExplicitNull() throws Exception {
        IntakeService service = mock(IntakeService.class);
        Map<String, String> values = new LinkedHashMap<>();
        values.put("actualEffort", null);
        var info = new IntakeProcessInfoResponse(values, true);
        when(service.processInfo(19L)).thenReturn(info);
        when(service.updateProcessInfo(eq(19L), any(), eq("editor"))).thenReturn(info);
        var mvc = MockMvcBuilders.standaloneSetup(new IntakeController(service, mock(DevelopmentAnalysisService.class)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/api/intake/19/process-info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.estimatedEffortOverridden").value(true));
        mvc.perform(post("/api/intake-records/19/process-info")
                        .principal(new TestingAuthenticationToken("editor", "pw"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"original":{"values":{"actualEffort":"8h"},"estimatedEffortOverridden":false},
                                 "changes":{"actualEffort":null}}
                                """))
                .andExpect(status().isOk());
        var captured = org.mockito.ArgumentCaptor.forClass(IntakeProcessInfoUpdateRequest.class);
        verify(service).updateProcessInfo(eq(19L), captured.capture(), eq("editor"));
        assertTrue(captured.getValue().changes().containsKey("actualEffort"));
        assertNull(captured.getValue().changes().get("actualEffort"));
        when(service.updateProcessInfo(eq(19L), any(), any())).thenThrow(new IllegalArgumentException("过程信息已更新，请重新打开后修改"));
        mvc.perform(post("/api/intake/19/process-info").principal(new TestingAuthenticationToken("editor", "pw"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"original\":null,\"changes\":{}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.message").value("过程信息已更新，请重新打开后修改"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"intake:metadata:update", "intake:record:update", "intake:record:view", "intake:ai:operate"})
    void enforcesMethodSecurityForBothEndpoints(String permission) {
        try (var context = new AnnotationConfigApplicationContext(SecurityConfig.class)) {
            var authentication = new TestingAuthenticationToken("editor", "pw", permission);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            var controller = context.getBean(IntakeController.class);
            var service = context.getBean(IntakeService.class);
            var request = new IntakeProcessInfoUpdateRequest(new IntakeProcessInfoResponse(Map.of(), false), Map.of());
            if (permission.endsWith(":update")) {
                controller.processInfo(19L);
                controller.updateProcessInfo(19L, request, authentication);
                verify(service).processInfo(19L);
                verify(service).updateProcessInfo(19L, request, "editor");
            } else {
                assertThrows(AccessDeniedException.class, () -> controller.processInfo(19L));
                assertThrows(AccessDeniedException.class, () -> controller.updateProcessInfo(19L, request, authentication));
                verifyNoInteractions(service);
            }
        }
    }

    @Configuration
    @EnableMethodSecurity
    static class SecurityConfig {
        @Bean
        IntakeService service() { return mock(IntakeService.class); }

        @Bean
        IntakeController controller(IntakeService service) {
            return new IntakeController(service, mock(DevelopmentAnalysisService.class));
        }
    }
}
