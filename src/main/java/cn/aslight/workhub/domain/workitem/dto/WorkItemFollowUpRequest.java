package cn.aslight.workhub.domain.workitem.dto;

import jakarta.validation.constraints.NotBlank;

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
