package cn.aslight.workhub.service.ai;

import cn.aslight.workhub.model.ai.ZCodeModelCatalogRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import tools.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ZCodeModelCatalogTest {
    private final ObjectMapper json = new ObjectMapper();
    private String snapshot() {
        return """
            {"protocol":{"name":"ZCode Protocol","version":1},"session":{"sessionId":"fixture"},
             "settings":{"model":{"current":{"providerId":"a","modelId":"GLM"},"available":[
              {"ref":{"providerId":"a","modelId":"GLM"},"label":"GLM","providerLabel":"先行通",
               "reasoning":{"levels":[{"value":"low"},{"value":"max"}],"defaultLevel":"max"}},
              {"ref":{"providerId":"b","modelId":"GLM"},"label":"GLM",
               "reasoning":{"levels":[{"value":"enabled"}],"defaultLevel":"enabled"}}
             ]}}}
            """;
    }
    @Test void keepsProviderIdentityAndRawEfforts() {
        var models = ZCodeModelCatalogConversation.parseModels(json.readTree(snapshot()));
        assertEquals(2, models.size());
        assertEquals("a/GLM", models.getFirst().model());
        assertEquals("b/GLM", models.getLast().model());
        assertEquals("GLM", models.getFirst().displayName());
        assertEquals("max", models.getFirst().defaultReasoningEffort());
        assertTrue(models.getFirst().isDefault());
        assertFalse(models.getLast().isDefault());
    }
    @Test void rejectsInvalidDefaultAndDuplicateIdentity() {
        assertThrows(IllegalStateException.class, () -> ZCodeModelCatalogConversation.parseModels(
                json.readTree(snapshot().replace("\"defaultLevel\":\"max\"", "\"defaultLevel\":\"ultra\""))));
        assertThrows(IllegalStateException.class, () -> ZCodeModelCatalogConversation.parseModels(
                json.readTree(snapshot().replace("\"providerId\":\"b\"", "\"providerId\":\"a\""))));
    }
    @Test void createsDeferredSessionClosesAndNeverSendsPrompt() throws Exception {
        var conversation = new ZCodeModelCatalogConversation(json, Path.of("/tmp/catalog-test"));
        var out = new ByteArrayOutputStream();
        conversation.start(out);
        conversation.accept(json.readTree("{\"id\":99,\"method\":\"permission/request\"}"));
        conversation.accept(json.readTree("{\"id\":\"catalog-create\",\"result\":" + snapshot() + "}"));
        assertFalse(conversation.result.isDone());
        conversation.accept(json.readTree("{\"id\":\"catalog-close\",\"result\":{\"closed\":true}}"));
        assertEquals(2, conversation.result.get().size());
        var messages = out.toString(java.nio.charset.StandardCharsets.UTF_8).lines().map(json::readTree).toList();
        assertEquals("deferred", messages.getFirst().path("params").path("persistence").asText());
        assertEquals(-32601, messages.get(1).path("error").path("code").asInt());
        assertEquals("session/close", messages.getLast().path("method").asText());
        assertEquals(3, messages.size());
    }
    @Test void rejectsRpcFailureAndEarlyClose() {
        var conversation = new ZCodeModelCatalogConversation(json, Path.of("/tmp/catalog-test"));
        assertThrows(IllegalStateException.class, () -> conversation.accept(json.readTree("{\"id\":\"catalog-create\",\"error\":{\"message\":\"secret\"}}")));
        assertThrows(IllegalStateException.class, () -> conversation.accept(json.readTree("{\"id\":\"catalog-close\",\"result\":{\"closed\":true}}")));
    }
    @Test
    @EnabledIfSystemProperty(named = "workhub.test.zcode.catalog", matches = "true")
    void readsInstalledCliWithoutInference() {
        var service = new ZCodeModelCatalogService(new ZCodeCliClient(json), json);
        var request = new ZCodeModelCatalogRequest(System.getProperty("workhub.test.zcode.command"),
                System.getProperty("workhub.test.zcode.runtime"), System.getProperty("workhub.test.zcode.builtin"),
                System.getProperty("workhub.test.zcode.personal"), true);
        var response = service.query(request);
        assertFalse(response.cached());
        assertTrue(response.models().stream().anyMatch(m -> m.model().endsWith("/glm-5.3")));
        assertTrue(response.models().stream().anyMatch(m -> m.model().endsWith("/GLM-5.3-Flash")));
    }
}
