package cn.aslight.workhub.security;

import cn.aslight.workhub.model.system.SysUserEntity;
import cn.aslight.workhub.service.auth.PermissionContextService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
@Component
/**
 * JWT 鉴权过滤器。
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenService jwtTokenService;
    private final PermissionContextService permissionContextService;

    public JwtAuthenticationFilter(JwtTokenService jwtTokenService,
                                   PermissionContextService permissionContextService) {
        this.jwtTokenService = jwtTokenService;
        this.permissionContextService = permissionContextService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(authorization) && authorization.startsWith("Bearer ")) {
            String token = authorization.substring(7);
            try {
                String username = jwtTokenService.parseUsername(token);
                SysUserEntity user = permissionContextService.requireActiveUser(username);
                UsernamePasswordAuthenticationToken authentication =
                        UsernamePasswordAuthenticationToken.authenticated(
                                user.getUserName(),
                                null,
                                permissionContextService.authorities(user.getId())
                        );
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
