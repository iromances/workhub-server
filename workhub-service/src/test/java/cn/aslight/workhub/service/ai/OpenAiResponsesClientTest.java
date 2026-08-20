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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiResponsesClientTest {

    @Test
    void execute_shouldUseResponsesContractAndJoinOutputText() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/responses", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = """
                    {
                      "output": [
                        {"content": [{"type":"output_text","text":"{\\\"status\\\":\\\"OK\\\"}"}]}
                      ]
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            AiProviderConfigEntity provider = new AiProviderConfigEntity();
            provider.setApiBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
            provider.setConnectTimeoutSeconds(2);
            OpenAiResponsesClient client = new OpenAiResponsesClient(objectMapper);

            String result = client.execute(new AiApiInvocation(
                    "test.responses",
                    provider,
                    "secret",
                    "gpt-test",
                    "HIGH",
                    "FAST",
                    5,
                    "返回 JSON",
                    List.of(),
                    "{\"type\":\"object\",\"additionalProperties\":false}"
            ));

            assertEquals("{\"status\":\"OK\"}", result);
            JsonNode body = objectMapper.readTree(requestBody.get());
            assertEquals("gpt-test", body.path("model").asText());
            assertEquals("返回 JSON", body.path("input").asText());
            assertFalse(body.path("store").asBoolean());
            assertEquals("high", body.path("reasoning").path("effort").asText());
            assertEquals("priority", body.path("service_tier").asText());
            assertEquals("json_schema", body.path("text").path("format").path("type").asText());
            assertTrue(body.path("text").path("format").path("strict").asBoolean());
        } finally {
            server.stop(0);
        }
    }
}
