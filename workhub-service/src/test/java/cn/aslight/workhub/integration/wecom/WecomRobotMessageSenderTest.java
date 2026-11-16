package cn.aslight.workhub.integration.wecom;

import cn.aslight.workhub.mcp.audit.McpAuditLogger;
import cn.aslight.workhub.service.system.SysConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WecomRobotMessageSenderTest {

    private static final String WEBHOOK = "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=test-key";

    @TempDir
    Path tempDir;

    private final ObjectMapper auditMapper = new ObjectMapper();
    private final SysConfigService sysConfigService = mock(SysConfigService.class);
    private final HttpClient httpClient = mock(HttpClient.class);

    @Test
    @SuppressWarnings("unchecked")
    void send_shouldDeliverMarkdownAndReturnSuccess() throws Exception {
        WecomRobotMessageSender sender = newSender();
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"errcode\":0,\"errmsg\":\"ok\"}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        WecomRobotSendResult result = sender.send("## 巡检汇总\n> 本轮无命中");

        assertTrue(result.sent());
        assertEquals(0, result.errcode());
        assertEquals("ok", result.errmsg());
        ArgumentCaptor<HttpRequest> captured = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captured.capture(), any(HttpResponse.BodyHandler.class));
        assertEquals(WEBHOOK, captured.getValue().uri().toString());
        String body = bodyOf(captured.getValue());
        assertTrue(body.contains("\"msgtype\":\"markdown\""), "actual body: " + body);
        assertTrue(body.contains("巡检汇总"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void send_shouldAuditBytesAndResultWithoutContentOrWebhook() throws Exception {
        Path auditLog = tempDir.resolve("audit.jsonl");
        WecomRobotMessageSender sender = new WecomRobotMessageSender(
                sysConfigService, new tools.jackson.databind.ObjectMapper(), httpClient,
                new McpAuditLogger(auditMapper, auditLog));
        when(sysConfigService.requireSecretValue(WecomRobotMessageSender.CONFIG_GROUP, WecomRobotMessageSender.CONFIG_KEY))
                .thenReturn(WEBHOOK);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"errcode\":0,\"errmsg\":\"ok\"}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        sender.send("## 巡检汇总：交易失败 3 笔");

        String audit = Files.readString(auditLog);
        assertTrue(audit.contains("\"tool\":\"send_wecom_robot_markdown\""));
        assertTrue(audit.contains("\"result\":\"SUCCESS\""));
        assertTrue(audit.contains("contentBytes"));
        assertFalse(audit.contains("巡检汇总"));
        assertFalse(audit.contains(WEBHOOK));
    }

    @Test
    @SuppressWarnings("unchecked")
    void send_shouldPropagateNonZeroErrcode() throws Exception {
        WecomRobotMessageSender sender = newSender();
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"errcode\":93000,\"errmsg\":\"invalid webhook url\"}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        WecomRobotSendResult result = sender.send("汇总");

        assertFalse(result.sent());
        assertEquals(93000, result.errcode());
        assertEquals("invalid webhook url", result.errmsg());
    }

    @Test
    void send_shouldRejectBlankContentBeforeRequest() {
        WecomRobotMessageSender sender = newSender();

        assertThrows(IllegalArgumentException.class, () -> sender.send("   "));
        verifyNoInteractions(httpClient);
    }

    @Test
    void send_shouldRejectContentOverWeComByteLimit() {
        WecomRobotMessageSender sender = newSender();
        String content = "长".repeat(4100);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> sender.send(content));
        assertTrue(ex.getMessage().contains("4096"));
        verifyNoInteractions(httpClient);
    }

    @Test
    void send_shouldFailFastWhenWebhookNotConfigured() {
        WecomRobotMessageSender sender = newSender();
        when(sysConfigService.requireSecretValue(WecomRobotMessageSender.CONFIG_GROUP, WecomRobotMessageSender.CONFIG_KEY))
                .thenThrow(new IllegalArgumentException("密钥配置不存在、未启用或不是加密类型：wecom.agent.webhook"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> sender.send("汇总"));
        assertTrue(ex.getMessage().contains("wecom.agent.webhook"));
        verifyNoInteractions(httpClient);
    }

    @Test
    @SuppressWarnings("unchecked")
    void send_shouldReturnFailureOnHttpError() throws Exception {
        WecomRobotMessageSender sender = newSender();
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("connection reset"));

        WecomRobotSendResult result = sender.send("汇总");

        assertFalse(result.sent());
        assertTrue(result.errmsg().contains("connection reset"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void send_shouldReturnFailureOnNon200Response() throws Exception {
        WecomRobotMessageSender sender = newSender();
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(502);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        WecomRobotSendResult result = sender.send("汇总");

        assertFalse(result.sent());
        assertTrue(result.errmsg().contains("502"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void send_shouldReturnFailureOnUnparsableBody() throws Exception {
        WecomRobotMessageSender sender = newSender();
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("<html>bad gateway</html>");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        WecomRobotSendResult result = sender.send("汇总");

        assertFalse(result.sent());
        assertEquals(-1, result.errcode());
    }

    private WecomRobotMessageSender newSender() {
        when(sysConfigService.requireSecretValue(WecomRobotMessageSender.CONFIG_GROUP, WecomRobotMessageSender.CONFIG_KEY))
                .thenReturn(WEBHOOK);
        return new WecomRobotMessageSender(
                sysConfigService, new tools.jackson.databind.ObjectMapper(), httpClient,
                new McpAuditLogger(auditMapper, tempDir.resolve("audit-" + System.nanoTime() + ".jsonl")));
    }

    /** HttpRequest 的 BodyPublisher 只能按 Flow 订阅读取，同步收齐全部字节。 */
    private String bodyOf(HttpRequest request) throws Exception {
        CompletableFuture<byte[]> future = new CompletableFuture<>();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                byte[] bytes = new byte[item.remaining()];
                item.get(bytes);
                output.writeBytes(bytes);
            }

            @Override
            public void onError(Throwable throwable) {
                future.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                future.complete(output.toByteArray());
            }
        });
        return new String(future.get(2, TimeUnit.SECONDS), StandardCharsets.UTF_8);
    }
}
