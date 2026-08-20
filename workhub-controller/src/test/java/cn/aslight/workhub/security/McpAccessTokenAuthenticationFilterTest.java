package cn.aslight.workhub.security;

import cn.aslight.workhub.config.McpProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpAccessTokenAuthenticationFilterTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilter_shouldAuthenticateMatchingMcpToken() throws Exception {
        McpProperties properties = new McpProperties();
        properties.setAccessToken("test-mcp-token");
        McpAccessTokenAuthenticationFilter filter = new McpAccessTokenAuthenticationFilter(properties);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/mcp/runtime");
        request.addHeader("Authorization", "Bearer test-mcp-token");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertEquals("workhub-mcp", SecurityContextHolder.getContext().getAuthentication().getName());
        assertTrue(SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(authority -> "ROLE_MCP".equals(authority.getAuthority())));
    }

    @Test
    void doFilter_shouldNotAuthenticateWrongToken() throws Exception {
        McpProperties properties = new McpProperties();
        properties.setAccessToken("test-mcp-token");
        McpAccessTokenAuthenticationFilter filter = new McpAccessTokenAuthenticationFilter(properties);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/mcp/runtime");
        request.addHeader("Authorization", "Bearer wrong-token");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
