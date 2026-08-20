package cn.aslight.workhub.controller.ai;

import cn.aslight.workhub.common.api.ApiResponse;
import cn.aslight.workhub.common.api.PageResponse;
import cn.aslight.workhub.model.ai.AiProviderConfigRequest;
import cn.aslight.workhub.model.ai.AiProviderConfigResponse;
import cn.aslight.workhub.model.ai.AiProviderConnectionTestResponse;
import cn.aslight.workhub.model.ai.AiProviderProbeRequest;
import cn.aslight.workhub.model.ai.AiProviderProbeResponse;
import cn.aslight.workhub.model.ai.AiUseCaseConfigQuery;
import cn.aslight.workhub.model.ai.AiUseCaseConfigRequest;
import cn.aslight.workhub.model.ai.AiUseCaseConfigResponse;
import cn.aslight.workhub.service.ai.AiProviderConfigService;
import cn.aslight.workhub.service.ai.AiProviderConnectionTestService;
import cn.aslight.workhub.service.ai.AiProviderProbeService;
import cn.aslight.workhub.service.ai.AiUseCaseConfigService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 配置管理接口控制器。
 */
@RestController
@RequestMapping("/api/system")
public class AiConfigController {

    private final AiProviderConfigService aiProviderConfigService;
    private final AiUseCaseConfigService aiUseCaseConfigService;
    private final AiProviderProbeService aiProviderProbeService;
    private final AiProviderConnectionTestService aiProviderConnectionTestService;

    public AiConfigController(AiProviderConfigService aiProviderConfigService,
                              AiUseCaseConfigService aiUseCaseConfigService,
                              AiProviderProbeService aiProviderProbeService,
                              AiProviderConnectionTestService aiProviderConnectionTestService) {
        this.aiProviderConfigService = aiProviderConfigService;
        this.aiUseCaseConfigService = aiUseCaseConfigService;
        this.aiProviderProbeService = aiProviderProbeService;
        this.aiProviderConnectionTestService = aiProviderConnectionTestService;
    }

    @GetMapping("/ai-providers")
    @PreAuthorize("hasAuthority('system:ai-config:view') or hasAuthority('system:ai-config:manage')")
    public ApiResponse<PageResponse<AiProviderConfigResponse>> listProviders() {
        List<AiProviderConfigResponse> items = aiProviderConfigService.list();
        return ApiResponse.success(new PageResponse<>(items.size(), items));
    }

    @GetMapping("/ai-providers/{id}")
    @PreAuthorize("hasAuthority('system:ai-config:view') or hasAuthority('system:ai-config:manage')")
    public ApiResponse<AiProviderConfigResponse> providerDetail(@PathVariable Long id) {
        return ApiResponse.success(aiProviderConfigService.detail(id));
    }

    @PostMapping("/ai-providers")
    @PreAuthorize("hasAuthority('system:ai-config:create') or hasAuthority('system:ai-config:manage')")
    public ApiResponse<AiProviderConfigResponse> createProvider(@Valid @RequestBody AiProviderConfigRequest request,
                                                                 Authentication authentication,
                                                                 HttpServletRequest httpRequest) {
        return ApiResponse.success(aiProviderConfigService.create(
                request,
                authentication.getName(),
                clientIp(httpRequest)
        ));
    }

    @PutMapping("/ai-providers/{id}")
    @PreAuthorize("hasAuthority('system:ai-config:update') or hasAuthority('system:ai-config:manage')")
    public ApiResponse<AiProviderConfigResponse> updateProvider(@PathVariable Long id,
                                                                @Valid @RequestBody AiProviderConfigRequest request,
                                                                Authentication authentication,
                                                                HttpServletRequest httpRequest) {
        return ApiResponse.success(aiProviderConfigService.update(
                id,
                request,
                authentication.getName(),
                clientIp(httpRequest)
        ));
    }

    @PostMapping("/ai-providers/{id}/probe")
    @PreAuthorize("hasAuthority('system:ai-config:update') or hasAuthority('system:ai-config:manage')")
    public ApiResponse<AiProviderProbeResponse> probeProvider(@PathVariable Long id,
                                                              @Valid @RequestBody AiProviderProbeRequest request,
                                                              Authentication authentication,
                                                              HttpServletRequest httpRequest) {
        return ApiResponse.success(aiProviderProbeService.probe(
                id,
                request,
                authentication.getName(),
                clientIp(httpRequest)
        ));
    }

    @PostMapping("/ai-providers/test-connection")
    @PreAuthorize("hasAuthority('system:ai-config:create') or hasAuthority('system:ai-config:update') or hasAuthority('system:ai-config:manage')")
    public ApiResponse<AiProviderConnectionTestResponse> testProviderConnection(
            @Valid @RequestBody AiProviderConfigRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        return ApiResponse.success(aiProviderConnectionTestService.test(
                request,
                authentication.getName(),
                clientIp(httpRequest)
        ));
    }

    @GetMapping("/ai-use-cases")
    @PreAuthorize("hasAuthority('system:ai-config:view') or hasAuthority('system:ai-config:manage')")
    public ApiResponse<PageResponse<AiUseCaseConfigResponse>> listUseCases(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) Long providerConfigId,
            @RequestParam(required = false) String channelType,
            @RequestParam(required = false) String vendor,
            @RequestParam(required = false) String modelProvider,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword) {
        AiUseCaseConfigQuery query = new AiUseCaseConfigQuery();
        query.setPage(page);
        query.setPageSize(pageSize);
        query.setDomain(domain);
        query.setProviderConfigId(providerConfigId);
        query.setChannelType(channelType);
        query.setVendor(vendor);
        query.setModelProvider(modelProvider);
        query.setEnabled(enabled);
        query.setKeyword(keyword);
        List<AiUseCaseConfigResponse> items = aiUseCaseConfigService.list(query);
        return ApiResponse.success(page(items, page, pageSize));
    }

    @GetMapping("/ai-use-cases/{id}")
    @PreAuthorize("hasAuthority('system:ai-config:view') or hasAuthority('system:ai-config:manage')")
    public ApiResponse<AiUseCaseConfigResponse> useCaseDetail(@PathVariable Long id) {
        return ApiResponse.success(aiUseCaseConfigService.detail(id));
    }

    @PostMapping("/ai-use-cases")
    @PreAuthorize("hasAuthority('system:ai-config:create') or hasAuthority('system:ai-config:manage')")
    public ApiResponse<AiUseCaseConfigResponse> createUseCase(@Valid @RequestBody AiUseCaseConfigRequest request,
                                                               Authentication authentication,
                                                               HttpServletRequest httpRequest) {
        return ApiResponse.success(aiUseCaseConfigService.create(
                request,
                authentication.getName(),
                clientIp(httpRequest)
        ));
    }

    @PutMapping("/ai-use-cases/{id}")
    @PreAuthorize("hasAuthority('system:ai-config:update') or hasAuthority('system:ai-config:manage')")
    public ApiResponse<AiUseCaseConfigResponse> updateUseCase(@PathVariable Long id,
                                                              @Valid @RequestBody AiUseCaseConfigRequest request,
                                                              Authentication authentication,
                                                              HttpServletRequest httpRequest) {
        return ApiResponse.success(aiUseCaseConfigService.update(
                id,
                request,
                authentication.getName(),
                clientIp(httpRequest)
        ));
    }

    private <T> PageResponse<T> page(List<T> items, int page, int pageSize) {
        int normalizedPage = Math.max(page, 1);
        int normalizedPageSize = Math.min(Math.max(pageSize, 1), 200);
        int fromIndex = Math.min((normalizedPage - 1) * normalizedPageSize, items.size());
        int toIndex = Math.min(fromIndex + normalizedPageSize, items.size());
        return new PageResponse<>(items.size(), items.subList(fromIndex, toIndex));
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
