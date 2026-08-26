package cn.aslight.workhub.security;

import cn.aslight.workhub.config.McpProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * 使用独立 Bearer Token 认证 WorkHub MCP 客户端。
 */
@Component
public class McpAccessTokenAuthenticationFilter extends OncePerRequestFilter {

    static final String MCP_RUNTIME_PATH = "/api/mcp/runtime";

    private final String configuredAccessToken;

    public McpAccessTokenAuthenticationFilter(
            McpProperties mcpProperties,
            @Value("${WORKHUB_MCP_ACCESS_TOKEN:}") String environmentAccessToken) {
        this.configuredAccessToken = StringUtils.hasText(environmentAccessToken)
                ? environmentAccessToken
                : mcpProperties.getAccessToken();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !MCP_RUNTIME_PATH.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(configuredAccessToken)
                && StringUtils.hasText(authorization)
                && authorization.startsWith("Bearer ")
                && tokenMatches(configuredAccessToken, authorization.substring(7))) {
            UsernamePasswordAuthenticationToken authentication =
                    UsernamePasswordAuthenticationToken.authenticated(
                            "workhub-mcp",
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_MCP"))
                    );
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        filterChain.doFilter(request, response);
    }

    private boolean tokenMatches(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }
}
