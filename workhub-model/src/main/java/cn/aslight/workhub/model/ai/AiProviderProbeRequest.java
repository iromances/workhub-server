package cn.aslight.workhub.model.ai;

import jakarta.validation.constraints.NotBlank;

/**
 * AI 供应商连接探针请求。
 */
public class AiProviderProbeRequest {

    @NotBlank(message = "探针模型不能为空")
    private String model;

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }
}
