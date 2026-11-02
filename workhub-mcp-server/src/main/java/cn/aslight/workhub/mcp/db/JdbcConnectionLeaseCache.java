package cn.aslight.workhub.mcp.db;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.LongSupplier;

/** 进程内共享目标库连接；每个连接键只保留一个空闲连接，凭据变化或空闲超时后重建。 */
public final class JdbcConnectionLeaseCache implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger(JdbcConnectionLeaseCache.class.getName());
    static final long IDLE_NANOS = Duration.ofMinutes(5).toNanos();
    private static volatile JdbcConnectionLeaseCache shared;
    private static boolean sharedClosed;
    private final Map<String, Slot> slots = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock lifecycle = new ReentrantReadWriteLock();
    private final ConnectionFactory connectionFactory;
    private final LongSupplier clock;
    private final byte[] salt = new byte[32];
    private final ScheduledExecutorService reaper;
    private boolean closed;

    public static synchronized JdbcConnectionLeaseCache shared() {
        if (sharedClosed) {
            throw new IllegalStateException("目标库连接缓存已关闭");
        }
        if (shared == null) {
            shared = new JdbcConnectionLeaseCache(
                    (url, username, password) -> DriverManager.getConnection(url, username, password),
                    System::nanoTime,
                    true);
            Runtime.getRuntime().addShutdownHook(new Thread(JdbcConnectionLeaseCache::closeShared, "workhub-jdbc-cache-exit"));
        }
        return shared;
    }

    public static void closeShared() {
        JdbcConnectionLeaseCache cache;
        synchronized (JdbcConnectionLeaseCache.class) {
            sharedClosed = true;
            cache = shared;
        }
        if (cache != null) {
            cache.close();
        }
    }

    JdbcConnectionLeaseCache(ConnectionFactory connectionFactory, LongSupplier clock, boolean scheduleCleanup) {
        this.connectionFactory = connectionFactory;
        this.clock = clock;
        new SecureRandom().nextBytes(salt);
        reaper = scheduleCleanup ? Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "workhub-jdbc-idle-cleanup");
            thread.setDaemon(true);
            return thread;
        }) : null;
        if (reaper != null) {
            reaper.scheduleWithFixedDelay(this::reapSafely, 1, 1, TimeUnit.MINUTES);
        }
    }

    public Lease acquire(String jdbcUrl, String username, String password) throws SQLException {
        String key = jdbcUrl + "|" + username;
        String version = version(jdbcUrl, username, password);
        lifecycle.readLock().lock();
        try {
            if (closed) {
                throw new SQLException("目标库连接缓存已关闭");
            }
            Slot slot = slots.computeIfAbsent(key, ignored -> new Slot());
            synchronized (slot) {
                Entry cached = slot.cached;
                if (cached != null && !slot.busy && cached.version.equals(version) && healthy(cached)) {
                    slot.busy = true;
                    return new Lease(this, slot, cached, false);
                }
                if (cached != null && !slot.busy) {
                    dispose(slot, cached);
                }
                Entry fresh = new Entry(version, connectionFactory.open(jdbcUrl, username, password));
                if (!slot.busy) {
                    slot.cached = fresh;
                    slot.busy = true;
                    return new Lease(this, slot, fresh, false);
                }
                return new Lease(this, slot, fresh, true);
            }
        } finally {
            lifecycle.readLock().unlock();
        }
    }

    private boolean healthy(Entry entry) {
        try {
            return !entry.connection.isClosed() && entry.connection.isValid(2);
        } catch (SQLException ex) {
            return false;
        }
    }

    private void release(Slot slot, Entry entry, boolean standalone) {
        synchronized (slot) {
            if (standalone) {
                closeQuietly(entry.connection);
                return;
            }
            slot.busy = false;
            if (slot.cached == entry) {
                if (entry.broken) {
                    slot.cached = null;
                    closeQuietly(entry.connection);
                } else {
                    entry.idleSince = clock.getAsLong();
                }
            } else {
                closeQuietly(entry.connection);
            }
        }
    }

    void reapIdle() {
        for (Slot slot : slots.values()) {
            synchronized (slot) {
                Entry entry = slot.cached;
                if (entry != null && !slot.busy && clock.getAsLong() - entry.idleSince > IDLE_NANOS) {
                    dispose(slot, entry);
                    LOG.log(System.Logger.Level.INFO, "目标库连接空闲超过五分钟，已回收");
                }
            }
        }
    }

    private void reapSafely() {
        try {
            reapIdle();
        } catch (RuntimeException ex) {
            LOG.log(System.Logger.Level.WARNING, "目标库空闲连接清理异常，将在下次扫描继续");
        }
    }

    private void dispose(Slot slot, Entry entry) {
        slot.cached = null;
        closeQuietly(entry.connection);
    }

    private void closeQuietly(Connection connection) {
        try {
            connection.close();
        } catch (SQLException ex) {
            LOG.log(System.Logger.Level.WARNING, "目标库连接关闭失败");
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
                    if (slot.cached != null) {
                        closeQuietly(slot.cached.connection);
                        slot.cached = null;
                    }
                }
            }
            slots.clear();
        } finally {
            lifecycle.writeLock().unlock();
        }
    }

    private String version(String jdbcUrl, String username, String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt);
            for (String value : new String[] {jdbcUrl, username, password == null ? "" : password}) {
                byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
                digest.update((byte) bytes.length);
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }

    interface ConnectionFactory {
        Connection open(String jdbcUrl, String username, String password) throws SQLException;
    }

    private static final class Slot {
        Entry cached;
        boolean busy;
    }

    private static final class Entry {
        final String version;
        final Connection connection;
        long idleSince;
        boolean broken;

        Entry(String version, Connection connection) {
            this.version = version;
            this.connection = connection;
        }
    }

    /** 连接租约；discard 标记连接已损坏，归还时关闭而不缓存。 */
    public static final class Lease implements AutoCloseable {
        private final JdbcConnectionLeaseCache cache;
        private final Slot slot;
        private final Entry entry;
        private final boolean standalone;
        private boolean released;

        private Lease(JdbcConnectionLeaseCache cache, Slot slot, Entry entry, boolean standalone) {
            this.cache = cache;
            this.slot = slot;
            this.entry = entry;
            this.standalone = standalone;
        }

        public Connection connection() {
            return entry.connection;
        }

        public void discard() {
            entry.broken = true;
        }

        @Override
        public synchronized void close() {
            if (!released) {
                released = true;
                cache.release(slot, entry, standalone);
            }
        }
    }
}
