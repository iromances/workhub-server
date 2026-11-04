package cn.aslight.workhub.service.agent;

import tools.jackson.databind.JsonNode;
import java.util.Set;

/** 企业能力桥接契约；执行器只能调用授权目录中的能力。 */
public interface AgentTool {
    String code();
    String description();
    JsonNode inputSchema();
    Set<String> permissions();
    /** 只有明确只读的操作才允许在丢失回执后重做。写工具必须覆盖并返回 false。 */
    default boolean readOnly() { return false; }
    Object execute(AgentToolContext context, JsonNode arguments);

    /** 工具已明确拒绝操作、无未知外部副作用；允许 AI 按错误修正参数。 */
    class Rejected extends IllegalArgumentException {
        public Rejected(String message) { super(message); }
    }
}
