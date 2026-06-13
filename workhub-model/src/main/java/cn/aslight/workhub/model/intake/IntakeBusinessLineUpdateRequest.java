package cn.aslight.workhub.model.intake;

import jakarta.validation.constraints.NotBlank;

/**
 * 需求业务线更新请求。
 */
public class IntakeBusinessLineUpdateRequest {

    /**
     * 业务线名称。
     */
    @NotBlank(message = "业务线不能为空")
    private String businessLine;

    public String getBusinessLine() {
        return businessLine;
    }

    public void setBusinessLine(String businessLine) {
        this.businessLine = businessLine;
    }
}
