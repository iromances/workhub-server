package cn.aslight.workhub.model.system;

import java.time.LocalDateTime;

/**
 * 系统集成测试连接响应；不回传任何凭据。
 */
public class IntegrationProbeResponse {

    private boolean ok;
    private String message;
    private Long elapsedMs;
    private LocalDateTime probedAt;

    public IntegrationProbeResponse() {
    }

    public IntegrationProbeResponse(boolean ok, String message, Long elapsedMs) {
        this.ok = ok;
        this.message = message;
        this.elapsedMs = elapsedMs;
        this.probedAt = LocalDateTime.now();
    }

    public boolean isOk() {
        return ok;
    }

    public void setOk(boolean ok) {
        this.ok = ok;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Long getElapsedMs() {
        return elapsedMs;
    }

    public void setElapsedMs(Long elapsedMs) {
        this.elapsedMs = elapsedMs;
    }

    public LocalDateTime getProbedAt() {
        return probedAt;
    }

    public void setProbedAt(LocalDateTime probedAt) {
        this.probedAt = probedAt;
    }
}
