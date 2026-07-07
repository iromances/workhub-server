package cn.aslight.workhub.controller.mcp;

import cn.aslight.workhub.common.api.ApiResponse;
import cn.aslight.workhub.common.api.PageResponse;
import cn.aslight.workhub.model.mcp.McpAuditEntryResponse;
import cn.aslight.workhub.model.mcp.McpCatalogResponse;
import cn.aslight.workhub.model.mcp.McpResourceResponse;
import cn.aslight.workhub.model.mcp.McpResourceSaveRequest;
import cn.aslight.workhub.service.mcp.McpResourceService;
import jakarta.validation.Valid;
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

    public McpResourceController(McpResourceService mcpResourceService) {
        this.mcpResourceService = mcpResourceService;
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
    public ApiResponse<PageResponse<McpAuditEntryResponse>> auditEntries(@RequestParam(defaultValue = "100") int limit) {
        List<McpAuditEntryResponse> items = mcpResourceService.auditEntries(limit);
        return ApiResponse.success(new PageResponse<>(items.size(), items));
    }
}
