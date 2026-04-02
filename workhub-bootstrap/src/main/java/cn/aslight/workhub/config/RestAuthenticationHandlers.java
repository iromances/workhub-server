package cn.aslight.workhub.config;

import cn.aslight.workhub.common.api.ApiResponse;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * REST 鉴权失败处理器。
 */
@Component
public class RestAuthenticationHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public RestAuthenticationHandlers(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        writeResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "登录已失效，请重新登录");
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        writeResponse(response, HttpServletResponse.SC_FORBIDDEN, "当前账号无权访问该资源");
    }

    private void writeResponse(HttpServletResponse response,
                               int status,
                               String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.failure(message)));
    }
}
