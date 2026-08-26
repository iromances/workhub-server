package cn.aslight.workhub.security;

import cn.aslight.workhub.model.system.SysUserEntity;
import cn.aslight.workhub.service.auth.PermissionContextService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private final JwtTokenService jwtTokenService = mock(JwtTokenService.class);
    private final PermissionContextService permissionContextService = mock(PermissionContextService.class);
    private final JwtAuthenticationFilter filter =
            new JwtAuthenticationFilter(jwtTokenService, permissionContextService);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilter_shouldPreserveMcpAuthenticationForMcpRuntime() throws Exception {
        UsernamePasswordAuthenticationToken mcpAuthentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        "workhub-mcp",
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_MCP"))
                );
        SecurityContextHolder.getContext().setAuthentication(mcpAuthentication);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/mcp/runtime");
        request.addHeader("Authorization", "Bearer test-mcp-token");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertEquals("workhub-mcp", SecurityContextHolder.getContext().getAuthentication().getName());
        verify(jwtTokenService, never()).parseUsername("test-mcp-token");
    }

    @Test
    void doFilter_shouldAuthenticateJwtForOrdinaryRequest() throws Exception {
        SysUserEntity user = new SysUserEntity();
        user.setId(1L);
        user.setUserName("tester");
        when(jwtTokenService.parseUsername("test-jwt-token")).thenReturn("tester");
        when(permissionContextService.requireActiveUser("tester")).thenReturn(user);
        when(permissionContextService.authorities(1L))
                .thenReturn(List.of(new SimpleGrantedAuthority("system:read")));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/system/ping");
        request.addHeader("Authorization", "Bearer test-jwt-token");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertEquals("tester", SecurityContextHolder.getContext().getAuthentication().getName());
        verify(jwtTokenService).parseUsername("test-jwt-token");
    }
}
