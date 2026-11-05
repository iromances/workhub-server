package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.model.ai.AiProviderConfigEntity;
import cn.aslight.workhub.service.ai.AiCliType;
import cn.aslight.workhub.service.ai.ZCodeCliClient;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

@Component
public class ZCodeAgentCliProtocol implements AgentCliProtocol {
    private final ZCodeCliClient client;
    public ZCodeAgentCliProtocol(ZCodeCliClient client) { this.client = client; }
    @Override public boolean supports(AiProviderConfigEntity provider) { return AiCliType.is(provider, "ZCODE"); }
    @Override public void preflight(AiProviderConfigEntity provider) throws IOException, InterruptedException { client.preflight(provider); }
    @Override
    public cn.aslight.workhub.service.ai.CliTaskLaunch prepare(AiProviderConfigEntity provider, String model, Path directory, Path schema, Path output,
                                         String mcpUrl, String token, List<String> toolNames, String prompt) throws IOException {
        String finalResultSchema = java.nio.file.Files.readString(schema);
        return client.prepare(provider, model, provider.getDefaultReasoningLevel(), directory, output,
                prompt + "\n最终输出必须是单个 JSON 对象，严格符合：\n" + finalResultSchema,
                toolNames.stream().map(name -> "mcp__workhub_task__" + name).toList(), mcpUrl, token, finalResultSchema);
    }
}
