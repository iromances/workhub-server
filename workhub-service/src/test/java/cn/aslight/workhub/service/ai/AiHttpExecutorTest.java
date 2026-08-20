package cn.aslight.workhub.service.ai;

import cn.aslight.workhub.model.ai.AiProviderConfigEntity;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiHttpExecutorTest {
    @Test
    void send_shouldEnforceBodyReadIdleTimeoutIndependentlyFromCallTimeout() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/slow-body", exchange -> {
            exchange.sendResponseHeaders(200, 2);
            exchange.getResponseBody().write("{".getBytes());
            exchange.getResponseBody().flush();
            try { Thread.sleep(1500); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            try { exchange.getResponseBody().write("}".getBytes()); } finally { exchange.close(); }
        });
        server.start();
        try {
            AiProviderConfigEntity provider = new AiProviderConfigEntity();
            provider.setConnectTimeoutSeconds(2);
            provider.setReadTimeoutSeconds(1);
            provider.setCallTimeoutSeconds(5);
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/slow-body"))
                    .timeout(Duration.ofSeconds(5)).GET().build();
            long started = System.nanoTime();
            Exception error = assertThrows(Exception.class, () -> AiHttpExecutor.send(request, provider, 5));
            long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;
            assertTrue(elapsedMillis >= 800 && elapsedMillis < 4000,
                    "读取超时应先于 5 秒总调用超时触发，实际 " + elapsedMillis + "ms，错误=" + error);
        } finally {
            server.stop(0);
        }
    }
}
