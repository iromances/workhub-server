package cn.aslight.workhub.mcp.ssh;

import cn.aslight.workhub.mcp.config.McpResourceCatalog.DatabaseTarget;
import cn.aslight.workhub.mcp.security.SecretResolver;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.LongSupplier;

/** 进程内共享堡垒机主连接；租约关闭只归还使用权。 */
public final class SshConnectionManager implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger(SshConnectionManager.class.getName());
    static final long IDLE_NANOS = Duration.ofHours(4).toNanos();
    private static volatile SshConnectionManager shared;
    private static boolean sharedClosed;
    private final Map<Endpoint, Slot> slots = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock lifecycle = new ReentrantReadWriteLock();
    private final Connector connector;
    private final LongSupplier clock;
    private final byte[] salt = new byte[32];
    private final ScheduledExecutorService reaper;
    private boolean closed;

    public static synchronized SshConnectionManager shared() {
        if (sharedClosed) {
            throw new IllegalStateException("堡垒机连接管理器已关闭");
        }
        if (shared == null) {
            shared = new SshConnectionManager(OpenSshConnection::open, System::nanoTime, true);
            Runtime.getRuntime().addShutdownHook(new Thread(SshConnectionManager::closeShared, "workhub-ssh-exit"));
        }
        return shared;
    }

    public static void closeShared() {
        SshConnectionManager manager;
        synchronized (SshConnectionManager.class) {
            sharedClosed = true;
            manager = shared;
        }
        if (manager != null) {
            manager.close();
        }
    }

    /** 不因更新配置而初始化连接管理器，更不会主动登录堡垒机。 */
    public static void invalidateShared(String host, int port, String username) {
        SshConnectionManager manager = shared;
        if (manager != null) {
            manager.invalidate(host, port, username);
        }
    }

    SshConnectionManager(Connector connector, LongSupplier clock, boolean scheduleCleanup) {
        this.connector = connector;
        this.clock = clock;
        new SecureRandom().nextBytes(salt);
        reaper = scheduleCleanup ? Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "workhub-ssh-idle-cleanup");
            thread.setDaemon(true);
            return thread;
        }) : null;
        if (reaper != null) {
            reaper.scheduleWithFixedDelay(this::reapSafely, 1, 1, TimeUnit.MINUTES);
        }
    }

    public Lease acquire(DatabaseTarget.SshTunnel config, String targetHost, int targetPort) throws IOException {
        Credentials credentials = credentials(config);
        Target target = new Target(targetHost, targetPort);
        String version = version(credentials);
        lifecycle.readLock().lock();
        try {
            if (closed) {
                throw new IOException("堡垒机连接管理器已关闭");
            }
            Slot slot = slots.computeIfAbsent(credentials.endpoint, ignored -> new Slot());
            synchronized (slot) {
                Entry entry = slot.current;
                if (entry != null && (!entry.version.equals(version) || expired(entry) || !entry.connection.healthy())) {
                    retire(slot, entry);
                    entry = null;
                }
                if (entry == null) {
                    // 合并同一批失败请求，避免认证错误时排队请求连续冲击堡垒机。
                    if (version.equals(slot.failedVersion) && clock.getAsLong() - slot.failedAt < TimeUnit.SECONDS.toNanos(8)) {
                        throw new IOException("堡垒机连接刚刚失败，请稍后重试");
                    }
                    try {
                        entry = new Entry(version, connector.open(credentials), clock.getAsLong());
                        slot.current = entry;
                        slot.failedVersion = null;
                        LOG.log(System.Logger.Level.INFO, "堡垒机共享连接已建立");
                    } catch (IOException ex) {
                        slot.failedVersion = version;
                        slot.failedAt = clock.getAsLong();
                        throw ex;
                    }
                }
                Integer port = entry.forwards.get(target);
                if (port == null) {
                    port = entry.connection.forward(target);
                    entry.forwards.put(target, port);
                }
                entry.users++;
                return new Lease(this, slot, entry, port);
            }
        } finally {
            lifecycle.readLock().unlock();
        }
    }

    public void invalidate(String host, int port, String username) {
        Slot slot = slots.get(new Endpoint(host, port <= 0 ? 22 : port, username));
        if (slot != null) {
            synchronized (slot) {
                if (slot.current != null) {
                    retire(slot, slot.current);
                    LOG.log(System.Logger.Level.INFO, "堡垒机共享连接因配置更新失效");
                }
                slot.failedVersion = null;
            }
        }
    }

    void reapIdle() {
        for (Slot slot : slots.values()) {
            synchronized (slot) {
                Entry entry = slot.current;
                if (entry != null && expired(entry)) {
                    retire(slot, entry);
                    LOG.log(System.Logger.Level.INFO, "堡垒机共享连接空闲超过四小时，已回收");
                }
            }
        }
    }

    private boolean expired(Entry entry) {
        return entry.users == 0 && clock.getAsLong() - entry.idleSince > IDLE_NANOS;
    }

    private void reapSafely() {
        try {
            reapIdle();
        } catch (RuntimeException ex) {
            LOG.log(System.Logger.Level.WARNING, "堡垒机空闲连接清理异常，将在下次扫描继续");
        }
    }

    private void retire(Slot slot, Entry entry) {
        slot.current = null;
        entry.retired = true;
        if (entry.users == 0) {
            dispose(entry);
        } else {
            slot.retired.add(entry);
        }
    }

    private void release(Slot slot, Entry entry) {
        synchronized (slot) {
            entry.users--;
            if (entry.users == 0) {
                entry.idleSince = clock.getAsLong();
                if (entry.retired) {
                    dispose(entry);
                    slot.retired.remove(entry);
                }
            }
        }
    }

    private void dispose(Entry entry) {
        if (!entry.disposed) {
            entry.disposed = true;
            try {
                entry.connection.close();
            } catch (RuntimeException ex) {
                LOG.log(System.Logger.Level.WARNING, "堡垒机共享连接资源清理失败");
            } finally {
                entry.forwards.clear();
            }
        }
    }

    @Override
    public void close() {
        lifecycle.writeLock().lock();
        try {
            if (closed) {
                return;
            }
            closed = true;
            if (reaper != null) {
                reaper.shutdownNow();
            }
            for (Slot slot : slots.values()) {
                synchronized (slot) {
                    if (slot.current != null) {
                        dispose(slot.current);
                        slot.current = null;
                    }
                    slot.retired.forEach(this::dispose);
                    slot.retired.clear();
                }
            }
            slots.clear();
        } finally {
            lifecycle.writeLock().unlock();
        }
    }

    private Credentials credentials(DatabaseTarget.SshTunnel config) {
        String password = new SecretResolver().resolve(config.password());
        return new Credentials(new Endpoint(config.bastionHost(), config.bastionPort() <= 0 ? 22 : config.bastionPort(),
                config.bastionUser()), password == null || password.isBlank() ? null : password,
                config.identityFile() == null || config.identityFile().isBlank() ? null : config.identityFile());
    }

    private String version(Credentials credentials) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt);
            add(digest, credentials.password);
            add(digest, credentials.identityFile);
            if (credentials.password == null && credentials.identityFile != null) {
                try (var input = Files.newInputStream(Path.of(credentials.identityFile))) {
                    byte[] bytes = new byte[4096];
                    int count;
                    while ((count = input.read(bytes)) != -1) {
                        digest.update(bytes, 0, count);
                    }
                } catch (IOException ex) {
                    throw new IOException("堡垒机私钥文件不可用");
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }

    private void add(MessageDigest digest, String value) {
        byte[] bytes = value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array());
        digest.update(bytes);
    }

    record Endpoint(String host, int port, String username) { }
    record Target(String host, int port) { }

    // 不生成包含密码的 record.toString，也不在共享注册表中长期保留明文凭据。
    static final class Credentials {
        final Endpoint endpoint;
        final String password;
        final String identityFile;

        Credentials(Endpoint endpoint, String password, String identityFile) {
            this.endpoint = endpoint;
            this.password = password;
            this.identityFile = identityFile;
        }
    }

    interface Connector { Connection open(Credentials credentials) throws IOException; }
    interface Connection extends AutoCloseable {
        boolean healthy();
        int forward(Target target) throws IOException;
        @Override void close();
    }

    private static final class Slot {
        Entry current;
        final List<Entry> retired = new ArrayList<>();
        String failedVersion;
        long failedAt;
    }

    private static final class Entry {
        final String version;
        final Connection connection;
        final Map<Target, Integer> forwards = new HashMap<>();
        int users;
        long idleSince;
        boolean retired;
        boolean disposed;

        Entry(String version, Connection connection, long idleSince) {
            this.version = version;
            this.connection = connection;
            this.idleSince = idleSince;
        }
    }

    public static final class Lease implements AutoCloseable {
        private final SshConnectionManager manager;
        private final Slot slot;
        private final Entry entry;
        private final int port;
        private boolean released;

        private Lease(SshConnectionManager manager, Slot slot, Entry entry, int port) {
            this.manager = manager;
            this.slot = slot;
            this.entry = entry;
            this.port = port;
        }

        public int port() { return port; }

        @Override
        public synchronized void close() {
            if (!released) {
                released = true;
                manager.release(slot, entry);
            }
        }
    }
}
