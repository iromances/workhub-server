package cn.aslight.workhub.mcp.ssh;

import cn.aslight.workhub.mcp.config.McpResourceCatalog.DatabaseTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class SshConnectionManagerTest {
    private final AtomicLong time = new AtomicLong();
    private final List<FakeConnection> connections = new CopyOnWriteArrayList<>();
    private final DatabaseTarget.SshTunnel bastion = config("bastion-a", "password");

    private SshConnectionManager manager() {
        return new SshConnectionManager(credentials -> {
            FakeConnection connection = new FakeConnection();
            connections.add(connection);
            return connection;
        }, time::get, false);
    }

    @Test
    void alternatingConfigurationsOnSameEndpointKeepTheirOwnConnections() throws Exception {
        var first = new DatabaseTarget.SshTunnel("bastion-a", 22, "user", "first", null, 1L);
        var second = new DatabaseTarget.SshTunnel("bastion-a", 22, "user", "second", null, 2L);
        try (var manager = manager()) {
            for (int i = 0; i < 3; i++) {
                manager.acquire(first, "consumer-db", 3306).close();
                manager.acquire(second, "logistics-db", 3306).close();
            }
            assertEquals(2, connections.size());
            assertTrue(connections.stream().allMatch(c -> c.closes.get() == 0));
            assertTrue(connections.stream().allMatch(c -> c.forwards.get() == 1));
        }
        assertTrue(connections.stream().allMatch(c -> c.closes.get() == 1));
    }

    @Test
    void differentConfigurationsWithIdenticalCredentialsStillRemainSeparate() throws Exception {
        var first = new DatabaseTarget.SshTunnel("bastion-a", 22, "user", "password", null, 1L);
        var second = new DatabaseTarget.SshTunnel("bastion-a", 22, "user", "password", null, 2L);
        try (var manager = manager()) {
            manager.acquire(first, "database", 3306).close();
            manager.acquire(second, "database", 3306).close();
            assertEquals(2, connections.size());
        }
    }

    @Test
    void savedConfigurationDoesNotShareConnectionWithLegacyTunnel() throws Exception {
        var saved = new DatabaseTarget.SshTunnel("bastion-a", 22, "user", "password", null, 1L);
        try (var manager = manager()) {
            manager.acquire(saved, "database", 3306).close();
            manager.acquire(bastion, "database", 3306).close();
            manager.acquire(saved, "database", 3306).close();
            assertEquals(2, connections.size());
            assertTrue(connections.stream().allMatch(c -> c.closes.get() == 0));
        }
    }

    @Test
    void sharesOneConnectionAcrossTargetsAndReturnsLeaseWithoutDisconnecting() throws Exception {
        var bastion = new DatabaseTarget.SshTunnel("bastion-a", 22, "user", "password", null, 1L);
        try (var manager = manager()) {
            int databasePort;
            try (var lease = manager.acquire(bastion, "database", 3306)) {
                databasePort = lease.port();
            }
            try (var database = manager.acquire(bastion, "database", 3306);
                 var server = manager.acquire(bastion, "server", 22)) {
                assertEquals(databasePort, database.port());
                assertNotEquals(database.port(), server.port());
                assertEquals(1, connections.size());
                assertEquals(2, connections.getFirst().forwards.get());
                assertEquals(0, connections.getFirst().closes.get());
            }
        }
        assertEquals(1, connections.getFirst().closes.get());
    }

    @Test
    void concurrentFirstRequestsAuthenticateAndForwardOnlyOnce() throws Exception {
        var bastion = new DatabaseTarget.SshTunnel("bastion-a", 22, "user", "password", null, 1L);
        try (var manager = manager(); var executor = Executors.newFixedThreadPool(12)) {
            CountDownLatch start = new CountDownLatch(1);
            var tasks = new ArrayList<java.util.concurrent.Future<Integer>>();
            for (int i = 0; i < 24; i++) {
                tasks.add(executor.submit(() -> {
                    start.await();
                    try (var lease = manager.acquire(bastion, "database", 3306)) {
                        return lease.port();
                    }
                }));
            }
            start.countDown();
            for (var task : tasks) {
                assertEquals(10001, task.get(3, TimeUnit.SECONDS));
            }
            assertEquals(1, connections.size());
            assertEquals(1, connections.getFirst().forwards.get());
        }
    }

    @Test
    void invalidatingConfigurationKeepsOtherIdAndLegacyConnectionsAlive() throws Exception {
        var first = new DatabaseTarget.SshTunnel("bastion-a", 22, "user", "first", null, 1L);
        var moved = new DatabaseTarget.SshTunnel("bastion-b", 22, "user", "first", null, 1L);
        var second = new DatabaseTarget.SshTunnel("bastion-a", 22, "user", "second", null, 2L);
        try (var manager = manager()) {
            var active = manager.acquire(first, "consumer-db", 3306);
            manager.acquire(moved, "consumer-db", 3306).close();
            manager.acquire(second, "logistics-db", 3306).close();
            manager.acquire(bastion, "legacy-db", 3306).close();

            manager.invalidate(1L);
            assertEquals(0, connections.get(0).closes.get());
            assertEquals(1, connections.get(1).closes.get());
            assertEquals(0, connections.get(2).closes.get());
            assertEquals(0, connections.get(3).closes.get());
            manager.acquire(second, "logistics-db", 3306).close();
            manager.acquire(bastion, "legacy-db", 3306).close();
            assertEquals(4, connections.size());

            manager.acquire(first, "consumer-db", 3306).close();
            assertEquals(5, connections.size());
            active.close();
            assertEquals(1, connections.get(0).closes.get());
        }
    }

    @Test
    void failedAuthenticationOnOneIdDoesNotSuppressAnotherIdOnSameEndpoint() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        var first = new DatabaseTarget.SshTunnel("bastion-a", 22, "user", "bad", null, 1L);
        var second = new DatabaseTarget.SshTunnel("bastion-a", 22, "user", "good", null, 2L);
        try (var manager = new SshConnectionManager(credentials -> {
            attempts.incrementAndGet();
            if ("bad".equals(credentials.password)) {
                throw new IOException("堡垒机认证失败");
            }
            return new FakeConnection();
        }, time::get, false)) {
            assertThrows(IOException.class, () -> manager.acquire(first, "db", 3306));
            assertThrows(IOException.class, () -> manager.acquire(first, "db", 3306));
            manager.acquire(second, "db", 3306).close();
            manager.acquire(second, "db", 3306).close();
            assertEquals(2, attempts.get());
            manager.invalidate(1L);
            assertThrows(IOException.class, () -> manager.acquire(first, "db", 3306));
            assertEquals(3, attempts.get());
            manager.acquire(second, "db", 3306).close();
            assertEquals(3, attempts.get());
        }
    }

    @Test
    void credentialChangeAndDeadConnectionOnlyReplaceTheirOwnConfiguration() throws Exception {
        var first = new DatabaseTarget.SshTunnel("bastion-a", 22, "user", "first", null, 1L);
        var changed = new DatabaseTarget.SshTunnel("bastion-a", 22, "user", "changed", null, 1L);
        var second = new DatabaseTarget.SshTunnel("bastion-a", 22, "user", "second", null, 2L);
        try (var manager = manager()) {
            manager.acquire(first, "db", 3306).close();
            manager.acquire(second, "db", 3306).close();
            manager.acquire(changed, "db", 3306).close();
            assertEquals(3, connections.size());
            assertEquals(1, connections.get(0).closes.get());
            assertEquals(0, connections.get(1).closes.get());

            connections.get(2).healthy = false;
            manager.acquire(changed, "db", 3306).close();
            manager.acquire(second, "db", 3306).close();
            assertEquals(4, connections.size());
            assertEquals(1, connections.get(2).closes.get());
            assertEquals(0, connections.get(1).closes.get());
        }
    }

    @Test
    void slowConfigurationDoesNotBlockAnotherConfigurationOnSameEndpoint() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        var slowConfig = new DatabaseTarget.SshTunnel("bastion-a", 22, "user", "slow-password", null, 1L);
        var fastConfig = new DatabaseTarget.SshTunnel("bastion-a", 22, "user", "password", null, 2L);
        try (var manager = new SshConnectionManager(credentials -> {
            if ("slow-password".equals(credentials.password)) {
                entered.countDown();
                try {
                    if (!resume.await(3, TimeUnit.SECONDS)) throw new IOException("test timeout");
                } catch (InterruptedException ex) {
                    throw new IOException(ex);
                }
            }
            return new FakeConnection();
        }, time::get, false); var executor = Executors.newFixedThreadPool(2)) {
            var slow = executor.submit(() -> manager.acquire(slowConfig, "db", 3306));
            assertTrue(entered.await(1, TimeUnit.SECONDS));
            try {
                var fast = executor.submit(() -> manager.acquire(fastConfig, "db", 3306));
                fast.get(1, TimeUnit.SECONDS).close();
            } finally {
                resume.countDown();
            }
            slow.get(1, TimeUnit.SECONDS).close();
        }
    }

    @Test
    void expiresOnlyAfterFourHoursSinceLastReleaseAndCloseIsIdempotent() throws Exception {
        try (var manager = manager()) {
            var first = manager.acquire(bastion, "db", 3306);
            var second = manager.acquire(bastion, "db", 3306);
            time.set(Duration.ofHours(5).toNanos());
            first.close();
            first.close();
            manager.reapIdle();
            assertEquals(0, connections.getFirst().closes.get());
            second.close();
            time.addAndGet(SshConnectionManager.IDLE_NANOS - 1);
            manager.reapIdle();
            assertEquals(0, connections.getFirst().closes.get());
            time.incrementAndGet();
            manager.reapIdle();
            assertEquals(0, connections.getFirst().closes.get());
            time.incrementAndGet();
            manager.reapIdle();
            assertEquals(1, connections.getFirst().closes.get());
            manager.acquire(bastion, "db", 3306).close();
            assertEquals(2, connections.size());
        }
    }

    @Test
    void newBusinessResetsIdleClockAndCrossingMidnightDoesNotReconnect() throws Exception {
        try (var manager = manager()) {
            time.set(Duration.ofHours(23).toNanos());
            manager.acquire(bastion, "db", 3306).close();
            time.addAndGet(Duration.ofHours(3).toNanos());
            var active = manager.acquire(bastion, "db", 3306);
            time.addAndGet(Duration.ofHours(10).toNanos());
            manager.reapIdle();
            assertEquals(0, connections.getFirst().closes.get());
            active.close();
            time.addAndGet(SshConnectionManager.IDLE_NANOS);
            manager.reapIdle();
            assertEquals(1, connections.size());
            assertEquals(0, connections.getFirst().closes.get());
        }
    }

    @Test
    void acquireDoesNotReuseExpiredConnectionWhenReaperHasNotRun() throws Exception {
        try (var manager = manager()) {
            manager.acquire(bastion, "db", 3306).close();
            time.addAndGet(SshConnectionManager.IDLE_NANOS + 1);
            manager.acquire(bastion, "db", 3306).close();
            assertEquals(2, connections.size());
            assertEquals(1, connections.getFirst().closes.get());
        }
    }

    @Test
    void healthChecksDoNotRefreshBusinessIdleTime() throws Exception {
        try (var manager = manager()) {
            manager.acquire(bastion, "db", 3306).close();
            time.addAndGet(Duration.ofHours(3).toNanos());
            assertTrue(connections.getFirst().healthy());
            time.addAndGet(Duration.ofHours(2).toNanos());
            manager.reapIdle();
            assertEquals(1, connections.getFirst().closes.get());
        }
    }

    @Test
    void deadConnectionIsReplacedOnNextRequestWithoutReplayingExistingWork() throws Exception {
        try (var manager = manager()) {
            var active = manager.acquire(bastion, "db", 3306);
            connections.getFirst().healthy = false;
            manager.acquire(bastion, "db", 3306).close();
            assertEquals(2, connections.size());
            assertEquals(1, connections.getFirst().forwards.get());
            active.close();
            assertEquals(1, connections.getFirst().closes.get());
        }
    }

    @Test
    void invalidateDrainsActiveLeaseAndRebuildsEvenWithSameCredentials() throws Exception {
        var bastion = new DatabaseTarget.SshTunnel("bastion-a", 22, "user", "password", null, 1L);
        try (var manager = manager()) {
            var active = manager.acquire(bastion, "db", 3306);
            manager.invalidate(1L);
            assertEquals(0, connections.getFirst().closes.get());
            manager.acquire(bastion, "db", 3306).close();
            assertEquals(2, connections.size());
            active.close();
            assertEquals(1, connections.getFirst().closes.get());
        }
    }

    @Test
    void passwordChangeAndPrivateKeyContentChangeCannotReuseOldAuthentication(@TempDir Path directory) throws Exception {
        try (var manager = manager()) {
            manager.acquire(bastion, "db", 3306).close();
            manager.acquire(config("bastion-a", "changed"), "db", 3306).close();
            assertEquals(2, connections.size());
            Path key = directory.resolve("key");
            Files.writeString(key, "first-key");
            var config = new DatabaseTarget.SshTunnel("bastion-a", 22, "user", null, key.toString());
            manager.acquire(config, "db", 3306).close();
            Files.writeString(key, "other-key");
            manager.acquire(config, "db", 3306).close();
            assertEquals(4, connections.size());
            Files.delete(key);
            assertThrows(IOException.class, () -> manager.acquire(config, "db", 3306));
        }
    }

    @Test
    void failedAuthenticationIsNotRetriedByQueuedRequestsOrInBackground() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        try (var manager = new SshConnectionManager(credentials -> {
            attempts.incrementAndGet();
            throw new IOException("堡垒机认证失败");
        }, time::get, false)) {
            for (int i = 0; i < 10; i++) {
                assertThrows(IOException.class, () -> manager.acquire(bastion, "db", 3306));
            }
            manager.reapIdle();
            assertEquals(1, attempts.get());
            time.addAndGet(Duration.ofSeconds(8).toNanos());
            assertThrows(IOException.class, () -> manager.acquire(bastion, "db", 3306));
            assertEquals(2, attempts.get());
        }
    }

    @Test
    void failedForwardDoesNotLeakLeaseOrDestroyOtherTargets() throws Exception {
        try (var manager = manager()) {
            var existing = manager.acquire(bastion, "db", 3306);
            connections.getFirst().failForward = true;
            assertThrows(IOException.class, () -> manager.acquire(bastion, "bad", 3306));
            try (var reused = manager.acquire(bastion, "db", 3306)) {
                assertEquals(existing.port(), reused.port());
            }
            existing.close();
            time.addAndGet(SshConnectionManager.IDLE_NANOS + 1);
            manager.reapIdle();
            assertEquals(1, connections.getFirst().closes.get());
        }
    }

    @Test
    void concurrentReapCannotCloseAnActiveLease() throws Exception {
        try (var manager = manager(); var executor = Executors.newFixedThreadPool(2)) {
            manager.acquire(bastion, "db", 3306).close();
            time.set(SshConnectionManager.IDLE_NANOS + 1);
            var acquire = executor.submit(() -> manager.acquire(bastion, "db", 3306));
            var reap = executor.submit(manager::reapIdle);
            var lease = acquire.get(1, TimeUnit.SECONDS);
            reap.get(1, TimeUnit.SECONDS);
            assertEquals(0, connections.getLast().closes.get());
            lease.close();
        }
    }

    @Test
    void shutdownClosesActiveAndRetiredConnectionsAndRejectsNewLeases() throws Exception {
        var bastion = new DatabaseTarget.SshTunnel("bastion-a", 22, "user", "password", null, 1L);
        var manager = manager();
        var first = manager.acquire(bastion, "db", 3306);
        manager.invalidate(1L);
        var second = manager.acquire(bastion, "db", 3306);
        manager.close();
        manager.close();
        first.close();
        second.close();
        assertTrue(connections.stream().allMatch(c -> c.closes.get() == 1));
        assertThrows(IOException.class, () -> manager.acquire(bastion, "db", 3306));
    }

    @Test
    void cleanupFailureDoesNotPreventOtherConnectionsClosing() throws Exception {
        var manager = manager();
        manager.acquire(bastion, "db", 3306).close();
        manager.acquire(config("bastion-b", "password"), "db", 3306).close();
        connections.getFirst().failClose = true;
        assertDoesNotThrow(manager::close);
        assertTrue(connections.stream().allMatch(c -> c.closes.get() == 1));
    }

    private DatabaseTarget.SshTunnel config(String host, String password) {
        return new DatabaseTarget.SshTunnel(host, 22, "user", password, null);
    }

    private static class FakeConnection implements SshConnectionManager.Connection {
        final AtomicInteger forwards = new AtomicInteger();
        final AtomicInteger closes = new AtomicInteger();
        boolean healthy = true;
        boolean failForward;
        boolean failClose;
        @Override public boolean healthy() { return healthy; }
        @Override public int forward(SshConnectionManager.Target target) throws IOException {
            if (failForward) throw new IOException("forward failed");
            return 10000 + forwards.incrementAndGet();
        }
        @Override public void close() {
            closes.incrementAndGet();
            if (failClose) throw new IllegalStateException("cleanup failed");
        }
    }
}
