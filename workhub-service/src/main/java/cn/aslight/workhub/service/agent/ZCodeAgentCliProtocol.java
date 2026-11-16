package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.model.ai.AiProviderConfigEntity;
import cn.aslight.workhub.service.ai.AiCliType;
import cn.aslight.workhub.service.ai.ZCodeCliClient;
import cn.aslight.workhub.service.mcp.McpRuntimeService;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

@Component
public class ZCodeAgentCliProtocol implements AgentCliProtocol {
    private final ZCodeCliClient client;
    private final McpRuntimeService mcpRuntime;
    public ZCodeAgentCliProtocol(ZCodeCliClient client, McpRuntimeService mcpRuntime) {
        this.client = client;
        this.mcpRuntime = mcpRuntime;
    }
    @Override public boolean supports(AiProviderConfigEntity provider) { return AiCliType.is(provider, "ZCODE"); }
    @Override public void preflight(AiProviderConfigEntity provider) throws IOException, InterruptedException { client.preflight(provider); }
    @Override
    public cn.aslight.workhub.service.ai.CliTaskLaunch prepare(AiProviderConfigEntity provider, String model, Path directory, Path schema, Path output,
                                         String mcpUrl, String token, String prompt) throws IOException {
        String finalResultSchema = java.nio.file.Files.readString(schema);
        // 平台 MCP 全量工具放开，AI 依据职责描述自行选择；未配置 MCP Token 时不放开任何工具。
        List<String> allowedTools = mcpUrl == null ? List.of()
                : mcpRuntime.toolNames().stream().map(name -> "mcp__workhub_task__" + name).toList();
        return client.prepare(provider, model, provider.getDefaultReasoningLevel(), directory, output,
                prompt + "\n最终输出必须是单个 JSON 对象，严格符合：\n" + finalResultSchema,
                allowedTools, mcpUrl, token, finalResultSchema);
    }
}
