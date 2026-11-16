package cn.aslight.workhub.controller.system;

import cn.aslight.workhub.common.api.ApiResponse;
import cn.aslight.workhub.common.api.PageResponse;
import cn.aslight.workhub.model.system.IntegrationApiCatalogResponse;
import cn.aslight.workhub.model.system.IntegrationConfigResponse;
import cn.aslight.workhub.model.system.IntegrationConfigSaveRequest;
import cn.aslight.workhub.model.system.IntegrationProbeResponse;
import cn.aslight.workhub.model.system.IntegrationSystemDetailResponse;
import cn.aslight.workhub.model.system.IntegrationSystemResponse;
import cn.aslight.workhub.model.system.IntegrationSystemSaveRequest;
import cn.aslight.workhub.service.system.IntegrationApiCatalogService;
import cn.aslight.workhub.service.system.IntegrationCatalogService;
import cn.aslight.workhub.service.system.IntegrationConfigService;
import cn.aslight.workhub.service.system.IntegrationProbeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
 * 系统集成台账接口控制器。
 */
@RestController
@RequestMapping("/api/system/integrations")
public class IntegrationController {

    private final IntegrationCatalogService catalogService;
    private final IntegrationProbeService probeService;
    private final IntegrationConfigService configService;
    private final IntegrationApiCatalogService apiCatalogService;

    public IntegrationController(IntegrationCatalogService catalogService,
                                 IntegrationProbeService probeService,
                                 IntegrationConfigService configService,
                                 IntegrationApiCatalogService apiCatalogService) {
        this.catalogService = catalogService;
        this.probeService = probeService;
        this.configService = configService;
        this.apiCatalogService = apiCatalogService;
    }

    /**
     * 查询系统集成台账列表。
     */
    @GetMapping
    @PreAuthorize("hasAuthority('system:integration:view')")
    public ApiResponse<PageResponse<IntegrationSystemResponse>> list() {
        List<IntegrationSystemResponse> items = catalogService.list();
        return ApiResponse.success(new PageResponse<>(items.size(), items));
    }

    /**
     * 查询集成系统详情（能力清单与配置组状态）。
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:integration:view')")
    public ApiResponse<IntegrationSystemDetailResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(catalogService.detail(id));
    }

    /**
     * 新增集成系统。
     */
    @PostMapping
    @PreAuthorize("hasAuthority('system:integration:create')")
    public ApiResponse<IntegrationSystemDetailResponse> create(@Valid @RequestBody IntegrationSystemSaveRequest request,
                                                               Authentication authentication,
                                                               HttpServletRequest httpRequest) {
        return ApiResponse.success(catalogService.create(
                request,
                authentication == null ? null : authentication.getName(),
                clientIp(httpRequest)
        ));
    }

    /**
     * 编辑集成系统元数据与能力清单（系统编码不可修改）。
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:integration:update')")
    public ApiResponse<IntegrationSystemDetailResponse> update(@PathVariable Long id,
                                                               @Valid @RequestBody IntegrationSystemSaveRequest request,
                                                               Authentication authentication,
                                                               HttpServletRequest httpRequest) {
        return ApiResponse.success(catalogService.update(
                id,
                request,
                authentication == null ? null : authentication.getName(),
                clientIp(httpRequest)
        ));
    }

    /**
     * 测试连接：按系统编码分派只读探针。
     */
    @PostMapping("/{id}/probe")
    @PreAuthorize("hasAuthority('system:integration:update')")
    public ApiResponse<IntegrationProbeResponse> probe(@PathVariable Long id) {
        return ApiResponse.success(probeService.probe(catalogService.requireExisting(id)));
    }

    /**
     * 查询连接配置：仅返回地址/账号与凭据已配置布尔，不回显明文。
     */
    @GetMapping("/{id}/config")
    @PreAuthorize("hasAuthority('system:integration:view')")
    public ApiResponse<IntegrationConfigResponse> config(@PathVariable Long id) {
        return ApiResponse.success(configService.get(id));
    }

    /**
     * 官方 API 接口目录分页查询（官方文档快照，只读）。
     */
    @GetMapping("/{id}/apis")
    @PreAuthorize("hasAuthority('system:integration:view')")
    public ApiResponse<IntegrationApiCatalogResponse> apis(@PathVariable Long id,
                                                           @RequestParam(required = false) String resourceGroup,
                                                           @RequestParam(required = false) String httpMethod,
                                                           @RequestParam(required = false) String keyword,
                                                           @RequestParam(required = false) Integer page,
                                                           @RequestParam(required = false) Integer pageSize) {
        return ApiResponse.success(apiCatalogService.page(id, resourceGroup, httpMethod, keyword, page, pageSize));
    }

    /**
     * 保存连接配置：password/apiToken 留空表示保留既有凭据；Jenkins 实例段覆盖保存，Job 绑定保留。
     */
    @PutMapping("/{id}/config")
    @PreAuthorize("hasAuthority('system:integration:update')")
    public ApiResponse<IntegrationConfigResponse> saveConfig(@PathVariable Long id,
                                                             @RequestBody IntegrationConfigSaveRequest request,
                                                             Authentication authentication,
                                                             HttpServletRequest httpRequest) {
        return ApiResponse.success(configService.save(
                id,
                request,
                authentication == null ? null : authentication.getName(),
                clientIp(httpRequest)
        ));
    }

    private String clientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
