package cn.aslight.workhub.service.ai;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

class AiFailureDiagnosticsTest {
    @Test void preservesCauseAndFramesButMasksCredentials() {
        var cause = new IllegalArgumentException("finishReason=length password=secret-value Bearer abc.def");
        var failure = new IllegalStateException("gateway failed", cause);
        failure.addSuppressed(new IllegalArgumentException("suppressed"));
        String stack = AiFailureDiagnostics.stackTrace(failure);
        assertTrue(stack.contains("Caused by:"));
        assertTrue(stack.contains("Suppressed:"));
        assertTrue(stack.contains("finishReason=length"));
        assertTrue(stack.contains("AiFailureDiagnosticsTest.java:"));
        assertFalse(stack.contains("secret-value"));
        assertFalse(stack.contains("abc.def"));
        String serialized = new ObjectMapper().writeValueAsString(AiGatewayResult.failed("failed", "provider", "model", 1, cause));
        assertFalse(serialized.contains("failureCause"));
        assertFalse(serialized.contains("secret-value"));
    }

    @Test void limitsPersistedTextAndMarksTruncation() {
        String stack = AiFailureDiagnostics.stackTrace(new IllegalStateException("中".repeat(16000)));
        assertEquals(12000, stack.length());
        assertTrue(stack.endsWith("[堆栈过长，已截断]"));
    }
}
