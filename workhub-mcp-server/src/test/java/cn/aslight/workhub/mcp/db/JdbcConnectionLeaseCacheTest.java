package cn.aslight.workhub.mcp.db;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcConnectionLeaseCacheTest {

    private JdbcConnectionLeaseCache cache;

    @AfterEach
    void tearDown() {
        if (cache != null) {
            cache.close();
        }
    }

    @Test
    void acquire_shouldReuseCachedConnectionWhenIdle() throws Exception {
        CountingFactory factory = new CountingFactory();
        cache = newCache(factory, System::nanoTime);
        JdbcConnectionLeaseCache.Lease first = cache.acquire("jdbc:mysql://host:3306/db", "user", "pass");
        first.close();

        JdbcConnectionLeaseCache.Lease second = cache.acquire("jdbc:mysql://host:3306/db", "user", "pass");

        assertSame(first.connection(), second.connection());
        assertEquals(1, factory.opened.get());
        second.close();
    }

    @Test
    void acquire_shouldOpenStandaloneConnectionWhenCachedLeaseBusy() throws Exception {
        CountingFactory factory = new CountingFactory();
        cache = newCache(factory, System::nanoTime);
        JdbcConnectionLeaseCache.Lease busy = cache.acquire("jdbc:mysql://host:3306/db", "user", "pass");

        JdbcConnectionLeaseCache.Lease concurrent = cache.acquire("jdbc:mysql://host:3306/db", "user", "pass");

        assertNotSame(busy.connection(), concurrent.connection());
        assertEquals(2, factory.opened.get());
        concurrent.close();
        assertTrue(concurrent.connection().isClosed());

        busy.close();
        JdbcConnectionLeaseCache.Lease next = cache.acquire("jdbc:mysql://host:3306/db", "user", "pass");
        assertSame(busy.connection(), next.connection());
        next.close();
    }

    @Test
    void acquire_shouldReplaceConnectionWhenPasswordChanges() throws Exception {
        CountingFactory factory = new CountingFactory();
        cache = newCache(factory, System::nanoTime);
        JdbcConnectionLeaseCache.Lease original = cache.acquire("jdbc:mysql://host:3306/db", "user", "old");
        original.close();

        JdbcConnectionLeaseCache.Lease rotated = cache.acquire("jdbc:mysql://host:3306/db", "user", "new");

        assertNotSame(original.connection(), rotated.connection());
        assertTrue(original.connection().isClosed());
        assertEquals(2, factory.opened.get());
        rotated.close();
    }

    @Test
    void release_shouldCloseDiscardedConnectionInsteadOfCaching() throws Exception {
        CountingFactory factory = new CountingFactory();
        cache = newCache(factory, System::nanoTime);
        JdbcConnectionLeaseCache.Lease lease = cache.acquire("jdbc:mysql://host:3306/db", "user", "pass");
        lease.discard();
        lease.close();

        assertTrue(lease.connection().isClosed());
        Connection reopened = cache.acquire("jdbc:mysql://host:3306/db", "user", "pass").connection();
        assertEquals(2, factory.opened.get());
        reopened.close();
    }

    @Test
    void reapIdle_shouldCloseConnectionsIdleBeyondTimeout() throws Exception {
        CountingFactory factory = new CountingFactory();
        MutableClock clock = new MutableClock();
        cache = newCache(factory, clock);
        JdbcConnectionLeaseCache.Lease lease = cache.acquire("jdbc:mysql://host:3306/db", "user", "pass");
        Connection connection = lease.connection();
        lease.close();

        clock.advanceMillis(5 * 60_000 + 1);
        cache.reapIdle();

        assertTrue(connection.isClosed());
        Connection reopened = cache.acquire("jdbc:mysql://host:3306/db", "user", "pass").connection();
        assertEquals(2, factory.opened.get());
        assertFalse(reopened.isClosed());
        reopened.close();
    }

    private JdbcConnectionLeaseCache newCache(JdbcConnectionLeaseCache.ConnectionFactory factory, LongSupplier clock) {
        return new JdbcConnectionLeaseCache(factory, clock, false);
    }

    private static final class CountingFactory implements JdbcConnectionLeaseCache.ConnectionFactory {
        private final AtomicInteger opened = new AtomicInteger();

        @Override
        public Connection open(String jdbcUrl, String username, String password) {
            opened.incrementAndGet();
            return fakeConnection();
        }
    }

    private static final class MutableClock implements LongSupplier {
        private long nanos;

        @Override
        public long getAsLong() {
            return nanos;
        }

        void advanceMillis(long millis) {
            nanos += millis * 1_000_000;
        }
    }

    static Connection fakeConnection() {
        InvocationHandler handler = new InvocationHandler() {
            private boolean closed;

            @Override
            public Object invoke(Object proxy, Method method, Object[] args) {
                return switch (method.getName()) {
                    case "isClosed" -> closed;
                    case "isValid" -> !closed && ((Integer) args[0] >= 0);
                    case "close" -> withClosed();
                    case "toString" -> "fake-connection@" + Integer.toHexString(System.identityHashCode(proxy));
                    default -> null;
                };
            }

            private Object withClosed() {
                closed = true;
                return null;
            }
        };
        List<Class<?>> interfaces = List.of(Connection.class);
        return (Connection) Proxy.newProxyInstance(
                JdbcConnectionLeaseCacheTest.class.getClassLoader(),
                interfaces.toArray(new Class<?>[0]),
                handler);
    }
}
