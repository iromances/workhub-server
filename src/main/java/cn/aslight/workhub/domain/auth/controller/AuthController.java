package cn.aslight.workhub.domain.auth.controller;

import cn.aslight.workhub.common.api.ApiResponse;
import cn.aslight.workhub.domain.auth.dto.LoginRequest;
import cn.aslight.workhub.domain.auth.dto.LoginResponse;
import cn.aslight.workhub.domain.auth.dto.UserProfileResponse;
import cn.aslight.workhub.domain.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> currentUser(Authentication authentication) {
        return ApiResponse.success(authService.currentUser(authentication.getName()));
    }
}
