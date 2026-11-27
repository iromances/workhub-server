package cn.aslight.workhub.service.auth;

import cn.aslight.workhub.model.auth.LoginRequest;
import cn.aslight.workhub.model.auth.LoginResponse;
import cn.aslight.workhub.model.auth.UpdateProfileRequest;
import cn.aslight.workhub.model.auth.UserProfileResponse;
import cn.aslight.workhub.model.auth.ChangePasswordRequest;
import cn.aslight.workhub.dao.system.SysUserMapper;
import cn.aslight.workhub.model.system.SysUserEntity;
import cn.aslight.workhub.security.JwtTokenService;
import cn.aslight.workhub.service.attachment.AttachmentService;
import cn.aslight.workhub.service.system.SystemAuditService;
import org.springframework.core.io.Resource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 认证服务。
 */
@Service
public class AuthService {

    private static final int MAX_LOGIN_FAILURES = 5;
    private static final int LOCK_MINUTES = 15;
    private static final long MAX_AVATAR_BYTES = 2 * 1024 * 1024;
    private static final Set<String> ALLOWED_AVATAR_EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "webp");
    /** 本系统上传的头像地址，后半段是头像附件 ID。 */
    private static final String AVATAR_URL_PREFIX = "/api/auth/avatars/";
    private static final Pattern AVATAR_ID_PATTERN = Pattern.compile("[1-9][0-9]{0,17}");

    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final SysUserMapper userMapper;
    private final PermissionContextService permissionContextService;
    private final SystemAuditService auditService;
    private final AttachmentService attachmentService;

    public AuthService(PasswordEncoder passwordEncoder,
                       JwtTokenService jwtTokenService,
                       SysUserMapper userMapper,
                       PermissionContextService permissionContextService,
                       SystemAuditService auditService,
                       AttachmentService attachmentService) {
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.userMapper = userMapper;
        this.permissionContextService = permissionContextService;
        this.auditService = auditService;
        this.attachmentService = attachmentService;
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
        return new LoginResponse(token, user.getUserName(), user.getDisplayName(), user.getAvatarUrl(), user.getMustChangePassword(), roles, permissions);
    }

    public UserProfileResponse currentUser(String username) {
        SysUserEntity user = permissionContextService.requireActiveUser(username);
        return new UserProfileResponse(
                user.getUserName(),
                user.getDisplayName(),
                user.getAvatarUrl(),
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

    public UserProfileResponse updateProfile(String username, UpdateProfileRequest request, String ip) {
        SysUserEntity user = permissionContextService.requireActiveUser(username);
        String displayName = trimRequired(request.getDisplayName(), "昵称不能为空");
        userMapper.updateProfile(user.getId(), displayName);
        auditService.operation(username, null, "UPDATE_PROFILE", "SYS_USER", String.valueOf(user.getId()),
                null, "修改本人昵称", "SUCCESS", null, ip);
        return currentUser(username);
    }

    /** 上传头像：内容存入附件表，头像地址指向新的头像附件，并清理上一张本系统上传的头像。 */
    @Transactional
    public UserProfileResponse uploadAvatar(String username, MultipartFile file, String ip) {
        SysUserEntity user = permissionContextService.requireActiveUser(username);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("头像文件不能为空");
        }
        if (file.getSize() > MAX_AVATAR_BYTES) {
            throw new IllegalArgumentException("头像文件不能超过 2MB");
        }
        validateAvatarFile(file.getOriginalFilename(), file.getContentType());

        String oldAvatarUrl = trimToNull(user.getAvatarUrl());
        Long avatarId = attachmentService.saveUserAvatar(user.getId(), file);
        userMapper.updateAvatar(user.getId(), AVATAR_URL_PREFIX + avatarId);
        deleteStoredAvatar(oldAvatarUrl);
        auditService.operation(username, null, "UPLOAD_AVATAR", "SYS_USER", String.valueOf(user.getId()),
                null, "上传个人头像", "SUCCESS", null, ip);
        return currentUser(username);
    }

    /** 按头像地址中的附件 ID 读取头像；ID 非法、附件不存在或不是头像时按头像不存在处理。 */
    public AvatarResource loadAvatar(String avatarKey) {
        Long avatarId = parseAvatarId(avatarKey);
        if (avatarId == null) {
            throw new IllegalArgumentException("头像文件不存在");
        }
        AttachmentService.AttachmentResource avatar = attachmentService.loadUserAvatar(avatarId);
        String contentType = avatar.entity().getContentType();
        return new AvatarResource(avatar.resource(), contentType == null ? "application/octet-stream" : contentType);
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

    /** 头像只接受图片，扩展名限 png、jpg、jpeg、gif、webp。 */
    private void validateAvatarFile(String fileName, String contentType) {
        String normalizedContentType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (!normalizedContentType.startsWith("image/")) {
            throw new IllegalArgumentException("只能上传图片头像");
        }
        String sanitized = sanitizeAvatarFileName(fileName);
        int index = sanitized.lastIndexOf('.');
        String extension = index < 0 ? "" : sanitized.substring(index + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_AVATAR_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("头像仅支持 png、jpg、jpeg、gif、webp 格式");
        }
    }

    private String sanitizeAvatarFileName(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null || trimmed.contains("/") || trimmed.contains("\\") || trimmed.contains("..")) {
            throw new IllegalArgumentException("头像文件名非法");
        }
        return trimmed;
    }

    /** 只清理本系统上传的头像；用户管理中手工填写的其他地址不动。 */
    private void deleteStoredAvatar(String avatarUrl) {
        if (avatarUrl == null || !avatarUrl.startsWith(AVATAR_URL_PREFIX)) {
            return;
        }
        Long avatarId = parseAvatarId(avatarUrl.substring(AVATAR_URL_PREFIX.length()));
        if (avatarId != null) {
            attachmentService.deleteUserAvatar(avatarId);
        }
    }

    /** 头像地址中的附件 ID；不是纯数字时返回 null。 */
    private Long parseAvatarId(String value) {
        String trimmed = trimToNull(value);
        return trimmed != null && AVATAR_ID_PATTERN.matcher(trimmed).matches() ? Long.valueOf(trimmed) : null;
    }

    private String trimRequired(String value, String message) {
        String trimmed = normalize(value);
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        String trimmed = normalize(value);
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record AvatarResource(Resource resource, String contentType) {
    }
}
