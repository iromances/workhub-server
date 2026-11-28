package cn.aslight.workhub.controller.ops;

import cn.aslight.workhub.common.api.ApiResponse;
import cn.aslight.workhub.common.api.PageResponse;
import cn.aslight.workhub.model.ops.AccountingDashboardResponse;
import cn.aslight.workhub.model.ops.AccountingManualRunRequest;
import cn.aslight.workhub.model.ops.AccountingMonitorConfigEntity;
import cn.aslight.workhub.model.ops.AccountingMonitorSaveRequest;
import cn.aslight.workhub.model.ops.AccountingRuleModels;
import cn.aslight.workhub.model.ops.AccountingRunDetailResponse;
import cn.aslight.workhub.model.ops.AccountingRunEntity;
import cn.aslight.workhub.model.ops.AccountingScenarioTypeResponse;
import cn.aslight.workhub.service.ops.AccountingMonitorService;
import cn.aslight.workhub.service.ops.AccountingRuleAdminService;
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

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/ops/accounting")
public class AccountingMonitorController {

    private final AccountingMonitorService accountingMonitorService;
    private final AccountingRuleAdminService accountingRuleAdminService;

    public AccountingMonitorController(AccountingMonitorService accountingMonitorService,
                                       AccountingRuleAdminService accountingRuleAdminService) {
        this.accountingMonitorService = accountingMonitorService;
        this.accountingRuleAdminService = accountingRuleAdminService;
    }

    @GetMapping("/runs/{id}/sql-evidence")
    @PreAuthorize("hasAuthority('ops:accounting:view') or hasAuthority('ops:accounting:manage')")
    public ApiResponse<AccountingMonitorService.SqlEvidencePage> sqlEvidence(
            @PathVariable Long id, @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int pageSize,
            @RequestParam(required = false) String family) {
        return ApiResponse.success(accountingMonitorService.sqlEvidence(id, family, page, pageSize));
    }

    @GetMapping("/runs/{id}/agent-runs")
    @PreAuthorize("hasAuthority('ops:accounting:view') or hasAuthority('ops:accounting:manage')")
    public ApiResponse<List<String>> agentRuns(@PathVariable Long id) {
        return ApiResponse.success(accountingMonitorService.agentRuns(id));
    }

    @GetMapping("/configs/{id}/rules")
    @PreAuthorize("hasAuthority('ops:accounting:view') or hasAuthority('ops:accounting:manage')")
    public ApiResponse<List<java.util.Map<String,Object>>> rules(@PathVariable Long id) {
        return ApiResponse.success(accountingMonitorService.rules(id));
    }

    // ===== REQ-7f26a063：规则组与检查项管理 =====

    @GetMapping("/configs/{id}/sql-templates")
    @PreAuthorize("hasAuthority('ops:accounting:view') or hasAuthority('ops:accounting:manage')")
    public ApiResponse<List<AccountingRuleModels.SqlTemplateItem>> sqlTemplates(
            @PathVariable Long id, @RequestParam(required = false) String family) {
        return ApiResponse.success(accountingRuleAdminService.sqlTemplates(id, family));
    }

    @PutMapping("/configs/{id}/rules/{familyCode}/enabled")
    @PreAuthorize("hasAuthority('ops:accounting:manage')")
    public ApiResponse<Void> setFamilyEnabled(@PathVariable Long id, @PathVariable String familyCode,
                                              @Valid @RequestBody AccountingRuleModels.FamilyEnabledRequest request,
                                              Principal principal) {
        accountingRuleAdminService.setEnabled(id, familyCode, request,
                principal == null ? "unknown" : principal.getName());
        return ApiResponse.success(null);
    }

    @PutMapping("/configs/{id}/rules/{familyCode}/checks")
    @PreAuthorize("hasAuthority('ops:accounting:manage')")
    public ApiResponse<Void> saveChecks(@PathVariable Long id, @PathVariable String familyCode,
                                        @Valid @RequestBody AccountingRuleModels.ChecksSaveRequest request,
                                        Principal principal) {
        accountingRuleAdminService.saveChecks(id, familyCode, request,
                principal == null ? "unknown" : principal.getName());
        return ApiResponse.success(null);
    }

    @PostMapping("/configs/{id}/rules")
    @PreAuthorize("hasAuthority('ops:accounting:manage') and hasAuthority('ops:function-call:manage')")
    public ApiResponse<Void> createFamily(@PathVariable Long id,
                                          @Valid @RequestBody AccountingRuleModels.FamilyCreateRequest request,
                                          Principal principal) {
        accountingRuleAdminService.createFamily(id, request,
                principal == null ? "unknown" : principal.getName());
        return ApiResponse.success(null);
    }

    @PutMapping("/configs/{id}/rules/{familyCode}")
    @PreAuthorize("hasAuthority('ops:accounting:manage')")
    public ApiResponse<Void> editFamily(@PathVariable Long id, @PathVariable String familyCode,
                                        @Valid @RequestBody AccountingRuleModels.FamilyEditRequest request,
                                        Principal principal) {
        accountingRuleAdminService.editFamily(id, familyCode, request,
                principal == null ? "unknown" : principal.getName());
        return ApiResponse.success(null);
    }

    @DeleteMapping("/configs/{id}/rules/{familyCode}")
    @PreAuthorize("hasAuthority('ops:accounting:manage')")
    public ApiResponse<Void> deleteFamily(@PathVariable Long id, @PathVariable String familyCode,
                                          Principal principal) {
        accountingRuleAdminService.deleteFamily(id, familyCode,
                principal == null ? "unknown" : principal.getName());
        return ApiResponse.success(null);
    }

    @PutMapping("/configs/{id}/rules/{familyCode}/sql")
    @PreAuthorize("hasAuthority('ops:function-call:manage')")
    public ApiResponse<Void> saveFamilySql(@PathVariable Long id, @PathVariable String familyCode,
                                           @Valid @RequestBody AccountingRuleModels.FamilySqlSaveRequest request) {
        accountingRuleAdminService.saveFamilySql(id, familyCode, request);
        return ApiResponse.success(null);
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

    @GetMapping("/scenarios")
    @PreAuthorize("hasAuthority('ops:accounting:view') or hasAuthority('ops:accounting:manage')")
    public ApiResponse<List<AccountingScenarioTypeResponse>> scenarios() {
        return ApiResponse.success(accountingMonitorService.scenarioTypes());
    }

    @GetMapping("/runs")
    @PreAuthorize("hasAuthority('ops:accounting:view') or hasAuthority('ops:accounting:manage')")
    public ApiResponse<PageResponse<AccountingRunEntity>> runs(
            @RequestParam(required = false) String businessLineCode,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long configId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        List<AccountingRunEntity> items = accountingMonitorService.listRuns(businessLineCode, status, configId, page, pageSize);
        return ApiResponse.success(new PageResponse<>(
                accountingMonitorService.countRuns(businessLineCode, status, configId),
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
