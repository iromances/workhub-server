package cn.aslight.workhub.service.system;

import cn.aslight.workhub.dao.system.SysRoleMapper;
import cn.aslight.workhub.dao.system.SysUserMapper;
import cn.aslight.workhub.dao.system.SysUserRoleMapper;
import cn.aslight.workhub.model.system.SysPasswordResetResponse;
import cn.aslight.workhub.model.system.SysRoleEntity;
import cn.aslight.workhub.model.system.SysUserCreateResponse;
import cn.aslight.workhub.model.system.SysUserEntity;
import cn.aslight.workhub.model.system.SysUserResponse;
import cn.aslight.workhub.model.system.SysUserRoleRequest;
import cn.aslight.workhub.model.system.SysUserSaveRequest;
import cn.aslight.workhub.model.system.SysUserStatusRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 系统用户管理服务。
 */
@Service
public class UserManagementService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final PasswordGenerator passwordGenerator;
    private final SystemAuditService auditService;

    public UserManagementService(SysUserMapper userMapper,
                                 SysRoleMapper roleMapper,
                                 SysUserRoleMapper userRoleMapper,
                                 PasswordEncoder passwordEncoder,
                                 PasswordGenerator passwordGenerator,
                                 SystemAuditService auditService) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordEncoder = passwordEncoder;
        this.passwordGenerator = passwordGenerator;
        this.auditService = auditService;
    }

    public List<SysUserResponse> list(String keyword, String status) {
        return userMapper.findAll(trim(keyword), trim(status)).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public SysUserCreateResponse create(SysUserSaveRequest request, String operator, String ip) {
        String userName = trimRequired(request.getUserName(), "用户名不能为空");
        if (userMapper.findByUserName(userName) != null) {
            throw new IllegalArgumentException("用户名已存在");
        }
        String oneTimePassword = passwordGenerator.generate();
        SysUserEntity entity = new SysUserEntity();
        entity.setUserName(userName);
        entity.setDisplayName(trimRequired(request.getDisplayName(), "展示名不能为空"));
        entity.setEmail(trim(request.getEmail()));
        entity.setMobile(trim(request.getMobile()));
        entity.setWecomUserid(trim(request.getWecomUserid()));
        entity.setAvatarUrl(trim(request.getAvatarUrl()));
        entity.setPasswordHash(passwordEncoder.encode(oneTimePassword));
        entity.setStatus(normalizeStatus(request.getStatus()));
        entity.setMustChangePassword(true);
        userMapper.insert(entity);
        auditService.operation(operator, "system:user:manage", "CREATE", "SYS_USER", String.valueOf(entity.getId()),
                null, "创建账号 " + entity.getUserName(), "SUCCESS", null, ip);
        return new SysUserCreateResponse(toResponse(entity), oneTimePassword);
    }

    @Transactional
    public SysUserResponse update(Long id, SysUserSaveRequest request, String operator, String ip) {
        SysUserEntity existing = requireUser(id);
        existing.setDisplayName(trimRequired(request.getDisplayName(), "展示名不能为空"));
        existing.setEmail(trim(request.getEmail()));
        existing.setMobile(trim(request.getMobile()));
        existing.setWecomUserid(trim(request.getWecomUserid()));
        existing.setAvatarUrl(trim(request.getAvatarUrl()));
        existing.setStatus(normalizeStatus(request.getStatus()));
        userMapper.update(existing);
        auditService.operation(operator, "system:user:manage", "UPDATE", "SYS_USER", String.valueOf(id),
                null, "更新账号 " + existing.getUserName(), "SUCCESS", null, ip);
        return toResponse(requireUser(id));
    }

    @Transactional
    public SysUserResponse updateStatus(Long id, SysUserStatusRequest request, String operator, String ip) {
        SysUserEntity existing = requireUser(id);
        if ("admin".equals(existing.getUserName()) && !"ACTIVE".equals(normalizeStatus(request.getStatus()))) {
            throw new IllegalArgumentException("不能停用内置管理员账号");
        }
        if (!"ACTIVE".equals(normalizeStatus(request.getStatus()))
                && userRoleMapper.countOtherActiveSuperAdmins(id) == 0
                && roleMapper.findByUserId(id).stream().anyMatch(role -> "SUPER_ADMIN".equals(role.getRoleCode()))) {
            throw new IllegalArgumentException("不能停用最后一个超级管理员");
        }
        userMapper.updateStatus(id, normalizeStatus(request.getStatus()));
        auditService.operation(operator, "system:user:manage", "STATUS", "SYS_USER", String.valueOf(id),
                null, "更新账号状态 " + existing.getUserName(), "SUCCESS", null, ip);
        return toResponse(requireUser(id));
    }

    @Transactional
    public SysPasswordResetResponse resetPassword(Long id, String operator, String ip) {
        SysUserEntity existing = requireUser(id);
        String oneTimePassword = passwordGenerator.generate();
        userMapper.updatePassword(id, passwordEncoder.encode(oneTimePassword), true);
        auditService.operation(operator, "system:user:manage", "RESET_PASSWORD", "SYS_USER", String.valueOf(id),
                null, "重置账号密码 " + existing.getUserName(), "SUCCESS", null, ip);
        return new SysPasswordResetResponse(oneTimePassword);
    }

    public List<Long> roleIds(Long id) {
        requireUser(id);
        return userRoleMapper.findRoleIdsByUserId(id);
    }

    @Transactional
    public List<Long> updateRoles(Long id, SysUserRoleRequest request, String operator, String ip) {
        SysUserEntity existing = requireUser(id);
        List<Long> roleIds = request.getRoleIds().stream().distinct().toList();
        boolean removingLastSuperAdmin = roleMapper.findByUserId(id).stream().anyMatch(role -> "SUPER_ADMIN".equals(role.getRoleCode()))
                && roleIds.stream().map(roleMapper::findById).noneMatch(role -> role != null && "SUPER_ADMIN".equals(role.getRoleCode()))
                && userRoleMapper.countOtherActiveSuperAdmins(id) == 0;
        if (removingLastSuperAdmin) {
            throw new IllegalArgumentException("不能移除最后一个超级管理员");
        }
        userRoleMapper.deleteByUserId(id);
        if (!roleIds.isEmpty()) {
            userRoleMapper.insertUserRoles(id, roleIds);
        }
        auditService.operation(operator, "system:user:manage", "ASSIGN_ROLE", "SYS_USER", String.valueOf(id),
                null, "更新账号角色 " + existing.getUserName(), "SUCCESS", null, ip);
        return userRoleMapper.findRoleIdsByUserId(id);
    }

    private SysUserEntity requireUser(Long id) {
        SysUserEntity entity = userMapper.findById(id);
        if (entity == null) {
            throw new IllegalArgumentException("账号不存在");
        }
        return entity;
    }

    private SysUserResponse toResponse(SysUserEntity entity) {
        List<String> roleCodes = entity.getId() == null ? List.of() : roleMapper.findByUserId(entity.getId()).stream()
                .map(SysRoleEntity::getRoleCode)
                .toList();
        return new SysUserResponse(
                entity.getId(),
                entity.getUserName(),
                entity.getDisplayName(),
                entity.getEmail(),
                entity.getMobile(),
                entity.getWecomUserid(),
                entity.getAvatarUrl(),
                entity.getStatus(),
                entity.getMustChangePassword(),
                entity.getLoginFailCount(),
                entity.getLockedUntil(),
                entity.getLastLoginAt(),
                entity.getLastLoginIp(),
                roleCodes,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private String normalizeStatus(String status) {
        String value = trim(status);
        return value == null ? "ACTIVE" : value;
    }

    private String trimRequired(String value, String message) {
        String trimmed = trim(value);
        if (trimmed == null) {
            throw new IllegalArgumentException(message);
        }
        return trimmed;
    }

    private String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
