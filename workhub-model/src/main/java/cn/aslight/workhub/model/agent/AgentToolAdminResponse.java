package cn.aslight.workhub.model.agent;

import java.util.List;

/** 工具管理列表条目：code/只读/权限来自代码目录；名称与描述来自 pm_agent_tool，未维护时回退 code/空串。 */
public record AgentToolAdminResponse(String code, String name, String description, boolean readOnly,
                                     List<String> permissions, boolean maintained, boolean retired, long version) {

    public AgentToolAdminResponse {
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
    }
}
