package cn.aslight.workhub.model.workitem;

import jakarta.validation.constraints.NotBlank;

/**
 * WorkItemFollowUp 请求模型。
 */
public class WorkItemFollowUpRequest {

    @NotBlank(message = "不能为空")
    private String content;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
