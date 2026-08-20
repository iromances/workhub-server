package cn.aslight.workhub.service.ai;

import cn.aslight.workhub.model.ai.AiProviderConfigEntity;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiChatClientTest {

    @Test
    void execute_shouldUseChatCompletionsStructuredOutputContract() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = """
                    {"choices":[{"message":{"content":"{\\\"status\\\":\\\"OK\\\"}"}}]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            AiProviderConfigEntity provider = new AiProviderConfigEntity();
            provider.setApiBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
            provider.setConnectTimeoutSeconds(2);
            OpenAiChatClient client = new OpenAiChatClient(objectMapper);

            String result = client.execute(new AiApiInvocation(
                    "test.chat",
                    provider,
                    "secret",
                    "gpt-test",
                    "XHIGH",
                    "STANDARD",
                    5,
                    "返回 JSON",
                    List.of(),
                    "{\"type\":\"object\",\"additionalProperties\":false}"
            ));

            assertEquals("{\"status\":\"OK\"}", result);
            JsonNode body = objectMapper.readTree(requestBody.get());
            assertEquals("xhigh", body.path("reasoning_effort").asText());
            assertEquals("json_schema", body.path("response_format").path("type").asText());
            assertEquals("structured_output", body.path("response_format").path("json_schema").path("name").asText());
            assertTrue(body.path("response_format").path("json_schema").path("strict").asBoolean());
        } finally {
            server.stop(0);
        }
    }
}
