package cn.aslight.workhub.controller.ops;

import cn.aslight.workhub.common.api.ApiResponse;
import cn.aslight.workhub.common.api.PageResponse;
import cn.aslight.workhub.model.ops.AccountingDashboardResponse;
import cn.aslight.workhub.model.ops.AccountingManualRunRequest;
import cn.aslight.workhub.model.ops.AccountingMonitorConfigEntity;
import cn.aslight.workhub.model.ops.AccountingMonitorSaveRequest;
import cn.aslight.workhub.model.ops.AccountingRuleResponse;
import cn.aslight.workhub.model.ops.AccountingRunDetailResponse;
import cn.aslight.workhub.model.ops.AccountingRunEntity;
import cn.aslight.workhub.service.ops.AccountingMonitorService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/ops/accounting")
public class AccountingMonitorController {

    private final AccountingMonitorService accountingMonitorService;

    public AccountingMonitorController(AccountingMonitorService accountingMonitorService) {
        this.accountingMonitorService = accountingMonitorService;
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('ops:accounting:view') or hasAuthority('ops:accounting:manage')")
    public ApiResponse<AccountingDashboardResponse> dashboard(
            @RequestParam(required = false) String businessLineCode) {
        return ApiResponse.success(accountingMonitorService.dashboard(businessLineCode));
    }

    @GetMapping("/configs")
    @PreAuthorize("hasAuthority('ops:accounting:view') or hasAuthority('ops:accounting:manage')")
    public ApiResponse<PageResponse<AccountingMonitorConfigEntity>> configs(
            @RequestParam(required = false) String businessLineCode,
            @RequestParam(defaultValue = "false") boolean enabledOnly) {
        List<AccountingMonitorConfigEntity> items =
                accountingMonitorService.listConfigs(businessLineCode, enabledOnly);
        return ApiResponse.success(new PageResponse<>(items.size(), items));
    }

    @PostMapping("/configs")
    @PreAuthorize("hasAuthority('ops:accounting:manage')")
    public ApiResponse<AccountingMonitorConfigEntity> createConfig(
            @Valid @RequestBody AccountingMonitorSaveRequest request) {
        return ApiResponse.success(accountingMonitorService.createConfig(request));
    }

    @PutMapping("/configs/{id}")
    @PreAuthorize("hasAuthority('ops:accounting:manage')")
    public ApiResponse<AccountingMonitorConfigEntity> updateConfig(
            @PathVariable Long id,
            @Valid @RequestBody AccountingMonitorSaveRequest request) {
        return ApiResponse.success(accountingMonitorService.updateConfig(id, request));
    }

    @GetMapping("/rules")
    @PreAuthorize("hasAuthority('ops:accounting:view') or hasAuthority('ops:accounting:manage')")
    public ApiResponse<List<AccountingRuleResponse>> rules(@RequestParam String ruleProfile) {
        return ApiResponse.success(accountingMonitorService.listRules(ruleProfile));
    }

    @GetMapping("/runs")
    @PreAuthorize("hasAuthority('ops:accounting:view') or hasAuthority('ops:accounting:manage')")
    public ApiResponse<PageResponse<AccountingRunEntity>> runs(
            @RequestParam(required = false) String businessLineCode,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        List<AccountingRunEntity> items =
                accountingMonitorService.listRuns(businessLineCode, status, page, pageSize);
        return ApiResponse.success(new PageResponse<>(
                accountingMonitorService.countRuns(businessLineCode, status),
                items
        ));
    }

    @GetMapping("/runs/{id}")
    @PreAuthorize("hasAuthority('ops:accounting:view') or hasAuthority('ops:accounting:manage')")
    public ApiResponse<AccountingRunDetailResponse> runDetail(@PathVariable Long id) {
        return ApiResponse.success(accountingMonitorService.detail(id));
    }

    @PostMapping("/runs/manual")
    @PreAuthorize("hasAuthority('ops:accounting:run') or hasAuthority('ops:accounting:manage')")
    public ApiResponse<AccountingRunEntity> manual(
            @Valid @RequestBody AccountingManualRunRequest request,
            Principal principal) {
        return ApiResponse.success(accountingMonitorService.submitManual(
                request,
                principal == null ? "unknown" : principal.getName()
        ));
    }

    @PostMapping("/runs/{id}/retry")
    @PreAuthorize("hasAuthority('ops:accounting:run') or hasAuthority('ops:accounting:manage')")
    public ApiResponse<AccountingRunEntity> retry(@PathVariable Long id, Principal principal) {
        return ApiResponse.success(accountingMonitorService.retry(
                id,
                principal == null ? "unknown" : principal.getName()
        ));
    }
}
