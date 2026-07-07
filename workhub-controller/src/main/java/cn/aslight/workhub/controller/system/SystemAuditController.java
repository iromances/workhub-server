package cn.aslight.workhub.controller.system;

import cn.aslight.workhub.common.api.ApiResponse;
import cn.aslight.workhub.common.api.PageResponse;
import cn.aslight.workhub.model.system.SysLoginLogResponse;
import cn.aslight.workhub.model.system.SysOperationLogResponse;
import cn.aslight.workhub.service.system.SystemAuditService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 系统审计控制器。
 */
@RestController
@RequestMapping("/api/system/audits")
public class SystemAuditController {

    private final SystemAuditService auditService;

    public SystemAuditController(SystemAuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/login")
    @PreAuthorize("hasAuthority('system:audit:view')")
    public ApiResponse<PageResponse<SysLoginLogResponse>> loginLogs(@RequestParam(required = false) String userName,
                                                                    @RequestParam(required = false) String loginResult,
                                                                    @RequestParam(defaultValue = "100") int limit) {
        List<SysLoginLogResponse> items = auditService.loginLogs(userName, loginResult, limit);
        return ApiResponse.success(new PageResponse<>(items.size(), items));
    }

    @GetMapping("/operations")
    @PreAuthorize("hasAuthority('system:audit:view')")
    public ApiResponse<PageResponse<SysOperationLogResponse>> operationLogs(@RequestParam(required = false) String operatorUserName,
                                                                           @RequestParam(required = false) String permissionCode,
                                                                           @RequestParam(required = false) String result,
                                                                           @RequestParam(defaultValue = "100") int limit) {
        List<SysOperationLogResponse> items = auditService.operationLogs(operatorUserName, permissionCode, result, limit);
        return ApiResponse.success(new PageResponse<>(items.size(), items));
    }
}
