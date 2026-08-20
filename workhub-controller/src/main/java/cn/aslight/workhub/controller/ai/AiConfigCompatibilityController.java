package cn.aslight.workhub.controller.ai;

import cn.aslight.workhub.common.api.ApiResponse;
import cn.aslight.workhub.model.ai.*;
import cn.aslight.workhub.service.ai.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** 与 asset AI 配置页面完全同路径、同方法的兼容接口。 */
@RestController
@RequestMapping("/api/manage/system")
public class AiConfigCompatibilityController {
    private final AiGatewayConfigService gatewayConfigService;
    private final AiProviderConfigService providerService;
    private final AiProviderProbeService providerProbeService;
    private final AiProviderConnectionTestService providerConnectionTestService;
    private final AiUseCaseConfigService useCaseService;
    public AiConfigCompatibilityController(AiGatewayConfigService gatewayConfigService,
                                           AiProviderConfigService providerService,
                                           AiProviderProbeService providerProbeService,
                                           AiProviderConnectionTestService providerConnectionTestService,
                                           AiUseCaseConfigService useCaseService) {
        this.gatewayConfigService = gatewayConfigService;
        this.providerService = providerService;
        this.providerProbeService = providerProbeService;
        this.providerConnectionTestService = providerConnectionTestService;
        this.useCaseService = useCaseService;
    }

    @GetMapping("/ai-config/detail")
    @PreAuthorize("hasAuthority('system:ai-config:view') or hasAuthority('system:ai-config:manage')")
    public ApiResponse<AiGatewayConfig> configDetail() { return ApiResponse.success(gatewayConfigService.getCurrentConfig()); }
    @PostMapping("/ai-config/save")
    @PreAuthorize("hasAuthority('system:ai-config:update') or hasAuthority('system:ai-config:manage')")
    public ApiResponse<AiGatewayConfig> configSave(@RequestBody AiGatewayConfig config) { return ApiResponse.success(gatewayConfigService.save(config)); }

    @GetMapping("/ai-provider/list")
    @PreAuthorize("hasAuthority('system:ai-config:view') or hasAuthority('system:ai-config:manage')")
    public ApiResponse<List<AiProviderConfigResponse>> providers() { return ApiResponse.success(providerService.listForManage()); }
    @GetMapping("/ai-provider/{id}")
    @PreAuthorize("hasAuthority('system:ai-config:view') or hasAuthority('system:ai-config:manage')")
    public ApiResponse<AiProviderConfigResponse> provider(@PathVariable Long id) { return ApiResponse.success(providerService.detailForManage(id)); }
    @PostMapping("/ai-provider/save")
    @PreAuthorize("hasAuthority('system:ai-config:create') or hasAuthority('system:ai-config:update') or hasAuthority('system:ai-config:manage')")
    public ApiResponse<AiProviderConfigResponse> saveProvider(@Valid @RequestBody AiProviderConfigRequest request,
            Authentication authentication, HttpServletRequest httpRequest) {
        return ApiResponse.success(providerService.saveForManage(request, authentication.getName(), clientIp(httpRequest)));
    }
    @PostMapping("/ai-provider/{id}/probe")
    @PreAuthorize("hasAuthority('system:ai-config:update') or hasAuthority('system:ai-config:manage')")
    public ApiResponse<AiProviderProbeResponse> probeProvider(@PathVariable Long id,
            @Valid @RequestBody AiProviderProbeRequest request,
            Authentication authentication, HttpServletRequest httpRequest) {
        return ApiResponse.success(providerProbeService.probe(
                id, request, authentication.getName(), clientIp(httpRequest)));
    }
    @PostMapping("/ai-provider/test-connection")
    @PreAuthorize("hasAuthority('system:ai-config:create') or hasAuthority('system:ai-config:update') or hasAuthority('system:ai-config:manage')")
    public ApiResponse<AiProviderConnectionTestResponse> testProviderConnection(
            @Valid @RequestBody AiProviderConfigRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        return ApiResponse.success(providerConnectionTestService.test(
                request, authentication.getName(), clientIp(httpRequest)));
    }

    @PostMapping("/ai-use-case/list")
    @PreAuthorize("hasAuthority('system:ai-config:view') or hasAuthority('system:ai-config:manage')")
    public ApiResponse<AiUseCaseManagePage> useCases(@RequestBody(required=false) AiUseCaseConfigQuery query) {
        return ApiResponse.success(useCaseService.listForManage(query));
    }
    @GetMapping("/ai-use-case/{id}")
    @PreAuthorize("hasAuthority('system:ai-config:view') or hasAuthority('system:ai-config:manage')")
    public ApiResponse<AiUseCaseConfigResponse> useCase(@PathVariable Long id) { return ApiResponse.success(useCaseService.detailForManage(id)); }
    @PostMapping("/ai-use-case/save")
    @PreAuthorize("hasAuthority('system:ai-config:create') or hasAuthority('system:ai-config:update') or hasAuthority('system:ai-config:manage')")
    public ApiResponse<AiUseCaseConfigResponse> saveUseCase(@Valid @RequestBody AiUseCaseConfigRequest request,
            Authentication authentication, HttpServletRequest httpRequest) {
        return ApiResponse.success(useCaseService.saveForManage(request, authentication.getName(), clientIp(httpRequest)));
    }
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded != null && !forwarded.isBlank() ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
    }
}
