package cn.aslight.workhub.controller.ops;

import cn.aslight.workhub.common.api.ApiResponse;
import cn.aslight.workhub.common.api.PageResponse;
import cn.aslight.workhub.model.ops.SystemAlertDashboardResponse;
import cn.aslight.workhub.model.ops.SystemAlertSubsystemResponse;
import cn.aslight.workhub.model.ops.SystemAlertSubsystemSaveRequest;
import cn.aslight.workhub.service.ops.SystemAlertService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/ops/system-alerts")
public class SystemAlertController {

    private final SystemAlertService systemAlertService;

    public SystemAlertController(SystemAlertService systemAlertService) {
        this.systemAlertService = systemAlertService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ops:system-alert:view') or hasAuthority('ops:system-alert:manage')")
    public ApiResponse<SystemAlertDashboardResponse> dashboard(@RequestParam(required = false) String businessLineCode,
                                                               @RequestParam(required = false) String environmentCode,
                                                               @RequestParam(required = false) String serviceName,
                                                               @RequestParam(required = false) String level,
                                                               @RequestParam(required = false) LocalDateTime startTime,
                                                               @RequestParam(required = false) LocalDateTime endTime,
                                                               @RequestParam(defaultValue = "1") int page,
                                                               @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(systemAlertService.dashboard(businessLineCode, environmentCode, serviceName, level, startTime, endTime, page, pageSize));
    }

    @GetMapping("/subsystems")
    @PreAuthorize("hasAuthority('ops:system-alert:view') or hasAuthority('ops:system-alert:manage')")
    public ApiResponse<PageResponse<SystemAlertSubsystemResponse>> listSubsystems(@RequestParam(required = false) String businessLineCode,
                                                                                  @RequestParam(required = false) String environmentCode,
                                                                                  @RequestParam(defaultValue = "false") boolean enabledOnly,
                                                                                  @RequestParam(required = false) String keyword) {
        List<SystemAlertSubsystemResponse> items = systemAlertService.listSubsystems(businessLineCode, environmentCode, enabledOnly, keyword);
        return ApiResponse.success(new PageResponse<>(items.size(), items));
    }

    @PostMapping("/subsystems")
    @PreAuthorize("hasAuthority('ops:system-alert:create') or hasAuthority('ops:system-alert:manage')")
    public ApiResponse<SystemAlertSubsystemResponse> createSubsystem(@Valid @RequestBody SystemAlertSubsystemSaveRequest request) {
        return ApiResponse.success(systemAlertService.createSubsystem(request));
    }

    @PutMapping("/subsystems/{id}")
    @PreAuthorize("hasAuthority('ops:system-alert:update') or hasAuthority('ops:system-alert:manage')")
    public ApiResponse<SystemAlertSubsystemResponse> updateSubsystem(@PathVariable Long id,
                                                                     @Valid @RequestBody SystemAlertSubsystemSaveRequest request) {
        return ApiResponse.success(systemAlertService.updateSubsystem(id, request));
    }

    @DeleteMapping("/subsystems/{id}")
    @PreAuthorize("hasAuthority('ops:system-alert:delete') or hasAuthority('ops:system-alert:manage')")
    public ApiResponse<Void> deleteSubsystem(@PathVariable Long id) {
        systemAlertService.deleteSubsystem(id);
        return ApiResponse.success(null);
    }
}
