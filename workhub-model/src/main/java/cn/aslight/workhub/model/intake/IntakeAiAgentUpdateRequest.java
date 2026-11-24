package cn.aslight.workhub.model.intake;

import jakarta.validation.constraints.Size;

/**
 * 需求开发数字员工更新请求：设置或清除（空值清除，回退系统配置默认）。
 */
public class IntakeAiAgentUpdateRequest {

    /** 数字员工 ID；空表示清除选择，AI 开发回退系统配置。 */
    @Size(max = 64, message = "数字员工 ID 不能超过 64 字符")
    private String agentId;

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }
}
