package cn.aslight.workhub.controller.system;

import cn.aslight.workhub.common.api.ApiResponse;
import cn.aslight.workhub.common.api.PageResponse;
import cn.aslight.workhub.model.system.SysConfigResponse;
import cn.aslight.workhub.model.system.SysConfigSaveRequest;
import cn.aslight.workhub.service.system.SysConfigService;
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
 * 系统配置接口控制器。
 */
@RestController
@RequestMapping("/api/system/configs")
public class SysConfigController {

    private final SysConfigService sysConfigService;

    public SysConfigController(SysConfigService sysConfigService) {
        this.sysConfigService = sysConfigService;
    }

    /**
     * 查询系统配置列表。
     *
     * @param configGroup 配置分组，可为空
     * @param keyword     关键字，可匹配配置键和配置名称
     * @return 系统配置列表
     */
    @GetMapping
    @PreAuthorize("hasAuthority('system:config:view') or hasAuthority('system:config:manage')")
    public ApiResponse<PageResponse<SysConfigResponse>> list(@RequestParam(required = false) String configGroup,
                                                            @RequestParam(required = false) String keyword) {
        List<SysConfigResponse> items = sysConfigService.list(configGroup, keyword);
        return ApiResponse.success(new PageResponse<>(items.size(), items));
    }

    /**
     * 新增系统配置。
     *
     * @param request 系统配置保存请求
     * @return 新增后的配置
     */
    @PostMapping
    @PreAuthorize("hasAuthority('system:config:create') or hasAuthority('system:config:manage')")
    public ApiResponse<SysConfigResponse> create(@Valid @RequestBody SysConfigSaveRequest request,
                                                 Authentication authentication,
                                                 HttpServletRequest httpRequest) {
        return ApiResponse.success(sysConfigService.create(
                request,
                authentication == null ? null : authentication.getName(),
                clientIp(httpRequest)
        ));
    }

    /**
     * 更新系统配置。
     *
     * @param id      配置 ID
     * @param request 系统配置保存请求
     * @return 更新后的配置
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:config:update') or hasAuthority('system:config:manage')")
    public ApiResponse<SysConfigResponse> update(@PathVariable Long id,
                                                 @Valid @RequestBody SysConfigSaveRequest request,
                                                 Authentication authentication,
                                                 HttpServletRequest httpRequest) {
        return ApiResponse.success(sysConfigService.update(
                id,
                request,
                authentication == null ? null : authentication.getName(),
                clientIp(httpRequest)
        ));
    }

    private String clientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
