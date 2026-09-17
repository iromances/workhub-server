package cn.aslight.workhub.mcp.ssh;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class OpenSshConnectionTest {
    private final SshConnectionManager.Credentials credentials = new SshConnectionManager.Credentials(
            new SshConnectionManager.Endpoint("bastion", 22, "user"), "secret-password", null);

    @Test
    void usesPrivateSocketHeartbeatsEnvironmentPasswordAndControlOnlyForwarding() throws Exception {
        FakeLauncher launcher = new FakeLauncher();
        try (var connection = OpenSshConnection.open(credentials, launcher, 100, () -> 12345)) {
            assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(launcher.socket.getParent())));
            assertEquals("secret-password", launcher.environments.getFirst().get("SSHPASS"));
            assertFalse(launcher.commands.toString().contains("secret-password"));
            assertTrue(launcher.commands.getFirst().contains("ServerAliveInterval=60"));
            assertTrue(launcher.commands.getFirst().contains("ServerAliveCountMax=3"));
            assertTrue(connection.healthy());
            int port = connection.forward(new SshConnectionManager.Target("database", 3306));
            List<String> forward = launcher.commands.getLast();
            assertTrue(forward.contains("-O"));
            assertTrue(forward.contains("forward"));
            assertTrue(forward.contains("127.0.0.1:" + port + ":database:3306"));
            assertEquals(Map.of(), launcher.environments.getLast());
            assertEquals(0, launcher.master.destroyed);
        }
        assertEquals(1, launcher.master.destroyed);
        assertFalse(Files.exists(launcher.socket.getParent()));
    }

    @Test
    void failedAuthenticationIsClassifiedAndProcessAndDirectoryAreCleaned() {
        FakeLauncher launcher = new FakeLauncher();
        launcher.createSocket = false;
        launcher.master = new FakeProcess(false, 255, "Permission denied (password). private diagnostic");
        IOException error = assertThrows(IOException.class, () -> OpenSshConnection.open(credentials, launcher, 100, () -> 12345));
        assertEquals("堡垒机认证失败", error.getMessage());
        assertFalse(error.getMessage().contains("private diagnostic"));
        assertFalse(Files.exists(launcher.socket.getParent()));
        assertEquals(1, launcher.master.destroyed);
    }

    @Test
    void startupTimeoutCleansProcessAndPrivateDirectory() {
        FakeLauncher launcher = new FakeLauncher();
        launcher.createSocket = false;
        IOException error = assertThrows(IOException.class, () -> OpenSshConnection.open(credentials, launcher, 5, () -> 12345));
        assertEquals("SSH 隧道建立超时", error.getMessage());
        assertEquals(1, launcher.master.destroyed);
        assertFalse(Files.exists(launcher.socket.getParent()));
    }

    @Test
    void launchFailureCleansDirectoryAndDoesNotExposeRawException() {
        FakeLauncher launcher = new FakeLauncher();
        launcher.failStart = true;
        IOException error = assertThrows(IOException.class, () -> OpenSshConnection.open(credentials, launcher, 100, () -> 12345));
        assertFalse(error.getMessage().contains("private diagnostic"));
        assertFalse(Files.exists(launcher.socket.getParent()));
    }

    @Test
    void controlTimeoutDoesNotAuthenticateAgainAndKillsOnlyControlProcess() throws Exception {
        FakeLauncher launcher = new FakeLauncher();
        try (var connection = OpenSshConnection.open(credentials, launcher, 100, () -> 12345)) {
            launcher.control = new FakeProcess(true, 0, "");
            assertThrows(IOException.class, () -> connection.forward(new SshConnectionManager.Target("db", 3306)));
            assertEquals(2, launcher.commands.size());
            assertEquals(1, launcher.control.destroyed);
            assertEquals(0, launcher.master.destroyed);
        }
    }

    @Test
    void portCollisionRetriesAreBoundedAndOtherForwardErrorsAreNotRetried() throws Exception {
        FakeLauncher launcher = new FakeLauncher();
        try (var connection = OpenSshConnection.open(credentials, launcher, 100, () -> 12345)) {
            launcher.control = new FakeProcess(false, 1, "Address already in use");
            assertThrows(IOException.class, () -> connection.forward(new SshConnectionManager.Target("db", 3306)));
            assertEquals(4, launcher.commands.size());
            launcher.control = new FakeProcess(false, 1, "administratively prohibited");
            assertThrows(IOException.class, () -> connection.forward(new SshConnectionManager.Target("db", 3306)));
            assertEquals(5, launcher.commands.size());
        }
    }

    @Test
    void deadMasterAndMissingControlSocketAreUnhealthy() throws Exception {
        FakeLauncher launcher = new FakeLauncher();
        try (var connection = OpenSshConnection.open(credentials, launcher, 100, () -> 12345)) {
            Files.delete(launcher.socket);
            assertFalse(connection.healthy());
            Files.createFile(launcher.socket);
            launcher.master.alive = false;
            assertFalse(connection.healthy());
            assertEquals(1, launcher.commands.size());
        }
    }

    @Test
    void invalidTargetDoesNotIssueControlCommandAndIpv6UsesBrackets() throws Exception {
        FakeLauncher launcher = new FakeLauncher();
        try (var connection = OpenSshConnection.open(credentials, launcher, 100, () -> 12345)) {
            assertThrows(IOException.class, () -> connection.forward(new SshConnectionManager.Target("bad host", 3306)));
            assertThrows(IOException.class, () -> connection.forward(new SshConnectionManager.Target("db", 0)));
            assertEquals(1, launcher.commands.size());
            int port = connection.forward(new SshConnectionManager.Target("::1", 3306));
            assertTrue(launcher.commands.getLast().contains("127.0.0.1:" + port + ":[::1]:3306"));
        }
    }

    @Test
    void privateKeyAuthenticationDoesNotUsePasswordHelper() {
        var keyCredentials = new SshConnectionManager.Credentials(credentials.endpoint, null, "/private/key");
        var command = OpenSshConnection.masterCommand(keyCredentials, Path.of("/tmp/control"));
        assertFalse(command.contains("sshpass"));
        assertTrue(command.contains("BatchMode=yes"));
        assertTrue(command.contains("IdentitiesOnly=yes"));
        assertTrue(command.contains("/private/key"));
    }

    @Test
    void interruptedStartupPreservesInterruptAndCleansResources() {
        FakeLauncher launcher = new FakeLauncher();
        launcher.createSocket = false;
        Thread.currentThread().interrupt();
        try {
            assertThrows(IOException.class, () -> OpenSshConnection.open(credentials, launcher, 100, () -> 12345));
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(1, launcher.master.destroyed);
            assertFalse(Files.exists(launcher.socket.getParent()));
        } finally {
            Thread.interrupted();
        }
    }

    private static final class FakeLauncher implements OpenSshConnection.ProcessLauncher {
        final List<List<String>> commands = new ArrayList<>();
        final List<Map<String, String>> environments = new ArrayList<>();
        FakeProcess master = new FakeProcess(true, 0, "");
        FakeProcess control = new FakeProcess(false, 0, "");
        Path socket;
        boolean createSocket = true;
        boolean failStart;

        @Override
        public Process start(List<String> command, Map<String, String> environment) throws IOException {
            commands.add(List.copyOf(command));
            environments.add(Map.copyOf(environment));
            if (command.contains("-N")) {
                String option = command.stream().filter(c -> c.startsWith("ControlPath=")).findFirst().orElseThrow();
                socket = Path.of(option.substring("ControlPath=".length()));
                if (failStart) throw new IOException("private diagnostic");
                if (createSocket) Files.createFile(socket);
                return master;
            }
            // 每次命令模拟新进程/新输出流。
            control = new FakeProcess(control.alive, control.exit, control.text);
            return control;
        }
    }

    private static final class FakeProcess extends Process {
        boolean alive;
        final int exit;
        final String text;
        final InputStream input;
        int destroyed;

        FakeProcess(boolean alive, int exit, String text) {
            this.alive = alive;
            this.exit = exit;
            this.text = text;
            input = new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
        }
        @Override public OutputStream getOutputStream() { return OutputStream.nullOutputStream(); }
        @Override public InputStream getInputStream() { return input; }
        @Override public InputStream getErrorStream() { return InputStream.nullInputStream(); }
        @Override public int waitFor() { return exit; }
        @Override public boolean waitFor(long timeout, TimeUnit unit) { return !alive; }
        @Override public int exitValue() { return exit; }
        @Override public boolean isAlive() { return alive; }
        @Override public void destroy() { alive = false; destroyed++; }
        @Override public Process destroyForcibly() { destroy(); return this; }
        @Override public Stream<ProcessHandle> descendants() { return Stream.empty(); }
    }
}
