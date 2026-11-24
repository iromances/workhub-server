package cn.aslight.workhub.controller.intake;

import cn.aslight.workhub.model.intake.IntakeTestDeployment.*;
import cn.aslight.workhub.service.intake.IntakeTestDeploymentService;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import java.util.List;

/** 需求测试发布与合测入口，生产发布不经过此控制器。 */
@RestController
@RequestMapping("/api/intake/{id}/test-deployments")
public class IntakeTestDeploymentController {
    private final IntakeTestDeploymentService service;
    public IntakeTestDeploymentController(IntakeTestDeploymentService service) { this.service = service; }

    @GetMapping("/bindings")
    @PreAuthorize("hasAuthority('intake:record:detail')")
    public cn.aslight.workhub.common.api.ApiResponse<List<Binding>> bindings(@PathVariable Long id) {
        return cn.aslight.workhub.common.api.ApiResponse.success(service.bindings(id));
    }
    @GetMapping
    @PreAuthorize("hasAuthority('intake:record:detail')")
    public cn.aslight.workhub.common.api.ApiResponse<List<View>> history(@PathVariable Long id) {
        return cn.aslight.workhub.common.api.ApiResponse.success(service.history(id));
    }
    @PostMapping
    @PreAuthorize("hasAuthority('intake:stage:operate')")
    public cn.aslight.workhub.common.api.ApiResponse<View> submit(@PathVariable Long id, @RequestBody Request request,
            Authentication authentication) {
        return cn.aslight.workhub.common.api.ApiResponse.success(service.submit(id, request, authentication.getName()));
    }
}
