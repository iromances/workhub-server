package cn.aslight.workhub.controller.agent;

import cn.aslight.workhub.common.api.ApiResponse;
import cn.aslight.workhub.model.agent.*;
import cn.aslight.workhub.model.autonomy.*;
import cn.aslight.workhub.service.autonomy.AutonomyCaseService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/autonomy/cases")
@PreAuthorize("hasAuthority('autonomy:case:view') and hasAuthority('agent:view')")
public class AutonomyCaseController {
    private final AutonomyCaseService service;
    public AutonomyCaseController(AutonomyCaseService service) { this.service = service; }

    @GetMapping
    public ApiResponse<AgentPage<AutonomyCaseEntity>> list(Authentication auth, @RequestParam String agentId,
            @RequestParam String businessLineCode, @RequestParam String environmentCode,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(service.listForUser(agentId, new AgentScope(businessLineCode, environmentCode), auth.getName(), page, pageSize));
    }

    @GetMapping("/{id}")
    public ApiResponse<AutonomyCaseDetailResponse> detail(@PathVariable String id, @RequestParam String agentId, Authentication auth,
            @RequestParam(defaultValue = "1") int evidencePage, @RequestParam(defaultValue = "1") int proposalPage,
            @RequestParam(defaultValue = "5") int pageSize) {
        return ApiResponse.success(service.detailForUser(agentId, id, auth.getName(), evidencePage, proposalPage, pageSize));
    }

    @GetMapping("/{id}/resolutions")
    public ApiResponse<AgentPage<AutonomyCaseResolution>> resolutions(@PathVariable String id,@RequestParam String agentId,
            Authentication auth,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="5") int pageSize) {
        return ApiResponse.success(service.resolutionsForUser(agentId,id,auth.getName(),page,pageSize));
    }

    @GetMapping("/{id}/bugs")
    public ApiResponse<AgentPage<AutonomyBugLinkView>> bugs(@PathVariable String id,
            @RequestParam String agentId, Authentication auth, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "5") int pageSize) {
        return ApiResponse.success(service.bugsForUser(agentId, id, auth.getName(), page, pageSize));
    }
}
