package cn.aslight.workhub.service.ai;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CodexModelCatalogClientTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final CodexModelCatalogClient client = new CodexModelCatalogClient(mapper);
    private static final String INITIALIZED = "{\"id\":1,\"result\":{}}\n";

    private String model(String name) {
        return """
                {"model":"%s","displayName":"Model %s","isDefault":true,"hidden":false,
                 "defaultReasoningEffort":"ultra","supportedReasoningEfforts":[
                   {"reasoningEffort":"minimal","description":"light"},
                   {"reasoningEffort":"ultra","description":"deep"},
                   {"reasoningEffort":"futureEffort","description":"new"}]}
                """.formatted(name, name).replace("\n", "");
    }

    @Test
    void handshakePaginationAndNotificationsPreserveOriginalCodes() throws Exception {
        String input = INITIALIZED + "{\"method\":\"notice\",\"params\":{}}\n"
                + "{\"id\":2,\"result\":{\"data\":[" + model("CaseSensitive") + ",{\"hidden\":true}],\"nextCursor\":\"page2\"}}\n"
                + "{\"id\":3,\"result\":{\"data\":[" + model("second") + "],\"nextCursor\":null}}\n";
        StringWriter output = new StringWriter();
        var models = client.readCatalog(new StringReader(input), output);
        assertEquals(2, models.size());
        assertEquals("CaseSensitive", models.getFirst().model());
        assertEquals("ultra", models.getFirst().defaultReasoningEffort());
        assertEquals("futureEffort", models.getFirst().supportedReasoningEfforts().getLast().reasoningEffort());
        var requests = output.toString().lines().map(mapper::readTree).toList();
        assertEquals("initialize", requests.get(0).path("method").asText());
        assertEquals("initialized", requests.get(1).path("method").asText());
        assertEquals("model/list", requests.get(2).path("method").asText());
        assertFalse(requests.get(2).path("params").path("includeHidden").asBoolean());
        assertEquals("page2", requests.get(3).path("params").path("cursor").asText());
    }

    @Test
    void rejectsRpcErrorsMalformedOutputEarlyExitAndEmptyCatalog() {
        for (String input : new String[]{"", "not-json\n", "[]\n", "{\"id\":9,\"result\":{}}\n",
                "{\"id\":1,\"error\":{\"message\":\"secret credential\"}}\n",
                INITIALIZED + "{\"id\":2,\"result\":{\"data\":[],\"nextCursor\":null}}\n"}) {
            var error = assertThrows(IllegalStateException.class,
                    () -> client.readCatalog(new StringReader(input), new StringWriter()));
            assertFalse(error.getMessage().contains("secret credential"));
        }
    }

    @Test
    void rejectsRepeatedCursorAndUnsupportedDefault() {
        String input = INITIALIZED
                + "{\"id\":2,\"result\":{\"data\":[],\"nextCursor\":\"same\"}}\n"
                + "{\"id\":3,\"result\":{\"data\":[],\"nextCursor\":\"same\"}}\n";
        assertThrows(IllegalStateException.class, () -> client.readCatalog(new StringReader(input), new StringWriter()));
        String invalid = model("m").replace("\"defaultReasoningEffort\":\"ultra\"", "\"defaultReasoningEffort\":\"missing\"");
        assertThrows(IllegalStateException.class, () -> client.readCatalog(new StringReader(INITIALIZED
                + "{\"id\":2,\"result\":{\"data\":[" + invalid + "]}}\n"), new StringWriter()));
    }

    @Test
    void limitsOutputBeforeAnUnboundedLineCanBeAllocated() {
        assertThrows(IllegalStateException.class, () -> client.readCatalog(
                new StringReader("x".repeat(4 * 1024 * 1024 + 1)), new StringWriter()));
    }

    @Test
    void cleansUpProcessOnSuccessAndFailure() throws Exception {
        for (String input : new String[]{INITIALIZED + "{\"id\":2,\"result\":{\"data\":[" + model("m") + "]}}\n", "broken\n"}) {
            Process process = process(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
            CodexModelCatalogClient spy = spy(client);
            doReturn(process).when(spy).startProcess(any(), any());
            if (input.startsWith(INITIALIZED)) assertEquals(1, spy.query(Path.of("codex"), Path.of(".")).size());
            else assertThrows(IllegalStateException.class, () -> spy.query(Path.of("codex"), Path.of(".")));
            verify(process).destroyForcibly();
            verify(process).waitFor(1, TimeUnit.SECONDS);
        }
    }

    @Test
    void timeoutTerminatesProcessAndDoesNotWaitForMoreOutput() throws Exception {
        PipedInputStream input = new PipedInputStream();
        PipedOutputStream feeder = new PipedOutputStream(input);
        Process process = process(input);
        doAnswer(invocation -> { feeder.close(); return process; }).when(process).destroyForcibly();
        CodexModelCatalogClient spy = spy(new CodexModelCatalogClient(mapper, 50));
        doReturn(process).when(spy).startProcess(any(), any());
        var error = assertThrows(IllegalStateException.class, () -> spy.query(Path.of("codex"), Path.of(".")));
        assertTrue(error.getMessage().contains("超时"));
        verify(process).destroyForcibly();
        input.close();
    }

    @Test
    void interruptionRestoresFlagAndCleansUp() throws Exception {
        PipedInputStream input = new PipedInputStream();
        PipedOutputStream feeder = new PipedOutputStream(input);
        Process process = process(input);
        doAnswer(invocation -> { feeder.close(); return process; }).when(process).destroyForcibly();
        CodexModelCatalogClient spy = spy(client);
        doReturn(process).when(spy).startProcess(any(), any());
        Thread.currentThread().interrupt();
        try {
            assertThrows(IllegalStateException.class, () -> spy.query(Path.of("codex"), Path.of(".")));
            assertTrue(Thread.currentThread().isInterrupted());
            verify(process).destroyForcibly();
        } finally {
            Thread.interrupted();
            input.close();
        }
    }

    private Process process(InputStream input) throws Exception {
        Process process = mock(Process.class);
        when(process.getInputStream()).thenReturn(input);
        when(process.getOutputStream()).thenReturn(new ByteArrayOutputStream());
        when(process.getErrorStream()).thenReturn(InputStream.nullInputStream());
        when(process.descendants()).thenAnswer(invocation -> Stream.empty());
        when(process.waitFor(anyLong(), any())).thenReturn(true);
        return process;
    }
}
