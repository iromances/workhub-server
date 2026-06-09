package cn.aslight.workhub.controller.mcp;

import cn.aslight.workhub.service.mcp.McpRuntimeService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mcp/runtime")
public class McpRuntimeController {

    private final McpRuntimeService mcpRuntimeService;

    public McpRuntimeController(McpRuntimeService mcpRuntimeService) {
        this.mcpRuntimeService = mcpRuntimeService;
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> call(@RequestBody String request) {
        return ResponseEntity.ok(mcpRuntimeService.handle(request));
    }
}
