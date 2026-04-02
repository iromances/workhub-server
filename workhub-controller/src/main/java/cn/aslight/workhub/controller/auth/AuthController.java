package cn.aslight.workhub.controller.auth;

import cn.aslight.workhub.common.api.ApiResponse;
import cn.aslight.workhub.model.auth.LoginRequest;
import cn.aslight.workhub.model.auth.LoginResponse;
import cn.aslight.workhub.model.auth.UserProfileResponse;
import cn.aslight.workhub.service.auth.AuthService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口控制器。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 用户名密码登录并返回 JWT。
     *
     * @param request 登录请求
     * @return 登录结果
     */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    /**
     * 获取当前登录用户资料。
     *
     * @param authentication 当前认证信息
     * @return 当前用户资料
     */
    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> currentUser(Authentication authentication) {
        return ApiResponse.success(authService.currentUser(authentication.getName()));
    }
}
