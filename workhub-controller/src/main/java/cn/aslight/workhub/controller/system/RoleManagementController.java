package cn.aslight.workhub.controller.system;

import cn.aslight.workhub.common.api.ApiResponse;
import cn.aslight.workhub.common.api.PageResponse;
import cn.aslight.workhub.model.system.SysPermissionResponse;
import cn.aslight.workhub.model.system.SysRolePermissionRequest;
import cn.aslight.workhub.model.system.SysRoleResponse;
import cn.aslight.workhub.model.system.SysRoleSaveRequest;
import cn.aslight.workhub.model.system.SysRoleStatusRequest;
import cn.aslight.workhub.service.system.RoleManagementService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 系统角色管理控制器。
 */
@RestController
@RequestMapping("/api/system")
public class RoleManagementController {

    private final RoleManagementService roleManagementService;

    public RoleManagementController(RoleManagementService roleManagementService) {
        this.roleManagementService = roleManagementService;
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('system:role:view') or hasAuthority('system:role:manage')")
    public ApiResponse<PageResponse<SysRoleResponse>> list(@RequestParam(required = false) String keyword,
                                                           @RequestParam(required = false) Boolean enabled) {
        List<SysRoleResponse> items = roleManagementService.list(keyword, enabled);
        return ApiResponse.success(new PageResponse<>(items.size(), items));
    }

    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('system:role:create') or hasAuthority('system:role:manage')")
    public ApiResponse<SysRoleResponse> create(@Valid @RequestBody SysRoleSaveRequest request,
                                               Authentication authentication,
                                               HttpServletRequest httpRequest) {
        return ApiResponse.success(roleManagementService.create(request, authentication.getName(), clientIp(httpRequest)));
    }

    @PutMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('system:role:update') or hasAuthority('system:role:manage')")
    public ApiResponse<SysRoleResponse> update(@PathVariable Long id,
                                               @Valid @RequestBody SysRoleSaveRequest request,
                                               Authentication authentication,
                                               HttpServletRequest httpRequest) {
        return ApiResponse.success(roleManagementService.update(id, request, authentication.getName(), clientIp(httpRequest)));
    }

    @PostMapping("/roles/{id}/status")
    @PreAuthorize("hasAuthority('system:role:status') or hasAuthority('system:role:manage')")
    public ApiResponse<SysRoleResponse> updateStatus(@PathVariable Long id,
                                                     @RequestBody SysRoleStatusRequest request,
                                                     Authentication authentication,
                                                     HttpServletRequest httpRequest) {
        return ApiResponse.success(roleManagementService.updateStatus(id, request, authentication.getName(), clientIp(httpRequest)));
    }

    @DeleteMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('system:role:delete') or hasAuthority('system:role:manage')")
    public ApiResponse<Void> delete(@PathVariable Long id,
                                    Authentication authentication,
                                    HttpServletRequest httpRequest) {
        roleManagementService.delete(id, authentication.getName(), clientIp(httpRequest));
        return ApiResponse.success();
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('system:permission:view') or hasAuthority('system:role:view') or hasAuthority('system:role:manage')")
    public ApiResponse<List<SysPermissionResponse>> permissions() {
        return ApiResponse.success(roleManagementService.permissions());
    }

    @GetMapping("/roles/{id}/permissions")
    @PreAuthorize("hasAuthority('system:role:view') or hasAuthority('system:role:manage')")
    public ApiResponse<List<String>> rolePermissions(@PathVariable Long id) {
        return ApiResponse.success(roleManagementService.rolePermissions(id));
    }

    @PutMapping("/roles/{id}/permissions")
    @PreAuthorize("hasAuthority('system:role:assign-permission') or hasAuthority('system:role:manage')")
    public ApiResponse<List<String>> updateRolePermissions(@PathVariable Long id,
                                                           @RequestBody SysRolePermissionRequest request,
                                                           Authentication authentication,
                                                           HttpServletRequest httpRequest) {
        return ApiResponse.success(roleManagementService.updateRolePermissions(id, request, authentication.getName(), clientIp(httpRequest)));
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
