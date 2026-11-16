package cn.aslight.workhub.controller.system;

import cn.aslight.workhub.common.api.ApiResponse;
import cn.aslight.workhub.model.system.JobAdminModels.JobAdminItem;
import cn.aslight.workhub.model.system.JobAdminModels.JobEnabledUpdateRequest;
import cn.aslight.workhub.model.system.JobAdminModels.JobExecutionPage;
import cn.aslight.workhub.model.system.JobAdminModels.JobScheduleUpdateRequest;
import cn.aslight.workhub.model.system.JobAdminModels.JobTriggerRequest;
import cn.aslight.workhub.model.system.JobAdminModels.JobTriggerResult;
import cn.aslight.workhub.service.system.JobAdminService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** REQ-9f3c2a84：Job 管理接口。 */
@RestController
@RequestMapping("/api/ops/jobs")
public class JobAdminController {

    private final JobAdminService jobAdminService;

    public JobAdminController(JobAdminService jobAdminService) {
        this.jobAdminService = jobAdminService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ops:job:view') or hasAuthority('ops:job:manage')")
    public ApiResponse<List<JobAdminItem>> list() {
        return ApiResponse.success(jobAdminService.list());
    }

    @PutMapping("/{code}/schedule")
    @PreAuthorize("hasAuthority('ops:job:manage')")
    public ApiResponse<JobAdminItem> updateSchedule(@PathVariable String code,
                                                    @RequestBody JobScheduleUpdateRequest request) {
        return ApiResponse.success(jobAdminService.updateSchedule(code, request));
    }

    @PutMapping("/{code}/enabled")
    @PreAuthorize("hasAuthority('ops:job:manage')")
    public ApiResponse<JobAdminItem> updateEnabled(@PathVariable String code,
                                                   @RequestBody JobEnabledUpdateRequest request) {
        return ApiResponse.success(jobAdminService.updateEnabled(code, request.enabled()));
    }

    @PostMapping("/{code}/trigger")
    @PreAuthorize("hasAuthority('ops:job:trigger') or hasAuthority('ops:job:manage')")
    public ApiResponse<JobTriggerResult> trigger(@PathVariable String code,
                                                 @RequestBody(required = false) JobTriggerRequest request) {
        return ApiResponse.success(jobAdminService.trigger(code, request == null ? null : request.params()));
    }

    @GetMapping("/{code}/executions")
    @PreAuthorize("hasAuthority('ops:job:view') or hasAuthority('ops:job:manage')")
    public ApiResponse<JobExecutionPage> executions(@PathVariable String code,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "20") int pageSize,
                                                    @RequestParam(required = false) String status) {
        return ApiResponse.success(jobAdminService.executions(code, page, pageSize, status));
    }
}
