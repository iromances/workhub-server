package cn.aslight.workhub.model.mcp;

import java.util.List;

public record McpAuditPageResponse(long total, List<McpAuditEntryResponse> items) {
}
