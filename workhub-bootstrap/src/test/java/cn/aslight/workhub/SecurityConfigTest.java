package cn.aslight.workhub;

import cn.aslight.workhub.config.McpProperties;
import cn.aslight.workhub.config.RestAuthenticationHandlers;
import cn.aslight.workhub.config.SecurityConfig;
import cn.aslight.workhub.security.JwtAuthenticationFilter;
import cn.aslight.workhub.security.JwtTokenService;
import cn.aslight.workhub.security.McpAccessTokenAuthenticationFilter;
import cn.aslight.workhub.service.auth.PermissionContextService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class SecurityConfigTest {

    private final SecurityConfig securityConfig = new SecurityConfig();

    @Test
    void jwtAuthenticationFilterRegistration_shouldDisableServletRegistration() {
        JwtAuthenticationFilter filter = mock(JwtAuthenticationFilter.class);

        FilterRegistrationBean<JwtAuthenticationFilter> registration =
                securityConfig.jwtAuthenticationFilterRegistration(filter);

        assertFalse(registration.isEnabled());
        assertSame(filter, registration.getFilter());
    }

    @Test
    void mcpAccessTokenAuthenticationFilterRegistration_shouldDisableServletRegistration() {
        McpAccessTokenAuthenticationFilter filter = mock(McpAccessTokenAuthenticationFilter.class);

        FilterRegistrationBean<McpAccessTokenAuthenticationFilter> registration =
                securityConfig.mcpAccessTokenAuthenticationFilterRegistration(filter);

        assertFalse(registration.isEnabled());
        assertSame(filter, registration.getFilter());
    }

    @Test
    void securityFilterChain_shouldAuthenticateMcpRuntimeWithConfiguredToken() throws Exception {
        try (AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(TestSecurityConfiguration.class);
            context.refresh();

            FilterChainProxy filterChain =
                    context.getBean("springSecurityFilterChain", FilterChainProxy.class);
            MockHttpServletRequest request =
                    new MockHttpServletRequest("POST", "/api/mcp/runtime");
            request.setServletPath("/api/mcp/runtime");
            request.addHeader("Authorization", "Bearer test-mcp-token");
            MockHttpServletResponse response = new MockHttpServletResponse();
            AtomicReference<Authentication> authentication = new AtomicReference<>();

            filterChain.doFilter(request, response,
                    (servletRequest, servletResponse) ->
                            authentication.set(SecurityContextHolder.getContext().getAuthentication()));

            assertEquals(200, response.getStatus());
            assertEquals("workhub-mcp", authentication.get().getName());
            assertTrue(authentication.get().getAuthorities().stream()
                    .anyMatch(authority -> "ROLE_MCP".equals(authority.getAuthority())));
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebSecurity
    @Import(SecurityConfig.class)
    static class TestSecurityConfiguration {

        @Bean
        McpProperties mcpProperties() {
            McpProperties properties = new McpProperties();
            properties.setAccessToken("test-mcp-token");
            return properties;
        }

        @Bean
        JwtTokenService jwtTokenService() {
            return mock(JwtTokenService.class);
        }

        @Bean
        PermissionContextService permissionContextService() {
            return mock(PermissionContextService.class);
        }

        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter(JwtTokenService jwtTokenService,
                                                        PermissionContextService permissionContextService) {
            return new JwtAuthenticationFilter(jwtTokenService, permissionContextService);
        }

        @Bean
        McpAccessTokenAuthenticationFilter mcpAccessTokenAuthenticationFilter(McpProperties mcpProperties) {
            return new McpAccessTokenAuthenticationFilter(mcpProperties, "test-mcp-token");
        }

        @Bean
        RestAuthenticationHandlers restAuthenticationHandlers() {
            return new RestAuthenticationHandlers(new ObjectMapper());
        }

        @Bean
        CorsConfigurationSource corsConfigurationSource() {
            return new UrlBasedCorsConfigurationSource();
        }
    }
}
