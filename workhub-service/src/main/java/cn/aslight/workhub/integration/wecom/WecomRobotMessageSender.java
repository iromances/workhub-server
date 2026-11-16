package cn.aslight.workhub.integration.wecom;

import cn.aslight.workhub.mcp.audit.McpAuditLogger;
import cn.aslight.workhub.service.system.SysConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * 数字员工企微群机器人 markdown 消息发送器。
 * webhook 从加密系统配置 wecom.agent.webhook 读取，AI 不经手凭据；发送结果透传企微 errcode，不自动重试。
 */
@Component
public class WecomRobotMessageSender {

    public static final String CONFIG_GROUP = "wecom.agent";
    public static final String CONFIG_KEY = "webhook";

    static final int MAX_CONTENT_BYTES = 4096;

    private static final Logger log = LoggerFactory.getLogger(WecomRobotMessageSender.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final SysConfigService sysConfigService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final McpAuditLogger auditLogger;

    @Autowired
    public WecomRobotMessageSender(SysConfigService sysConfigService, ObjectMapper objectMapper) {
        this(sysConfigService, objectMapper, HttpClient.newHttpClient(),
                McpAuditLogger.defaultLogger(new com.fasterxml.jackson.databind.ObjectMapper()));
    }

    WecomRobotMessageSender(SysConfigService sysConfigService, ObjectMapper objectMapper,
                            HttpClient httpClient, McpAuditLogger auditLogger) {
        this.sysConfigService = sysConfigService;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.auditLogger = auditLogger;
    }

    public WecomRobotSendResult send(String content) {
        String message = requireContent(content);
        String webhook = sysConfigService.requireSecretValue(CONFIG_GROUP, CONFIG_KEY);
        long startedAt = System.nanoTime();
        WecomRobotSendResult result = deliver(message, webhook.trim());
        log.info("WeCom robot message send finished. sent={}, errcode={}, contentBytes={}, durationMs={}",
                result.sent(), result.errcode(), message.getBytes(StandardCharsets.UTF_8).length,
                Duration.ofNanos(System.nanoTime() - startedAt).toMillis());
        audit(message, result, Duration.ofNanos(System.nanoTime() - startedAt).toMillis());
        return result;
    }

    private WecomRobotSendResult deliver(String message, String webhook) {
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "msgtype", "markdown",
                    "markdown", Map.of("content", message)
            ));
            HttpRequest request = HttpRequest.newBuilder(URI.create(webhook))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                return new WecomRobotSendResult(false, -1, "企微机器人 HTTP " + response.statusCode());
            }
            return parseResult(response.body());
        } catch (IOException ex) {
            return new WecomRobotSendResult(false, -1, "企微机器人请求失败：" + ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new WecomRobotSendResult(false, -1, "企微机器人发送被中断");
        }
    }

    private WecomRobotSendResult parseResult(String body) {
        try {
            JsonNode node = objectMapper.readTree(body);
            int errcode = node.path("errcode").asInt(-1);
            return new WecomRobotSendResult(errcode == 0, errcode, node.path("errmsg").asText(""));
        } catch (JacksonException ex) {
            return new WecomRobotSendResult(false, -1, "企微机器人响应无法解析");
        }
    }

    private String requireContent(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("消息内容不能为空");
        }
        String message = content.strip();
        int bytes = message.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > MAX_CONTENT_BYTES) {
            throw new IllegalArgumentException("消息内容超出企微 markdown 上限 4096 字节（当前 " + bytes + " 字节），请压缩明细后重试");
        }
        return message;
    }

    private void audit(String content, WecomRobotSendResult result, long durationMs) {
        auditLogger.record(Map.ofEntries(
                Map.entry("tool", "send_wecom_robot_markdown"),
                Map.entry("contentBytes", content.getBytes(StandardCharsets.UTF_8).length),
                Map.entry("errcode", result.errcode()),
                Map.entry("result", result.sent() ? "SUCCESS" : "FAILED"),
                Map.entry("durationMs", durationMs)
        ));
    }
}
