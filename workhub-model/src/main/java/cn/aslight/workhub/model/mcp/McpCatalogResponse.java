package cn.aslight.workhub.model.mcp;

import java.util.List;
import java.util.Map;

public record McpCatalogResponse(List<Map<String, Object>> businessLines,
                                 List<Map<String, Object>> environments,
                                 List<Map<String, Object>> databaseTargets,
                                 List<Map<String, Object>> serverTargets,
                                 Map<String, Object> knowledge,
                                 Map<String, Object> gitlab) {
}
