package cn.aslight.workhub.service.ai;

import cn.aslight.workhub.model.ai.CodexModelCatalogRequest;
import cn.aslight.workhub.model.ai.CodexModelCatalogResponse.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.time.*;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CodexModelCatalogServiceTest {
    @TempDir Path temp;
    private final CodexModelCatalogClient client = mock(CodexModelCatalogClient.class);
    private final Clock clock = mock(Clock.class);
    private CodexModelCatalogService service;
    private Path executable;
    private final Instant now = Instant.parse("2026-09-23T10:00:00Z");
    private final List<Model> models = List.of(new Model("new-model", "New Model", true, "ultra",
            List.of(new ReasoningEffort("ultra", "Deep"))));

    @BeforeEach
    void setup() throws Exception {
        executable = Files.createFile(temp.resolve("codex"));
        assertTrue(executable.toFile().setExecutable(true));
        when(clock.instant()).thenReturn(now);
        when(client.query(any(), any())).thenReturn(models);
        service = new CodexModelCatalogService(client, clock);
    }

    private CodexModelCatalogRequest request(boolean refresh) {
        return new CodexModelCatalogRequest(executable.toString(), temp.toString(), refresh);
    }

    @Test
    void cacheExpiresAndManualRefreshBypassesIt() {
        assertFalse(service.query(request(false)).cached());
        assertTrue(service.query(request(false)).cached());
        when(clock.instant()).thenReturn(now.plusSeconds(59));
        assertTrue(service.query(request(false)).cached());
        when(clock.instant()).thenReturn(now.plusSeconds(60));
        assertFalse(service.query(request(false)).cached());
        assertFalse(service.query(request(true)).cached());
        verify(client, times(3)).query(any(), any());
    }

    @Test
    void differentDirectoriesAndCommandsDoNotShareCache() throws Exception {
        service.query(request(false));
        Path other = Files.createDirectory(temp.resolve("other"));
        service.query(new CodexModelCatalogRequest(executable.toString(), other.toString(), false));
        Path otherExecutable = Files.createFile(other.resolve("codex"));
        assertTrue(otherExecutable.toFile().setExecutable(true));
        service.query(new CodexModelCatalogRequest(otherExecutable.toString(), temp.toString(), false));
        verify(client, times(3)).query(any(), any());
    }

    @Test
    void failedRefreshDoesNotReturnOrCacheStaleData() {
        service.query(request(false));
        when(client.query(any(), any())).thenThrow(new IllegalStateException("failed"));
        assertThrows(IllegalStateException.class, () -> service.query(request(true)));
        assertThrows(IllegalStateException.class, () -> service.query(request(false)));
        doReturn(models).when(client).query(any(), any());
        assertFalse(service.query(request(false)).cached());
    }

    @Test
    void rejectsShellCommandsAndMissingDirectoryBeforeStartingProcess() {
        assertThrows(IllegalArgumentException.class, () -> service.query(new CodexModelCatalogRequest("codex --help", temp.toString(), false)));
        assertThrows(IllegalArgumentException.class, () -> service.query(new CodexModelCatalogRequest("sh", temp.toString(), false)));
        assertThrows(IllegalArgumentException.class, () -> service.query(new CodexModelCatalogRequest(executable.toString(), temp.resolve("missing").toString(), false)));
        verifyNoInteractions(client);
    }

    @Test
    void concurrentQueriesForSameEnvironmentShareOneProcess() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        when(client.query(any(), any())).thenAnswer(invocation -> {
            entered.countDown();
            assertTrue(finish.await(2, TimeUnit.SECONDS));
            return models;
        });
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> service.query(request(false)));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            var second = executor.submit(() -> service.query(request(false)));
            finish.countDown();
            assertEquals(models, first.get().models());
            assertEquals(models, second.get().models());
            verify(client).query(any(), any());
        }
    }
}
