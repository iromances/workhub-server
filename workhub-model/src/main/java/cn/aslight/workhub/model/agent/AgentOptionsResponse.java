package cn.aslight.workhub.model.agent;

import java.util.List;

/** 配置表单只获得选择所需信息，不返回执行命令、工作目录、API 地址或凭据。 */
public record AgentOptionsResponse(List<Provider> providers, List<Tool> tools, List<Scope> scopes) {
    public record Provider(Long id, String code, String name, String channelType, String defaultModel, boolean enabled) { }
    public record Tool(String code, String description) { }
    public record Scope(String businessLineCode, String businessLineName, List<String> environments) { }
}
