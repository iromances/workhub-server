package cn.aslight.workhub.service.auth;

import cn.aslight.workhub.dao.system.SysPermissionMapper;
import cn.aslight.workhub.dao.system.SysRoleMapper;
import cn.aslight.workhub.dao.system.SysUserMapper;
import cn.aslight.workhub.model.system.SysRoleEntity;
import cn.aslight.workhub.model.system.SysUserEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 当前用户权限上下文服务。
 */
@Service
public class PermissionContextService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysPermissionMapper permissionMapper;

    public PermissionContextService(SysUserMapper userMapper,
                                    SysRoleMapper roleMapper,
                                    SysPermissionMapper permissionMapper) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
    }

    public SysUserEntity requireActiveUser(String userName) {
        SysUserEntity user = userMapper.findByUserName(userName);
        if (user == null || Boolean.TRUE.equals(user.getDeleted())) {
            throw new IllegalArgumentException("用户不存在");
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new IllegalArgumentException("账号已停用");
        }
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("账号已锁定");
        }
        return user;
    }

    public List<String> permissionCodes(Long userId) {
        return permissionMapper.findPermissionCodesByUserId(userId).stream().distinct().toList();
    }

    public List<String> roleCodes(Long userId) {
        return roleMapper.findByUserId(userId).stream()
                .filter(role -> Boolean.TRUE.equals(role.getEnabled()))
                .map(SysRoleEntity::getRoleCode)
                .distinct()
                .toList();
    }

    public List<GrantedAuthority> authorities(Long userId) {
        return permissionCodes(userId).stream()
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
    }
}
