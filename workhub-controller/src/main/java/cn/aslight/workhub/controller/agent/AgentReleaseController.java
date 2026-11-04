package cn.aslight.workhub.controller.agent;

import cn.aslight.workhub.common.api.ApiResponse;
import cn.aslight.workhub.model.agent.*;
import cn.aslight.workhub.service.agent.AgentReleasePlanService;
import cn.aslight.workhub.service.agent.AgentVerificationService;
import tools.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/agent-release-plans")
@PreAuthorize("hasAuthority('agent:task:view') and hasAuthority('agent:code:use')")
public class AgentReleaseController {
    private final AgentReleasePlanService service;
    private final AgentVerificationService verification;
    public AgentReleaseController(AgentReleasePlanService service, AgentVerificationService verification) {
        this.service = service; this.verification = verification;
    }
    @GetMapping("/{id}/verification")
    public ApiResponse<JsonNode> verification(Authentication auth, @PathVariable String id) {
        return ApiResponse.success(verification.detailForUser(id, auth.getName()));
    }
    @GetMapping("/{id}/verification/samples")
    public ApiResponse<JsonNode> verificationSamples(Authentication auth,@PathVariable String id,@RequestParam String checkId,
            @RequestParam(defaultValue="0") long afterOrder,@RequestParam(required=false) Long throughOrder,@RequestParam(defaultValue="10") int pageSize) {
        return ApiResponse.success(verification.samplesForUser(id,auth.getName(),checkId,afterOrder,throughOrder,pageSize));
    }
    @GetMapping("/{id}/verification/samples/{sampleId}/evidence")
    public ApiResponse<JsonNode> verificationEvidence(Authentication auth,@PathVariable String id,@PathVariable String sampleId,
            @RequestParam String observationId,@RequestParam String pointer) {
        return ApiResponse.success(verification.evidenceForUser(id,auth.getName(),sampleId,observationId,pointer));
    }
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(Authentication auth, @RequestParam String changeSetId) {
        return ApiResponse.success(service.list(changeSetId, auth.getName()));
    }
    @GetMapping("/bindings")
    public ApiResponse<List<Map<String, Object>>> bindings(Authentication auth, @RequestParam String changeSetId) {
        return ApiResponse.success(service.bindings(changeSetId, auth.getName()));
    }
    @PostMapping
    @PreAuthorize("hasAuthority('agent:task:view') and hasAuthority('agent:code:use') and hasAuthority('agent:code:deliver')")
    public ApiResponse<Map<String, Object>> prepare(Authentication auth, @Valid @RequestBody AgentReleasePlanRequest request) {
        return ApiResponse.success(service.prepare(request, auth.getName()));
    }
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(Authentication auth, @PathVariable String id) {
        return ApiResponse.success(service.detail(id, auth.getName()));
    }
    @PostMapping("/{id}/reviews")
    @PreAuthorize("hasAuthority('agent:task:view') and hasAuthority('agent:code:use') and hasAuthority('autonomy:release:review')")
    public ApiResponse<Map<String, Object>> review(Authentication auth, @PathVariable String id, @Valid @RequestBody AgentReleaseReviewRequest request) {
        return ApiResponse.success(service.review(id, request, auth.getName()));
    }
}
