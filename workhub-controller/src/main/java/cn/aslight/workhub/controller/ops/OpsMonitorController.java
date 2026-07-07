package cn.aslight.workhub.controller.ops;

import cn.aslight.workhub.common.api.ApiResponse;
import cn.aslight.workhub.common.api.PageResponse;
import cn.aslight.workhub.model.ops.OpsMonitorCheckResponse;
import cn.aslight.workhub.model.ops.OpsMonitorResponse;
import cn.aslight.workhub.model.ops.OpsMonitorSaveRequest;
import cn.aslight.workhub.model.ops.XxlJobDashboardResponse;
import cn.aslight.workhub.model.ops.XxlJobExecutorResponse;
import cn.aslight.workhub.model.ops.XxlJobLogPageResponse;
import cn.aslight.workhub.service.ops.OpsMonitorService;
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

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/ops/monitors")
public class OpsMonitorController {

    private final OpsMonitorService opsMonitorService;

    public OpsMonitorController(OpsMonitorService opsMonitorService) {
        this.opsMonitorService = opsMonitorService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ops:monitor:view') or hasAuthority('ops:monitor:manage')")
    public ApiResponse<PageResponse<OpsMonitorResponse>> list(@RequestParam(required = false) String monitorType,
                                                              @RequestParam(required = false) String businessLineCode,
                                                              @RequestParam(required = false) String environmentCode,
                                                              @RequestParam(required = false) String keyword,
                                                              @RequestParam(defaultValue = "false") boolean enabledOnly) {
        List<OpsMonitorResponse> items = opsMonitorService.list(monitorType, businessLineCode, environmentCode, keyword, enabledOnly);
        return ApiResponse.success(new PageResponse<>(items.size(), items));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ops:monitor:create') or hasAuthority('ops:monitor:manage')")
    public ApiResponse<OpsMonitorResponse> create(@Valid @RequestBody OpsMonitorSaveRequest request) {
        return ApiResponse.success(opsMonitorService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ops:monitor:update') or hasAuthority('ops:monitor:manage')")
    public ApiResponse<OpsMonitorResponse> update(@PathVariable Long id,
                                                  @Valid @RequestBody OpsMonitorSaveRequest request) {
        return ApiResponse.success(opsMonitorService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ops:monitor:delete') or hasAuthority('ops:monitor:manage')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        opsMonitorService.delete(id);
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/check")
    @PreAuthorize("hasAuthority('ops:monitor:check')")
    public ApiResponse<OpsMonitorCheckResponse> check(@PathVariable Long id) {
        return ApiResponse.success(opsMonitorService.check(id));
    }

    @GetMapping("/xxl-job/dashboard")
    @PreAuthorize("hasAuthority('ops:monitor:view') or hasAuthority('ops:monitor:manage')")
    public ApiResponse<PageResponse<XxlJobDashboardResponse>> xxlJobDashboard(@RequestParam(required = false) String businessLineCode,
                                                                               @RequestParam(required = false) String environmentCode,
                                                                               @RequestParam(defaultValue = "true") boolean enabledOnly) {
        List<XxlJobDashboardResponse> items = opsMonitorService.dashboard(businessLineCode, environmentCode, enabledOnly);
        return ApiResponse.success(new PageResponse<>(items.size(), items));
    }

    @GetMapping("/xxl-job/executors")
    @PreAuthorize("hasAuthority('ops:monitor:view') or hasAuthority('ops:monitor:manage')")
    public ApiResponse<List<XxlJobExecutorResponse>> xxlJobExecutors(@RequestParam String businessLineCode,
                                                                      @RequestParam String environmentCode,
                                                                      @RequestParam String xxlJobDatabaseName) {
        return ApiResponse.success(opsMonitorService.xxlJobExecutors(businessLineCode, environmentCode, xxlJobDatabaseName));
    }

    @GetMapping("/{id}/xxl-job/detail")
    @PreAuthorize("hasAuthority('ops:monitor:view') or hasAuthority('ops:monitor:manage')")
    public ApiResponse<XxlJobDashboardResponse> xxlJobDetail(@PathVariable Long id,
                                                             @RequestParam(required = false) LocalDate startDate,
                                                             @RequestParam(required = false) LocalDate endDate,
                                                             @RequestParam(defaultValue = "1") int page,
                                                             @RequestParam(defaultValue = "20") int pageSize,
                                                             @RequestParam(required = false) String author,
                                                             @RequestParam(required = false) String executorAppName,
                                                             @RequestParam(defaultValue = "ALL") String logStatus) {
        return ApiResponse.success(opsMonitorService.xxlJobDetail(id, startDate, endDate, page, pageSize, author, executorAppName, logStatus));
    }

    @GetMapping("/{id}/xxl-job/logs")
    @PreAuthorize("hasAuthority('ops:monitor:view') or hasAuthority('ops:monitor:manage')")
    public ApiResponse<XxlJobLogPageResponse> xxlJobLogs(@PathVariable Long id,
                                                         @RequestParam(required = false) LocalDate startDate,
                                                         @RequestParam(required = false) LocalDate endDate,
                                                         @RequestParam(defaultValue = "1") int page,
                                                         @RequestParam(defaultValue = "20") int pageSize,
                                                         @RequestParam(required = false) String author,
                                                         @RequestParam(required = false) String executorAppName,
                                                         @RequestParam(defaultValue = "ALL") String logStatus) {
        return ApiResponse.success(opsMonitorService.xxlJobLogs(id, startDate, endDate, page, pageSize, author, executorAppName, logStatus));
    }
}
