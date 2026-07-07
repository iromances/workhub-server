package cn.aslight.workhub.service.auth;

import cn.aslight.workhub.model.auth.LoginRequest;
import cn.aslight.workhub.model.auth.LoginResponse;
import cn.aslight.workhub.model.auth.UserProfileResponse;
import cn.aslight.workhub.model.auth.ChangePasswordRequest;
import cn.aslight.workhub.dao.system.SysUserMapper;
import cn.aslight.workhub.model.system.SysUserEntity;
import cn.aslight.workhub.security.JwtTokenService;
import cn.aslight.workhub.service.system.SystemAuditService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 认证服务。
 */
@Service
public class AuthService {

    private static final int MAX_LOGIN_FAILURES = 5;
    private static final int LOCK_MINUTES = 15;

    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final SysUserMapper userMapper;
    private final PermissionContextService permissionContextService;
    private final SystemAuditService auditService;

    public AuthService(PasswordEncoder passwordEncoder,
                       JwtTokenService jwtTokenService,
                       SysUserMapper userMapper,
                       PermissionContextService permissionContextService,
                       SystemAuditService auditService) {
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.userMapper = userMapper;
        this.permissionContextService = permissionContextService;
        this.auditService = auditService;
    }

    public LoginResponse login(LoginRequest request, String ip, String userAgent) {
        String userName = normalize(request.getUsername());
        SysUserEntity user = userMapper.findByUserName(userName);
        if (user == null) {
            auditService.login(userName, "FAILED", "用户不存在", ip, userAgent);
            throw new IllegalArgumentException("用户名或密码错误");
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            auditService.login(userName, "FAILED", "账号已停用", ip, userAgent);
            throw new IllegalArgumentException("账号已停用");
        }
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            auditService.login(userName, "FAILED", "账号已锁定", ip, userAgent);
            throw new IllegalArgumentException("账号已锁定，请稍后再试");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            handleLoginFailure(user, ip, userAgent);
            throw new IllegalArgumentException("用户名或密码错误");
        }
        userMapper.markLoginSuccess(user.getId(), ip);
        auditService.login(userName, "SUCCESS", null, ip, userAgent);
        List<String> roles = permissionContextService.roleCodes(user.getId());
        List<String> permissions = permissionContextService.permissionCodes(user.getId());
        String token = jwtTokenService.generateToken(user.getUserName());
        return new LoginResponse(token, user.getUserName(), user.getDisplayName(), user.getMustChangePassword(), roles, permissions);
    }

    public UserProfileResponse currentUser(String username) {
        SysUserEntity user = permissionContextService.requireActiveUser(username);
        return new UserProfileResponse(
                user.getUserName(),
                user.getDisplayName(),
                user.getMustChangePassword(),
                permissionContextService.roleCodes(user.getId()),
                permissionContextService.permissionCodes(user.getId())
        );
    }

    public void changePassword(String username, ChangePasswordRequest request, String ip) {
        SysUserEntity user = permissionContextService.requireActiveUser(username);
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("旧密码错误");
        }
        if (request.getNewPassword().length() < 8) {
            throw new IllegalArgumentException("新密码至少 8 位");
        }
        userMapper.updatePassword(user.getId(), passwordEncoder.encode(request.getNewPassword()), false);
        auditService.operation(username, null, "CHANGE_PASSWORD", "SYS_USER", String.valueOf(user.getId()),
                null, "修改本人密码", "SUCCESS", null, ip);
    }

    private void handleLoginFailure(SysUserEntity user, String ip, String userAgent) {
        int failures = user.getLoginFailCount() == null ? 1 : user.getLoginFailCount() + 1;
        LocalDateTime lockedUntil = failures >= MAX_LOGIN_FAILURES ? LocalDateTime.now().plusMinutes(LOCK_MINUTES) : null;
        userMapper.updateLoginFailure(user.getId(), failures, lockedUntil);
        auditService.login(user.getUserName(), "FAILED", lockedUntil == null ? "密码错误" : "密码错误次数过多，账号锁定", ip, userAgent);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
