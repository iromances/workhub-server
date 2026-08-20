package cn.aslight.workhub.controller.mcp;

import cn.aslight.workhub.service.mcp.McpRuntimeService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mcp/runtime")
public class McpRuntimeController {

    private final McpRuntimeService mcpRuntimeService;

    public McpRuntimeController(McpRuntimeService mcpRuntimeService) {
        this.mcpRuntimeService = mcpRuntimeService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> call(@RequestBody String request,
                                       @RequestHeader(value = "MCP-Protocol-Version", required = false) String protocolVersion,
                                       @RequestHeader(value = "Origin", required = false) String origin) {
        if (origin != null && !origin.isBlank()) {
            return ResponseEntity.status(403).build();
        }
        McpRuntimeService.HttpResponse response = mcpRuntimeService.handleHttp(request, protocolVersion);
        if (response.body() == null) {
            return ResponseEntity.status(response.status()).build();
        }
        return ResponseEntity.status(response.status())
                .contentType(MediaType.APPLICATION_JSON)
                .body(response.body());
    }

    @GetMapping
    public ResponseEntity<Void> listen() {
        return ResponseEntity.status(405).build();
    }
}
