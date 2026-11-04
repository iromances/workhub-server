package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.model.ai.AiProviderConfigEntity;
import cn.aslight.workhub.service.ai.ZCodeCliClient;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** 不同 CLI 按真实参数和协议适配，不能将任意 CLI 当作 Codex 执行。 */
public interface AgentCliProtocol {
    boolean supports(AiProviderConfigEntity provider);
    void preflight(AiProviderConfigEntity provider) throws IOException, InterruptedException;
    cn.aslight.workhub.service.ai.CliTaskLaunch prepare(AiProviderConfigEntity provider, String model, Path directory, Path schema, Path output,
                                 String mcpUrl, String token, List<String> toolNames, String prompt) throws IOException;
}
