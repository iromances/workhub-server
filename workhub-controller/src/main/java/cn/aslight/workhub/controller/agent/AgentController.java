package cn.aslight.workhub.controller.agent;

import cn.aslight.workhub.common.api.ApiResponse;
import cn.aslight.workhub.model.agent.*;
import cn.aslight.workhub.service.agent.AgentWorkbenchService;
import cn.aslight.workhub.service.agent.AgentOptionsService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agents")
public class AgentController {
    private final AgentWorkbenchService service;
    private final AgentOptionsService options;

    public AgentController(AgentWorkbenchService service, AgentOptionsService options) {
        this.service = service;
        this.options = options;
    }

    @GetMapping("/{id}/runs/{runId}/report")
    @PreAuthorize("hasAuthority('agent:task:view')")
    public ApiResponse<java.util.Map<String, Object>> report(@PathVariable String id, @PathVariable String runId, Authentication auth) {
        return ApiResponse.success(service.report(id, runId, auth.getName()));
    }

    @GetMapping("/options")
    @PreAuthorize("hasAuthority('agent:view')")
    public ApiResponse<AgentOptionsResponse> options(Authentication auth) {
        return ApiResponse.success(options.options(auth.getName()));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('agent:view')")
    public ApiResponse<AgentPage<AgentDefinitionResponse>> list(Authentication auth,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(service.list(auth.getName(), page, pageSize));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('agent:view')")
    public ApiResponse<AgentDefinitionResponse> detail(@PathVariable String id, Authentication auth) {
        return ApiResponse.success(service.detail(id, auth.getName()));
    }

    @GetMapping("/{id}/duty-task")
    @PreAuthorize("hasAuthority('agent:task:view')")
    public ApiResponse<AgentTaskResponse> dutyTask(@PathVariable String id, Authentication auth) {
        return ApiResponse.success(service.dutyTask(id, auth.getName()));
    }

    @GetMapping("/{id}/runs")
    @PreAuthorize("hasAuthority('agent:task:view')")
    public ApiResponse<AgentPage<AgentRunResponse>> runs(@PathVariable String id, Authentication auth,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(service.agentRuns(id, auth.getName(), page, pageSize));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('agent:manage')")
    public ApiResponse<AgentDefinitionResponse> create(@Valid @RequestBody AgentSaveRequest request, Authentication auth) {
        return ApiResponse.success(service.create(request, auth.getName()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('agent:manage')")
    public ApiResponse<AgentDefinitionResponse> update(@PathVariable String id,
            @Valid @RequestBody AgentSaveRequest request, Authentication auth) {
        return ApiResponse.success(service.update(id, request, auth.getName()));
    }

    @PostMapping("/{id}/enable")
    @PreAuthorize("hasAuthority('agent:manage')")
    public ApiResponse<AgentDefinitionResponse> enable(@PathVariable String id,
            @Valid @RequestBody AgentVersionRequest request, Authentication auth) {
        return ApiResponse.success(service.setEnabled(id, request.expectedVersion(), true, auth.getName()));
    }

    @PostMapping("/{id}/disable")
    @PreAuthorize("hasAuthority('agent:manage')")
    public ApiResponse<AgentDefinitionResponse> disable(@PathVariable String id,
            @Valid @RequestBody AgentVersionRequest request, Authentication auth) {
        return ApiResponse.success(service.setEnabled(id, request.expectedVersion(), false, auth.getName()));
    }

    @PostMapping("/{id}/tasks")
    @PreAuthorize("hasAuthority('agent:task:assign')")
    public ApiResponse<AgentTaskResponse> task(@PathVariable String id,
            @Valid @RequestBody AgentTaskCreateRequest request, Authentication auth) {
        return ApiResponse.success(service.createTask(id, request, auth.getName()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('agent:manage')")
    public ApiResponse<Void> delete(@PathVariable String id, @RequestParam long expectedVersion, Authentication auth) {
        service.deleteAgent(id, expectedVersion, auth.getName());
        return ApiResponse.success(null);
    }
}
