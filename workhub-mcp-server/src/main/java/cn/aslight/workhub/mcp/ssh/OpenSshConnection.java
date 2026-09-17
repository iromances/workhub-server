package cn.aslight.workhub.mcp.ssh;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** OpenSSH 主连接及控制命令。原始输出仅用于分类，不向调用方返回。 */
final class OpenSshConnection implements SshConnectionManager.Connection {
    private static final int TIMEOUT_SECONDS = 8;
    private final SshConnectionManager.Endpoint endpoint;
    private final Path directory;
    private final Path socket;
    private final Process process;
    private final Output output;
    private final ProcessLauncher launcher;
    private final long timeoutMillis;
    private final PortAllocator portAllocator;
    private boolean closed;

    private OpenSshConnection(SshConnectionManager.Endpoint endpoint, Path directory, Process process, Output output,
                              ProcessLauncher launcher, long timeoutMillis, PortAllocator portAllocator) {
        this.endpoint = endpoint;
        this.directory = directory;
        this.socket = directory.resolve("control");
        this.process = process;
        this.output = output;
        this.launcher = launcher;
        this.timeoutMillis = timeoutMillis;
        this.portAllocator = portAllocator;
    }

    static OpenSshConnection open(SshConnectionManager.Credentials credentials) throws IOException {
        return open(credentials, (command, environment) -> {
            ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
            builder.environment().putAll(environment);
            return builder.start();
        }, TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS), () -> {
            try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
                return listener.getLocalPort();
            }
        });
    }

    static OpenSshConnection open(SshConnectionManager.Credentials credentials, ProcessLauncher launcher,
                                  long timeoutMillis, PortAllocator portAllocator) throws IOException {
        // 控制 socket 路径受 Unix 长度限制；目录必须仅服务用户可访问。
        Path directory = Files.createTempDirectory(Path.of("/tmp"), "wh-ssh-",
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        OpenSshConnection connection = null;
        try {
            Process process;
            try {
                process = launcher.start(masterCommand(credentials, directory.resolve("control")),
                        credentials.password == null ? Map.of() : Map.of("SSHPASS", credentials.password));
            } catch (IOException ex) {
                throw new IOException(credentials.password == null ? "服务环境 SSH 命令不可用" : "服务环境 ssh/sshpass 命令不可用");
            }
            connection = new OpenSshConnection(credentials.endpoint, directory, process, new Output(process), launcher, timeoutMillis, portAllocator);
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
            while (System.nanoTime() < deadline) {
                if (!process.isAlive()) {
                    connection.output.await();
                    throw new IOException(classify(connection.output.text()));
                }
                if (Files.exists(connection.socket)) {
                    return connection;
                }
                Thread.sleep(25);
            }
            throw new IOException("SSH 隧道建立超时");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            if (connection != null) {
                connection.close();
            }
            throw new IOException("等待 SSH 隧道启动被中断");
        } catch (IOException | RuntimeException ex) {
            if (connection != null) {
                connection.close();
            }
            throw ex;
        } finally {
            if (connection == null) {
                cleanup(directory);
            }
        }
    }

    static List<String> masterCommand(SshConnectionManager.Credentials credentials, Path socket) {
        List<String> command = new ArrayList<>();
        if (credentials.password != null) {
            command.addAll(List.of("sshpass", "-e"));
        }
        command.addAll(List.of("ssh", "-N", "-n", "-p", String.valueOf(credentials.endpoint.port()),
                "-o", "ControlMaster=yes", "-o", "ControlPath=" + socket,
                "-o", "ControlPersist=no", "-o", "ForkAfterAuthentication=no",
                "-o", "ConnectTimeout=8", "-o", "ConnectionAttempts=1",
                "-o", "StrictHostKeyChecking=accept-new", "-o", "ExitOnForwardFailure=yes",
                "-o", "ServerAliveInterval=60", "-o", "ServerAliveCountMax=3"));
        if (credentials.password != null) {
            command.addAll(List.of("-o", "PreferredAuthentications=password,keyboard-interactive", "-o", "PubkeyAuthentication=no"));
        } else {
            command.addAll(List.of("-o", "BatchMode=yes", "-o", "IdentitiesOnly=yes"));
        }
        if (credentials.identityFile != null) {
            command.addAll(List.of("-i", credentials.identityFile));
        }
        command.addAll(List.of("--", credentials.endpoint.username() + "@" + credentials.endpoint.host()));
        return command;
    }

    @Override
    public boolean healthy() {
        if (closed || !process.isAlive() || !Files.exists(socket)) {
            return false;
        }
        try {
            return control("check", null).exitCode == 0;
        } catch (IOException ex) {
            return false;
        }
    }

    @Override
    public int forward(SshConnectionManager.Target target) throws IOException {
        String host = forwardHost(target.host());
        if (target.port() < 1 || target.port() > 65535) {
            throw new IOException("SSH 转发目标端口无效");
        }
        for (int attempt = 0; attempt < 3; attempt++) {
            int port = portAllocator.allocate();
            CommandResult result = control("forward", "127.0.0.1:" + port + ":" + host + ":" + target.port());
            if (result.exitCode == 0) {
                return port;
            }
            String message = result.output.toLowerCase(Locale.ROOT);
            if (!message.contains("address already in use") && !message.contains("cannot listen to port")) {
                throw new IOException("SSH 目标转发建立失败");
            }
        }
        throw new IOException("SSH 本地转发端口分配失败");
    }

    private static String forwardHost(String host) throws IOException {
        if (host == null || host.isBlank() || host.chars().anyMatch(Character::isWhitespace)) {
            throw new IOException("SSH 转发目标地址无效");
        }
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        if (host.contains("[") || host.contains("]") || host.contains("/") || host.chars().anyMatch(Character::isISOControl)) {
            throw new IOException("SSH 转发目标地址无效");
        }
        return host.contains(":") ? "[" + host + "]" : host;
    }

    private CommandResult control(String operation, String forwarding) throws IOException {
        Process control = launcher.start(controlCommand(endpoint, socket, operation, forwarding), Map.of());
        Output response = new Output(control);
        try {
            if (!control.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {
                throw new IOException("SSH 控制命令超时");
            }
            response.await();
            return new CommandResult(control.exitValue(), response.text());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("SSH 控制命令被中断");
        } finally {
            terminate(control);
            response.close();
        }
    }

    static List<String> controlCommand(SshConnectionManager.Endpoint endpoint, Path socket, String operation, String forwarding) {
        // -O 是纯控制请求；控制 socket 消失时直接失败，不能回退为独立登录。
        List<String> command = new ArrayList<>(List.of("ssh", "-S", socket.toString(), "-O", operation,
                "-p", String.valueOf(endpoint.port()), "-o", "BatchMode=yes"));
        if (forwarding != null) {
            command.addAll(List.of("-L", forwarding));
        }
        command.addAll(List.of("--", endpoint.username() + "@" + endpoint.host()));
        return command;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            // 控制通道也可关闭父进程意外退出后仍存活的 SSH 主连接。
            if (Files.exists(socket) && !Thread.currentThread().isInterrupted()) {
                try {
                    control("exit", null);
                } catch (IOException ignored) {
                    // 控制通道损坏时继续终止本应用创建的进程树。
                }
            }
        } finally {
            try {
                // sshpass 是父进程；同时终止其 ssh 子进程，避免遗留已认证连接。
                terminate(process);
            } finally {
                output.close();
                cleanup(directory);
            }
        }
    }

    private static void terminate(Process process) {
        List<ProcessHandle> descendants = process.descendants().toList();
        descendants.forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
        try {
            process.waitFor(1, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static void cleanup(Path directory) {
        try {
            Files.deleteIfExists(directory.resolve("control"));
            Files.deleteIfExists(directory);
        } catch (IOException ex) {
            System.getLogger(OpenSshConnection.class.getName()).log(System.Logger.Level.WARNING, "SSH 临时控制目录清理失败");
        }
    }

    private static String classify(String output) {
        String normalized = output.toLowerCase(Locale.ROOT);
        if (normalized.contains("permission denied") || normalized.contains("authentication failed")) {
            return "堡垒机认证失败";
        }
        if (normalized.contains("timed out")) {
            return "SSH 隧道建立超时";
        }
        if (normalized.contains("connection refused")) {
            return "堡垒机连接被拒绝";
        }
        if (normalized.contains("could not resolve hostname")) {
            return "堡垒机地址无法解析";
        }
        return "SSH 隧道建立失败";
    }

    private record CommandResult(int exitCode, String output) { }

    interface ProcessLauncher {
        Process start(List<String> command, Map<String, String> environment) throws IOException;
    }

    interface PortAllocator { int allocate() throws IOException; }

    /** 持续排空长连接输出，仅保留前 8 KiB，避免管道填满阻塞 ssh。 */
    private static final class Output implements AutoCloseable {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final InputStream input;
        private final Thread reader;

        Output(Process process) {
            input = process.getInputStream();
            reader = Thread.ofVirtual().name("workhub-ssh-output").start(() -> {
                try {
                    byte[] buffer = new byte[2048];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        synchronized (bytes) {
                            bytes.write(buffer, 0, Math.min(count, 8192 - bytes.size()));
                        }
                    }
                } catch (IOException ignored) {
                    // 进程销毁会关闭流。
                }
            });
        }

        String text() {
            synchronized (bytes) {
                return bytes.toString(StandardCharsets.UTF_8);
            }
        }

        void await() throws InterruptedException { reader.join(1000); }

        @Override
        public void close() {
            try {
                input.close();
            } catch (IOException ignored) {
                // 不覆盖原始业务异常。
            }
        }
    }
}
