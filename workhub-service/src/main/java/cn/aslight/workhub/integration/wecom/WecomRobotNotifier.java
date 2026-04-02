package cn.aslight.workhub.integration.wecom;

import cn.aslight.workhub.config.WecomRobotProperties;
import cn.aslight.workhub.model.workitem.WorkItemDetailResponse;
import cn.aslight.workhub.model.workitem.WorkItemReminderCandidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Component
/**
 * 企业微信群机器人通知器。
 */
public class WecomRobotNotifier {

    private static final Logger log = LoggerFactory.getLogger(WecomRobotNotifier.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final WecomRobotProperties wecomRobotProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public WecomRobotNotifier(WecomRobotProperties wecomRobotProperties, ObjectMapper objectMapper) {
        this.wecomRobotProperties = wecomRobotProperties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
    }

    public void notifyAssignment(WorkItemDetailResponse workItem,
                                 String previousOwner,
                                 String previousFollower,
                                 String reason,
                                 String operatorUserName) {
        String content = """
                **WorkHub 指派通知**
                > 工作项：%s %s
                > 项目：%s
                > 原负责人：%s
                > 新负责人：%s
                > 原跟进人：%s
                > 新跟进人：%s
                > 操作人：%s
                > 说明：%s
                """.formatted(
                workItem.no(),
                workItem.title(),
                workItem.projectName(),
                safe(previousOwner),
                safe(workItem.ownerUserName()),
                safe(previousFollower),
                safe(workItem.followerUserName()),
                safe(operatorUserName),
                safe(reason)
        );
        sendMarkdown("assign-" + workItem.id(), content);
    }

    public void notifyStatusChanged(WorkItemDetailResponse workItem,
                                    String fromStatus,
                                    String toStatus,
                                    String reason,
                                    String operatorUserName) {
        String content = """
                **WorkHub 状态流转通知**
                > 工作项：%s %s
                > 项目：%s
                > 状态：%s -> %s
                > 负责人：%s
                > 操作人：%s
                > 说明：%s
                """.formatted(
                workItem.no(),
                workItem.title(),
                workItem.projectName(),
                safe(fromStatus),
                safe(toStatus),
                safe(workItem.ownerUserName()),
                safe(operatorUserName),
                safe(reason)
        );
        sendMarkdown("transition-" + workItem.id() + "-" + toStatus, content);
    }

    public void notifyDueReminder(WorkItemReminderCandidate candidate) {
        String content = """
                **WorkHub 到期提醒**
                > 工作项：%s %s
                > 项目：%s
                > 当前状态：%s
                > 优先级：%s
                > 负责人：%s
                > 计划结束：%s
                """.formatted(
                candidate.no(),
                candidate.title(),
                candidate.projectName(),
                candidate.status(),
                candidate.priority(),
                candidate.ownerUserName(),
                candidate.plannedEndAt().format(TIME_FORMATTER)
        );
        sendMarkdown("due-" + candidate.id(), content);
    }

    private void sendMarkdown(String bizKey, String content) {
        if (!wecomRobotProperties.isEnabled() || isBlank(wecomRobotProperties.getWebhook())) {
            log.info("Skip WeCom robot notify because webhook is disabled. bizKey={}", bizKey);
            return;
        }
        try {
            String requestBody = objectMapper.writeValueAsString(Map.of(
                    "msgtype", "markdown",
                    "markdown", Map.of("content", content)
            ));
            HttpRequest request = HttpRequest.newBuilder(URI.create(wecomRobotProperties.getWebhook().trim()))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            log.info("WeCom robot notify finished. bizKey={}, statusCode={}, body={}", bizKey, response.statusCode(), response.body());
        } catch (JacksonException | IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("WeCom robot notify failed but business flow continues. bizKey={}", bizKey, ex);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String safe(String value) {
        return isBlank(value) ? "-" : value.trim();
    }
}
