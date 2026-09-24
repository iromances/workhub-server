package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.config.AiProperties;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CodexCliCommandTest {
    @Test
    void reasoningIsEncodedAsOneConfigStringWithoutChangingItsValue() {
        var client = new CodexCliClient(new AiProperties());
        var request = new CodexCliClient.CodexCliRequest("test", Path.of("."), List.of(), List.of(), "{}", "test");
        for (String effort : List.of("ultra", "minimal", "futureEffort", "quote\"\\\nvalue")) {
            var options = new CodexCliClient.CodexCliExecutionOptions("codex", "CaseModel", effort, 10, true, false);
            var args = client.buildCommand(request, Path.of("schema"), Path.of("output"), options);
            String config = args.stream().filter(value -> value.startsWith("model_reasoning_effort=")).findFirst().orElseThrow();
            assertEquals(effort, new ObjectMapper().readTree(config.substring(config.indexOf('=') + 1)).asText());
            assertFalse(config.contains("\n"));
            assertEquals("CaseModel", args.get(args.indexOf("-m") + 1));
        }
    }
}
