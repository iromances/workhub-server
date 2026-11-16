package cn.aslight.workhub.controller.agent;

import cn.aslight.workhub.common.api.ApiResponse;
import cn.aslight.workhub.model.agent.AgentToolAdminResponse;
import cn.aslight.workhub.model.agent.AgentToolSaveRequest;
import cn.aslight.workhub.service.agent.AgentToolAdminService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/agent-tools")
public class AgentToolController {
    private final AgentToolAdminService service;

    public AgentToolController(AgentToolAdminService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('agent:view')")
    public ApiResponse<List<AgentToolAdminResponse>> list(Authentication auth) {
        return ApiResponse.success(service.list(auth.getName()));
    }

    @PutMapping("/{code}")
    @PreAuthorize("hasAuthority('agent:manage')")
    public ApiResponse<AgentToolAdminResponse> update(@PathVariable String code,
            @Valid @RequestBody AgentToolSaveRequest request, Authentication auth) {
        return ApiResponse.success(service.update(code, request, auth.getName()));
    }
}
