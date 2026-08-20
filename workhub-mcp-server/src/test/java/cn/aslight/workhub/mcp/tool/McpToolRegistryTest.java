package cn.aslight.workhub.mcp.tool;

import cn.aslight.workhub.mcp.config.McpResourceCatalog;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class McpToolRegistryTest {

    @Test
    void firstVersion_shouldExposeControlledLogSearchTool() {
        McpToolRegistry registry = McpToolRegistry.firstVersion(
                new McpResourceCatalog(List.of(), List.of(), List.of(), List.of(), Map.of(), Map.of()),
                new ObjectMapper()
        );

        JsonNode tool = java.util.stream.StreamSupport.stream(registry.listTools().spliterator(), false)
                .filter(node -> "search_service_logs".equals(node.path("name").asText()))
                .findFirst()
                .orElse(null);

        assertNotNull(tool);
        JsonNode required = tool.path("inputSchema").path("required");
        assertEquals(4, required.size());
        assertEquals("targetKey", required.get(0).asText());
        assertEquals("profileKey", required.get(1).asText());
        assertEquals("logPath", required.get(2).asText());
        assertEquals("keyword", required.get(3).asText());
        assertEquals("string", tool.path("inputSchema").path("properties").path("maxMatches").path("type").asText());
    }
}
