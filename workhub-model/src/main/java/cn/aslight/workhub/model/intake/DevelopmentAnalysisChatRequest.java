package cn.aslight.workhub.model.intake;

import jakarta.validation.constraints.NotBlank;

/**
 * 研发分析 AI 对话调整请求。
 */
public class DevelopmentAnalysisChatRequest {

    @NotBlank(message = "不能为空")
    private String message;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
