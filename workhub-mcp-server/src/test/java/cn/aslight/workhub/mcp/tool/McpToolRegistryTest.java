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
    void exposesBoundedFileToolsWithoutArbitraryCommandParameters() {
        McpToolRegistry registry = McpToolRegistry.firstVersion(
                new McpResourceCatalog(List.of(), List.of(), List.of(), List.of(), Map.of(), Map.of()), new ObjectMapper());
        for (String name : List.of("list_server_directory", "read_server_file")) {
            JsonNode tool = java.util.stream.StreamSupport.stream(registry.listTools().spliterator(), false)
                    .filter(node -> name.equals(node.path("name").asText())).findFirst().orElseThrow();
            assertEquals(3, tool.path("inputSchema").path("required").size());
            assertEquals("path", tool.path("inputSchema").path("required").get(2).asText());
            org.junit.jupiter.api.Assertions.assertFalse(tool.path("inputSchema").path("properties").has("command"));
        }
    }

    @Test
    void knowledgeContextKeepsVaultReadOnlyAndResolvesArchivePathsUnderRepository() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        McpToolRegistry registry = McpToolRegistry.firstVersion(
                new McpResourceCatalog(List.of(), List.of(), List.of(), List.of(),
                        Map.of("projectVaultPath", "/knowledge", "projectDocumentPath", "/workspace/workhub-server/docs"), Map.of()),
                mapper);
        JsonNode context = mapper.valueToTree(registry.call("get_knowledge_context", mapper.readTree("{\"businessLine\":\"BL000001\"}")));
        assertEquals("/knowledge", context.path("projectVaultPath").asText());
        org.junit.jupiter.api.Assertions.assertFalse(context.has("businessLineRawRoot"));
        assertEquals("/workspace/workhub-server/docs/requirements/BL000001", context.path("businessLineWikiRoot").asText());
    }

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
