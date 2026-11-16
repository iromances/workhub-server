package cn.aslight.workhub.controller.ai;

import cn.aslight.workhub.common.api.ApiResponse;
import cn.aslight.workhub.model.ai.CodexModelCatalogResponse;
import cn.aslight.workhub.model.ai.ZCodeModelCatalogRequest;
import cn.aslight.workhub.service.ai.ZCodeModelCatalogService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
public class ZCodeModelCatalogController {
    private final ZCodeModelCatalogService service;
    public ZCodeModelCatalogController(ZCodeModelCatalogService service) { this.service = service; }

    @PostMapping("/api/manage/system/ai-provider/zcode-models")
    @PreAuthorize("hasAuthority('system:ai-config:create') or hasAuthority('system:ai-config:update') or hasAuthority('system:ai-config:manage')")
    public ApiResponse<CodexModelCatalogResponse> models(@Valid @RequestBody ZCodeModelCatalogRequest request) {
        return ApiResponse.success(service.query(request));
    }
}
