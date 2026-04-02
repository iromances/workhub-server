package cn.aslight.workhub.service.auth;

import cn.aslight.workhub.model.auth.LoginRequest;
import cn.aslight.workhub.model.auth.LoginResponse;
import cn.aslight.workhub.model.auth.UserProfileResponse;
import cn.aslight.workhub.model.auth.LocalUser;
import cn.aslight.workhub.security.JwtTokenService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 认证服务。
 */
@Service
public class AuthService {

    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final LocalUser defaultUser;

    public AuthService(PasswordEncoder passwordEncoder, JwtTokenService jwtTokenService) {
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.defaultUser = new LocalUser(
                "admin",
                "WorkHub 管理员",
                passwordEncoder.encode("admin123")
        );
    }

    public LoginResponse login(LoginRequest request) {
        if (!defaultUser.userName().equals(request.getUsername())
                || !passwordEncoder.matches(request.getPassword(), defaultUser.encodedPassword())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        String token = jwtTokenService.generateToken(defaultUser.userName());
        return new LoginResponse(token, defaultUser.displayName());
    }

    public UserProfileResponse currentUser(String username) {
        if (!defaultUser.userName().equals(username)) {
            throw new IllegalArgumentException("用户不存在");
        }
        return new UserProfileResponse(defaultUser.userName(), defaultUser.displayName());
    }
}
