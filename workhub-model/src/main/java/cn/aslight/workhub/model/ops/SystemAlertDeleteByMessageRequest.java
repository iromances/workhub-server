package cn.aslight.workhub.model.ops;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class SystemAlertDeleteByMessageRequest {

    @NotBlank(message = "错误消息关键词不能为空")
    @Size(max = 200, message = "错误消息关键词不能超过200个字符")
    private String messageKeyword;

    public String getMessageKeyword() {
        return messageKeyword;
    }

    public void setMessageKeyword(String messageKeyword) {
        this.messageKeyword = messageKeyword;
    }
}
