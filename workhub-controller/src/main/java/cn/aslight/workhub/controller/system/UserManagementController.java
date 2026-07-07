package cn.aslight.workhub.controller.system;

import cn.aslight.workhub.common.api.ApiResponse;
import cn.aslight.workhub.common.api.PageResponse;
import cn.aslight.workhub.model.system.SysPasswordResetResponse;
import cn.aslight.workhub.model.system.SysUserCreateResponse;
import cn.aslight.workhub.model.system.SysUserResponse;
import cn.aslight.workhub.model.system.SysUserRoleRequest;
import cn.aslight.workhub.model.system.SysUserSaveRequest;
import cn.aslight.workhub.model.system.SysUserStatusRequest;
import cn.aslight.workhub.service.system.UserManagementService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
 * 系统账号管理控制器。
 */
@RestController
@RequestMapping("/api/system/users")
public class UserManagementController {

    private final UserManagementService userManagementService;

    public UserManagementController(UserManagementService userManagementService) {
        this.userManagementService = userManagementService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('system:user:view') or hasAuthority('system:user:manage')")
    public ApiResponse<PageResponse<SysUserResponse>> list(@RequestParam(required = false) String keyword,
                                                           @RequestParam(required = false) String status) {
        List<SysUserResponse> items = userManagementService.list(keyword, status);
        return ApiResponse.success(new PageResponse<>(items.size(), items));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:user:create') or hasAuthority('system:user:manage')")
    public ApiResponse<SysUserCreateResponse> create(@Valid @RequestBody SysUserSaveRequest request,
                                                     Authentication authentication,
                                                     HttpServletRequest httpRequest) {
        return ApiResponse.success(userManagementService.create(request, authentication.getName(), clientIp(httpRequest)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user:update') or hasAuthority('system:user:manage')")
    public ApiResponse<SysUserResponse> update(@PathVariable Long id,
                                               @Valid @RequestBody SysUserSaveRequest request,
                                               Authentication authentication,
                                               HttpServletRequest httpRequest) {
        return ApiResponse.success(userManagementService.update(id, request, authentication.getName(), clientIp(httpRequest)));
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAuthority('system:user:status') or hasAuthority('system:user:manage')")
    public ApiResponse<SysUserResponse> updateStatus(@PathVariable Long id,
                                                     @Valid @RequestBody SysUserStatusRequest request,
                                                     Authentication authentication,
                                                     HttpServletRequest httpRequest) {
        return ApiResponse.success(userManagementService.updateStatus(id, request, authentication.getName(), clientIp(httpRequest)));
    }

    @PostMapping("/{id}/password/reset")
    @PreAuthorize("hasAuthority('system:user:reset-password') or hasAuthority('system:user:manage')")
    public ApiResponse<SysPasswordResetResponse> resetPassword(@PathVariable Long id,
                                                               Authentication authentication,
                                                               HttpServletRequest httpRequest) {
        return ApiResponse.success(userManagementService.resetPassword(id, authentication.getName(), clientIp(httpRequest)));
    }

    @GetMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('system:user:view') or hasAuthority('system:user:manage')")
    public ApiResponse<List<Long>> roles(@PathVariable Long id) {
        return ApiResponse.success(userManagementService.roleIds(id));
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('system:user:assign-role') or hasAuthority('system:user:manage')")
    public ApiResponse<List<Long>> updateRoles(@PathVariable Long id,
                                               @RequestBody SysUserRoleRequest request,
                                               Authentication authentication,
                                               HttpServletRequest httpRequest) {
        return ApiResponse.success(userManagementService.updateRoles(id, request, authentication.getName(), clientIp(httpRequest)));
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
