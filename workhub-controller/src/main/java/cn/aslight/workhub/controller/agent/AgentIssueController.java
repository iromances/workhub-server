package cn.aslight.workhub.controller.agent;

import cn.aslight.workhub.common.api.ApiResponse;
import cn.aslight.workhub.model.agent.AgentIssueFixRequest;
import cn.aslight.workhub.model.agent.AgentIssueResponse;
import cn.aslight.workhub.model.agent.AgentIssueReviewRequest;
import cn.aslight.workhub.service.agent.AgentFixService;
import cn.aslight.workhub.service.agent.AgentIssueReviewService;
import cn.aslight.workhub.service.agent.AgentIssueService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agent-issues")
public class AgentIssueController {
    private final AgentIssueService service;
    private final AgentFixService fixService;
    private final AgentIssueReviewService reviewService;

    public AgentIssueController(AgentIssueService service, AgentFixService fixService, AgentIssueReviewService reviewService) {
        this.service = service;
        this.fixService = fixService;
        this.reviewService = reviewService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('agent:view')")
    public ApiResponse<List<AgentIssueResponse>> list(@RequestParam(required = false) String status,
            @RequestParam(required = false) String domain, @RequestParam(required = false) String severity,
            @RequestParam(required = false) String businessLineCode, @RequestParam(required = false) String agentId,
            @RequestParam(required = false) String runId, @RequestParam(defaultValue = "200") int limit,
            Authentication auth) {
        return ApiResponse.success(service.list(status, domain, severity, businessLineCode, agentId, runId, limit));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('agent:view')")
    public ApiResponse<AgentIssueResponse> detail(@PathVariable String id, Authentication auth) {
        return ApiResponse.success(service.detail(id));
    }

    @PostMapping("/fix")
    @PreAuthorize("hasAuthority('agent:manage')")
    public ApiResponse<List<AgentIssueResponse>> fix(@Valid @RequestBody AgentIssueFixRequest request, Authentication auth) {
        return ApiResponse.success(fixService.fix(request, auth.getName()));
    }

    @PutMapping("/{id}/review")
    @PreAuthorize("hasAuthority('agent:manage')")
    public ApiResponse<AgentIssueResponse> review(@PathVariable String id,
            @Valid @RequestBody AgentIssueReviewRequest request, Authentication auth) {
        return ApiResponse.success(reviewService.review(id, request, auth.getName()));
    }

    @PostMapping("/{id}/deploy")
    @PreAuthorize("hasAuthority('agent:manage')")
    public ApiResponse<AgentIssueResponse> deploy(@PathVariable String id,
            @RequestBody Map<String, String> body, Authentication auth) {
        return ApiResponse.success(reviewService.deployDecision(id, body.get("outcome"), auth.getName()));
    }
}

