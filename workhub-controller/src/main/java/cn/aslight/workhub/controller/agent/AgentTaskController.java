package cn.aslight.workhub.controller.agent;

import cn.aslight.workhub.common.api.ApiResponse;
import cn.aslight.workhub.model.agent.*;
import cn.aslight.workhub.service.agent.AgentWorkbenchService;
import cn.aslight.workhub.service.agent.AgentRunReconciliationService;
import cn.aslight.workhub.service.agent.AgentRunAcceptanceService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agent-tasks")
public class AgentTaskController {
    private final AgentWorkbenchService service;

    private final AgentRunReconciliationService reconciliation;

    private final AgentRunAcceptanceService acceptance;

    public AgentTaskController(AgentWorkbenchService service, AgentRunReconciliationService reconciliation, AgentRunAcceptanceService acceptance) {
        this.service = service;
        this.reconciliation = reconciliation;
        this.acceptance = acceptance;
    }

    @GetMapping("/{id}/runs/{runId}/acceptance")
    @PreAuthorize("hasAuthority('agent:task:view')")
    public ApiResponse<AgentRunAcceptance> acceptance(@PathVariable String id, @PathVariable String runId, Authentication auth) {
        return ApiResponse.success(acceptance.preview(id, runId, auth.getName()));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('agent:task:view')")
    public ApiResponse<AgentPage<AgentTaskResponse>> list(@RequestParam String agentId, Authentication auth,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(service.tasks(agentId, auth.getName(), page, pageSize));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('agent:task:view')")
    public ApiResponse<AgentTaskResponse> detail(@PathVariable String id, Authentication auth) {
        return ApiResponse.success(service.task(id, auth.getName()));
    }

    @PutMapping("/{id}/schedule")
    @PreAuthorize("hasAuthority('agent:task:operate')")
    public ApiResponse<AgentTaskResponse> schedule(@PathVariable String id,
            @Valid @RequestBody AgentScheduleRequest request, Authentication auth) {
        return ApiResponse.success(service.schedule(id, request, auth.getName()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('agent:task:operate')")
    public ApiResponse<AgentTaskResponse> update(@PathVariable String id,
            @Valid @RequestBody AgentTaskUpdateRequest request, Authentication auth) {
        return ApiResponse.success(service.updateTask(id, request, auth.getName()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('agent:manage')")
    public ApiResponse<Void> delete(@PathVariable String id, @RequestParam long expectedVersion, Authentication auth) {
        service.deleteTask(id, expectedVersion, auth.getName());
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/run")
    @PreAuthorize("hasAuthority('agent:task:operate')")
    public ApiResponse<AgentRunResponse> run(@PathVariable String id,
            @Valid @RequestBody AgentRunRequest request, Authentication auth) {
        return ApiResponse.success(service.run(id, request, auth.getName()));
    }

    @GetMapping("/{id}/runs")
    @PreAuthorize("hasAuthority('agent:task:view')")
    public ApiResponse<AgentPage<AgentRunResponse>> runs(@PathVariable String id, Authentication auth,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(service.runs(id, auth.getName(), page, pageSize));
    }

    @GetMapping("/{id}/runs/{runId}")
    @PreAuthorize("hasAuthority('agent:task:view')")
    public ApiResponse<AgentRunDetailResponse> runDetail(@PathVariable String id,
            @PathVariable String runId, Authentication auth) {
        return ApiResponse.success(service.runDetail(id, runId, auth.getName()));
    }

    @GetMapping("/{id}/runs/{runId}/reconciliation")
    @PreAuthorize("hasAuthority('agent:task:view')")
    public ApiResponse<AgentRunReconciliationView> reconciliation(@PathVariable String id,
            @PathVariable String runId, Authentication auth) {
        return ApiResponse.success(reconciliation.preview(id, runId, auth.getName()));
    }

    @PostMapping("/{id}/runs/{runId}/reconciliation")
    @PreAuthorize("hasAuthority('agent:task:operate') and hasAuthority('agent:manage')")
    public ApiResponse<AgentRunReconciliationResult> reconcile(@PathVariable String id, @PathVariable String runId,
            @Valid @RequestBody AgentRunReconciliationRequest request, Authentication auth) {
        return ApiResponse.success(reconciliation.reconcile(id, runId, request, auth.getName()));
    }

    @DeleteMapping("/{id}/runs/{runId}")
    @PreAuthorize("hasAuthority('agent:manage')")
    public ApiResponse<Void> deleteRun(@PathVariable String id, @PathVariable String runId,
            @RequestParam long expectedVersion, Authentication auth) {
        service.deleteRun(id, runId, expectedVersion, auth.getName());
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/runs/{runId}/cancel")
    @PreAuthorize("hasAuthority('agent:task:operate')")
    public ApiResponse<AgentRunResponse> cancel(@PathVariable String id, @PathVariable String runId,
            @Valid @RequestBody AgentVersionRequest request, Authentication auth) {
        return ApiResponse.success(service.cancel(id, runId, request.expectedVersion(), auth.getName()));
    }

    @PostMapping("/{id}/messages")
    @PreAuthorize("hasAuthority('agent:task:operate')")
    public ApiResponse<AgentRunResponse> message(@PathVariable String id,
            @Valid @RequestBody AgentMessageRequest request, Authentication auth) {
        return ApiResponse.success(service.message(id, request, auth.getName()));
    }

    @PostMapping("/{id}/runs/{runId}/retry")
    @PreAuthorize("hasAuthority('agent:task:operate')")
    public ApiResponse<AgentRunResponse> retry(@PathVariable String id, @PathVariable String runId,
            @Valid @RequestBody AgentRunRequest request, Authentication auth) {
        return ApiResponse.success(service.retry(id, runId, request, auth.getName()));
    }
}
