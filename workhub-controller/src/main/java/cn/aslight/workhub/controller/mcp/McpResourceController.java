package cn.aslight.workhub.controller.mcp;

import cn.aslight.workhub.common.api.ApiResponse;
import cn.aslight.workhub.common.api.PageResponse;
import cn.aslight.workhub.model.mcp.McpAuditEntryResponse;
import cn.aslight.workhub.model.mcp.McpAuditPageResponse;
import cn.aslight.workhub.model.mcp.McpBastionConnectionTestRequest;
import cn.aslight.workhub.model.mcp.McpBastionConnectionTestResponse;
import cn.aslight.workhub.model.mcp.McpBastionResponse;
import cn.aslight.workhub.model.mcp.McpBastionSaveRequest;
import cn.aslight.workhub.model.mcp.McpCatalogResponse;
import cn.aslight.workhub.model.mcp.McpDatabaseConnectionTestRequest;
import cn.aslight.workhub.model.mcp.McpDatabaseConnectionTestResponse;
import cn.aslight.workhub.model.mcp.McpResourceResponse;
import cn.aslight.workhub.model.mcp.McpResourceSaveRequest;
import cn.aslight.workhub.model.mcp.McpServerConnectionTestRequest;
import cn.aslight.workhub.model.mcp.McpServerConnectionTestResponse;
import cn.aslight.workhub.service.mcp.McpBastionService;
import cn.aslight.workhub.service.mcp.McpDatabaseConnectionTestService;
import cn.aslight.workhub.service.mcp.McpResourceService;
import cn.aslight.workhub.service.mcp.McpServerConnectionTestService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/mcp")
public class McpResourceController {

    private final McpResourceService mcpResourceService;
    private final McpBastionService mcpBastionService;
    private final McpDatabaseConnectionTestService databaseConnectionTestService;
    private final McpServerConnectionTestService serverConnectionTestService;

    public McpResourceController(McpResourceService mcpResourceService,
                                 McpBastionService mcpBastionService,
                                 McpDatabaseConnectionTestService databaseConnectionTestService,
                                 McpServerConnectionTestService serverConnectionTestService) {
        this.mcpResourceService = mcpResourceService;
        this.mcpBastionService = mcpBastionService;
        this.databaseConnectionTestService = databaseConnectionTestService;
        this.serverConnectionTestService = serverConnectionTestService;
    }

    @GetMapping("/bastions")
    @PreAuthorize("hasAuthority('ops:mcp-resource:view') or hasAuthority('ops:mcp-resource:manage')")
    public ApiResponse<PageResponse<McpBastionResponse>> listBastions(@RequestParam(required = false) String keyword,
                                                                      @RequestParam(defaultValue = "false") boolean enabledOnly) {
        List<McpBastionResponse> items = mcpBastionService.list(keyword, enabledOnly);
        return ApiResponse.success(new PageResponse<>(items.size(), items));
    }

    @PostMapping("/bastions")
    @PreAuthorize("hasAuthority('ops:mcp-resource:create') or hasAuthority('ops:mcp-resource:manage')")
    public ApiResponse<McpBastionResponse> createBastion(@Valid @RequestBody McpBastionSaveRequest request,
                                                         Authentication authentication,
                                                         HttpServletRequest servletRequest) {
        return ApiResponse.success(mcpBastionService.create(
                request,
                authentication == null ? null : authentication.getName(),
                clientIp(servletRequest)
        ));
    }

    @PutMapping("/bastions/{id}")
    @PreAuthorize("hasAuthority('ops:mcp-resource:update') or hasAuthority('ops:mcp-resource:manage')")
    public ApiResponse<McpBastionResponse> updateBastion(@PathVariable Long id,
                                                         @Valid @RequestBody McpBastionSaveRequest request,
                                                         Authentication authentication,
                                                         HttpServletRequest servletRequest) {
        return ApiResponse.success(mcpBastionService.update(
                id,
                request,
                authentication == null ? null : authentication.getName(),
                clientIp(servletRequest)
        ));
    }

    @PostMapping("/bastions/test-connection")
    @PreAuthorize("""
            (#request.id == null and (
                hasAuthority('ops:mcp-resource:create') or hasAuthority('ops:mcp-resource:manage')
            )) or
            (#request.id != null and (
                hasAuthority('ops:mcp-resource:update') or hasAuthority('ops:mcp-resource:manage')
            ))
            """)
    public ApiResponse<McpBastionConnectionTestResponse> testBastionConnection(
            @Valid @RequestBody McpBastionConnectionTestRequest request,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        return ApiResponse.success(mcpBastionService.testConnection(
                request,
                authentication == null ? null : authentication.getName(),
                clientIp(servletRequest)
        ));
    }

    @GetMapping("/resources")
    @PreAuthorize("hasAuthority('ops:mcp-resource:view') or hasAuthority('ops:mcp-resource:manage')")
    public ApiResponse<PageResponse<McpResourceResponse>> listResources(@RequestParam(required = false) String resourceType,
                                                                        @RequestParam(required = false) String businessLineCode,
                                                                        @RequestParam(required = false) String environmentCode,
                                                                        @RequestParam(required = false) String keyword,
                                                                        @RequestParam(defaultValue = "false") boolean enabledOnly) {
        List<McpResourceResponse> items = mcpResourceService.list(resourceType, businessLineCode, environmentCode, keyword, enabledOnly);
        return ApiResponse.success(new PageResponse<>(items.size(), items));
    }

    @PostMapping("/resources")
    @PreAuthorize("hasAuthority('ops:mcp-resource:create') or hasAuthority('ops:mcp-resource:manage')")
    public ApiResponse<McpResourceResponse> createResource(@Valid @RequestBody McpResourceSaveRequest request) {
        return ApiResponse.success(mcpResourceService.create(request));
    }

    @PutMapping("/resources/{id}")
    @PreAuthorize("hasAuthority('ops:mcp-resource:update') or hasAuthority('ops:mcp-resource:manage')")
    public ApiResponse<McpResourceResponse> updateResource(@PathVariable Long id,
                                                           @Valid @RequestBody McpResourceSaveRequest request) {
        return ApiResponse.success(mcpResourceService.update(id, request));
    }

    @PostMapping("/resources/test-database-connection")
    @PreAuthorize("""
            (#request.id == null and (
                hasAuthority('ops:mcp-resource:create') or hasAuthority('ops:mcp-resource:manage')
            )) or
            (#request.id != null and (
                hasAuthority('ops:mcp-resource:update') or hasAuthority('ops:mcp-resource:manage')
            ))
            """)
    public ApiResponse<McpDatabaseConnectionTestResponse> testDatabaseConnection(
            @Valid @RequestBody McpDatabaseConnectionTestRequest request,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        return ApiResponse.success(databaseConnectionTestService.test(
                request,
                authentication == null ? null : authentication.getName(),
                clientIp(servletRequest)
        ));
    }

    @PostMapping("/resources/test-server-connection")
    @PreAuthorize("""
            (#request.id == null and (
                hasAuthority('ops:mcp-resource:create') or hasAuthority('ops:mcp-resource:manage')
            )) or
            (#request.id != null and (
                hasAuthority('ops:mcp-resource:update') or hasAuthority('ops:mcp-resource:manage')
            ))
            """)
    public ApiResponse<McpServerConnectionTestResponse> testServerConnection(
            @Valid @RequestBody McpServerConnectionTestRequest request,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        return ApiResponse.success(serverConnectionTestService.test(
                request,
                authentication == null ? null : authentication.getName(),
                clientIp(servletRequest)
        ));
    }

    @DeleteMapping("/resources/{id}")
    @PreAuthorize("hasAuthority('ops:mcp-resource:delete') or hasAuthority('ops:mcp-resource:manage')")
    public ApiResponse<Void> deleteResource(@PathVariable Long id) {
        mcpResourceService.delete(id);
        return ApiResponse.success(null);
    }

    @GetMapping("/catalog")
    @PreAuthorize("hasAuthority('ops:mcp-resource:view') or hasAuthority('ops:mcp-resource:manage')")
    public ApiResponse<McpCatalogResponse> catalog() {
        return ApiResponse.success(mcpResourceService.catalog());
    }

    @GetMapping("/audits")
    @PreAuthorize("hasAuthority('ops:mcp-audit:view') or hasAuthority('ops:mcp-resource:view') or hasAuthority('ops:mcp-resource:manage')")
    public ApiResponse<PageResponse<McpAuditEntryResponse>> auditEntries(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Integer limit) {
        McpAuditPageResponse page = mcpResourceService.auditEntries(pageNum, limit == null ? pageSize : limit);
        return ApiResponse.success(new PageResponse<>(page.total(), page.items()));
    }

    private String clientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
