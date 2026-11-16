package cn.aslight.workhub.service.ai;

import cn.aslight.workhub.model.ai.ZCodeModelCatalogRequest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ZCodeModelCatalogServiceTest {
    @Test void timeoutAndEarlyExitReclaimProcessAndPrivateFiles() throws Exception {
        for (String command : new String[]{"/bin/sleep", "/usr/bin/true"}) {
            var client = mock(ZCodeCliClient.class);
            var builder = mock(ProcessBuilder.class);
            var process = command.endsWith("sleep") ? new ProcessBuilder(command, "10").start() : new ProcessBuilder(command).start();
            var directory = new AtomicReference<Path>();
            when(client.prepareBuilder(any(), any(), eq(false))).thenAnswer(call -> {
                directory.set(call.getArgument(1));
                Files.writeString(directory.get().resolve("provider.json"), "fixture secret");
                return builder;
            });
            when(builder.start()).thenReturn(process);
            var service = new ZCodeModelCatalogService(client, new ObjectMapper(), 100);
            try {
                var error = assertThrows(IllegalStateException.class, () -> service.query(
                        new ZCodeModelCatalogRequest("zcode", null, null, null, true)));
                assertFalse(error.getMessage().contains("fixture secret"));
                assertFalse(process.isAlive());
                assertFalse(Files.exists(directory.get()));
            } finally { process.destroyForcibly(); }
        }
    }
}
