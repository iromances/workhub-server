package cn.aslight.workhub.service.system;

import cn.aslight.workhub.dao.system.SysPermissionMapper;
import cn.aslight.workhub.dao.system.SysRoleMapper;
import cn.aslight.workhub.model.system.SysPermissionEntity;
import cn.aslight.workhub.model.system.SysPermissionResponse;
import cn.aslight.workhub.model.system.SysRoleEntity;
import cn.aslight.workhub.model.system.SysRolePermissionRequest;
import cn.aslight.workhub.model.system.SysRoleResponse;
import cn.aslight.workhub.model.system.SysRoleSaveRequest;
import cn.aslight.workhub.model.system.SysRoleStatusRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统角色管理服务。
 */
@Service
public class RoleManagementService {

    private final SysRoleMapper roleMapper;
    private final SysPermissionMapper permissionMapper;
    private final SystemAuditService auditService;

    public RoleManagementService(SysRoleMapper roleMapper,
                                 SysPermissionMapper permissionMapper,
                                 SystemAuditService auditService) {
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.auditService = auditService;
    }

    public List<SysRoleResponse> list(String keyword, Boolean enabled) {
        return roleMapper.findAll(trim(keyword), enabled).stream().map(this::toResponse).toList();
    }

    @Transactional
    public SysRoleResponse create(SysRoleSaveRequest request, String operator, String ip) {
        String roleCode = trimRequired(request.getRoleCode(), "角色编码不能为空");
        if (roleMapper.findByCode(roleCode) != null) {
            throw new IllegalArgumentException("角色编码已存在");
        }
        SysRoleEntity entity = new SysRoleEntity();
        entity.setRoleCode(roleCode);
        entity.setRoleName(trimRequired(request.getRoleName(), "角色名称不能为空"));
        entity.setRoleType("CUSTOM");
        entity.setBuiltIn(false);
        entity.setEnabled(request.getEnabled() == null || request.getEnabled());
        entity.setRemark(trim(request.getRemark()));
        roleMapper.insert(entity);
        auditService.operation(operator, "system:role:manage", "CREATE", "SYS_ROLE", String.valueOf(entity.getId()),
                null, "创建角色 " + entity.getRoleCode(), "SUCCESS", null, ip);
        return toResponse(entity);
    }

    @Transactional
    public SysRoleResponse update(Long id, SysRoleSaveRequest request, String operator, String ip) {
        SysRoleEntity existing = requireRole(id);
        if (Boolean.TRUE.equals(existing.getBuiltIn()) && !existing.getRoleCode().equals(trimRequired(request.getRoleCode(), "角色编码不能为空"))) {
            throw new IllegalArgumentException("内置角色不能修改角色编码");
        }
        existing.setRoleCode(trimRequired(request.getRoleCode(), "角色编码不能为空"));
        existing.setRoleName(trimRequired(request.getRoleName(), "角色名称不能为空"));
        existing.setEnabled(request.getEnabled() == null || request.getEnabled());
        existing.setRemark(trim(request.getRemark()));
        roleMapper.update(existing);
        auditService.operation(operator, "system:role:manage", "UPDATE", "SYS_ROLE", String.valueOf(id),
                null, "更新角色 " + existing.getRoleCode(), "SUCCESS", null, ip);
        return toResponse(requireRole(id));
    }

    @Transactional
    public SysRoleResponse updateStatus(Long id, SysRoleStatusRequest request, String operator, String ip) {
        SysRoleEntity existing = requireRole(id);
        boolean enabled = request.getEnabled() == null || request.getEnabled();
        if ("SUPER_ADMIN".equals(existing.getRoleCode()) && !enabled) {
            throw new IllegalArgumentException("不能停用超级管理员角色");
        }
        roleMapper.updateStatus(id, enabled);
        auditService.operation(operator, "system:role:manage", "STATUS", "SYS_ROLE", String.valueOf(id),
                null, "更新角色状态 " + existing.getRoleCode(), "SUCCESS", null, ip);
        return toResponse(requireRole(id));
    }

    @Transactional
    public void delete(Long id, String operator, String ip) {
        SysRoleEntity existing = requireRole(id);
        if (Boolean.TRUE.equals(existing.getBuiltIn())) {
            throw new IllegalArgumentException("内置角色不能删除");
        }
        permissionMapper.deleteRolePermissions(id);
        roleMapper.delete(id);
        auditService.operation(operator, "system:role:manage", "DELETE", "SYS_ROLE", String.valueOf(id),
                null, "删除角色 " + existing.getRoleCode(), "SUCCESS", null, ip);
    }

    public List<SysPermissionResponse> permissions() {
        List<SysPermissionEntity> entities = permissionMapper.findEnabledPermissions();
        Map<String, MutablePermission> byCode = new LinkedHashMap<>();
        for (SysPermissionEntity entity : entities) {
            byCode.put(entity.getPermissionCode(), new MutablePermission(entity));
        }
        List<MutablePermission> roots = new ArrayList<>();
        for (MutablePermission permission : byCode.values()) {
            if (permission.entity.getParentCode() != null && byCode.containsKey(permission.entity.getParentCode())) {
                byCode.get(permission.entity.getParentCode()).children.add(permission);
            } else {
                roots.add(permission);
            }
        }
        return roots.stream()
                .sorted(Comparator.comparing(permission -> permission.entity.getSortOrder()))
                .map(MutablePermission::toResponse)
                .toList();
    }

    public List<String> rolePermissions(Long roleId) {
        requireRole(roleId);
        return permissionMapper.findPermissionCodesByRoleId(roleId);
    }

    @Transactional
    public List<String> updateRolePermissions(Long roleId, SysRolePermissionRequest request, String operator, String ip) {
        SysRoleEntity existing = requireRole(roleId);
        List<String> permissionCodes = request.getPermissionCodes().stream()
                .map(this::trim)
                .filter(value -> value != null)
                .distinct()
                .toList();
        permissionMapper.deleteRolePermissions(roleId);
        if (!permissionCodes.isEmpty()) {
            permissionMapper.insertRolePermissions(roleId, permissionCodes);
        }
        auditService.operation(operator, "system:role:manage", "ASSIGN_PERMISSION", "SYS_ROLE", String.valueOf(roleId),
                null, "更新角色权限 " + existing.getRoleCode(), "SUCCESS", null, ip);
        return permissionMapper.findPermissionCodesByRoleId(roleId);
    }

    private SysRoleEntity requireRole(Long id) {
        SysRoleEntity entity = roleMapper.findById(id);
        if (entity == null) {
            throw new IllegalArgumentException("角色不存在");
        }
        return entity;
    }

    private SysRoleResponse toResponse(SysRoleEntity entity) {
        return new SysRoleResponse(
                entity.getId(),
                entity.getRoleCode(),
                entity.getRoleName(),
                entity.getRoleType(),
                entity.getEnabled(),
                entity.getBuiltIn(),
                entity.getRemark(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
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

    private static class MutablePermission {
        private final SysPermissionEntity entity;
        private final List<MutablePermission> children = new ArrayList<>();

        private MutablePermission(SysPermissionEntity entity) {
            this.entity = entity;
        }

        private SysPermissionResponse toResponse() {
            return new SysPermissionResponse(
                    entity.getPermissionCode(),
                    entity.getPermissionName(),
                    entity.getPermissionType(),
                    entity.getParentCode(),
                    entity.getRoutePath(),
                    entity.getSortOrder(),
                    entity.getEnabled(),
                    children.stream()
                            .sorted(Comparator.comparing(child -> child.entity.getSortOrder()))
                            .map(MutablePermission::toResponse)
                            .toList()
            );
        }
    }
}
