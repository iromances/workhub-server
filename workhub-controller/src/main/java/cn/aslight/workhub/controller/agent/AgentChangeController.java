package cn.aslight.workhub.controller.agent;

import cn.aslight.workhub.common.api.ApiResponse;
import cn.aslight.workhub.model.agent.*;
import cn.aslight.workhub.service.agent.AgentChangeService;
import cn.aslight.workhub.service.agent.AgentDeliveryService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/agent-changes")
@PreAuthorize("hasAuthority('agent:task:view') and hasAuthority('agent:code:use')")
public class AgentChangeController {
    private final AgentChangeService service;
    private final AgentDeliveryService deliveries;
    public AgentChangeController(AgentChangeService service, AgentDeliveryService deliveries) { this.service = service; this.deliveries = deliveries; }

    @GetMapping
    public ApiResponse<AgentPage<Map<String, Object>>> list(Authentication auth, @RequestParam String runId,
            @RequestParam(defaultValue = "1") int page) {
        return ApiResponse.success(service.listForUser(runId, auth.getName(), page));
    }
    @GetMapping("/{id}")
    public ApiResponse<AgentChangeView> detail(Authentication auth, @PathVariable String id) {
        return ApiResponse.success(service.detailForUser(id, auth.getName()));
    }
    @GetMapping("/{id}/deliveries")
    public ApiResponse<java.util.List<Map<String, Object>>> deliveries(Authentication auth, @PathVariable String id) {
        return ApiResponse.success(deliveries.listForUser(id, auth.getName()));
    }
}
