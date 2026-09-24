package cn.aslight.workhub.controller.ai;

import cn.aslight.workhub.common.api.ApiResponse;
import cn.aslight.workhub.model.ai.CodexModelCatalogRequest;
import cn.aslight.workhub.model.ai.CodexModelCatalogResponse;
import cn.aslight.workhub.service.ai.CodexModelCatalogService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CodexModelCatalogController {
    private final CodexModelCatalogService service;

    public CodexModelCatalogController(CodexModelCatalogService service) {
        this.service = service;
    }

    @PostMapping("/api/manage/system/ai-provider/codex-models")
    @PreAuthorize("hasAuthority('system:ai-config:create') or hasAuthority('system:ai-config:update') or hasAuthority('system:ai-config:manage')")
    public ApiResponse<CodexModelCatalogResponse> models(@Valid @RequestBody CodexModelCatalogRequest request) {
        return ApiResponse.success(service.query(request));
    }
}
