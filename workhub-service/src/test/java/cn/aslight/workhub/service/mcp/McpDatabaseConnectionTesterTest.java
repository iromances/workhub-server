package cn.aslight.workhub.service.mcp;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpDatabaseConnectionTesterTest {

    @Test
    void test_shouldUseDirectConnectionWhenBastionMissing() {
        AtomicReference<McpDatabaseConnectionTester.Input> captured = new AtomicReference<>();
        McpDatabaseConnectionTester tester = new McpDatabaseConnectionTester(captured::set);
        McpDatabaseConnectionTester.Input input = input(null);

        McpDatabaseConnectionTester.Result result = tester.test(input);

        assertTrue(result.success());
        assertEquals("DIRECT", result.connectionMode());
        assertEquals("直连数据库成功", result.message());
        assertEquals(input, captured.get());
    }

    @Test
    void test_shouldUseBastionConnectionWhenConfigured() {
        McpDatabaseConnectionTester tester = new McpDatabaseConnectionTester(input -> {
        });

        McpDatabaseConnectionTester.Result result = tester.test(input(new McpDatabaseConnectionTester.Bastion(
                "bastion", 22, "ops", "ssh-password", null
        )));

        assertTrue(result.success());
        assertEquals("BASTION", result.connectionMode());
        assertEquals("经堡垒机连接数据库成功", result.message());
    }

    @Test
    void test_shouldDistinguishBastionAndDatabaseFailures() {
        McpDatabaseConnectionTester bastionTester = new McpDatabaseConnectionTester(input -> {
            throw new McpDatabaseConnectionTester.ProbeException("BASTION", "堡垒机认证失败", null);
        });
        McpDatabaseConnectionTester databaseTester = new McpDatabaseConnectionTester(input -> {
            throw new SQLException("Access denied for user");
        });

        McpDatabaseConnectionTester.Result bastionResult = bastionTester.test(input(
                new McpDatabaseConnectionTester.Bastion("bastion", 22, "ops", "ssh-password", null)
        ));
        McpDatabaseConnectionTester.Result databaseResult = databaseTester.test(input(null));

        assertFalse(bastionResult.success());
        assertEquals("BASTION", bastionResult.failureStage());
        assertEquals("堡垒机认证失败", bastionResult.message());
        assertFalse(databaseResult.success());
        assertEquals("DATABASE", databaseResult.failureStage());
        assertEquals("数据库认证失败", databaseResult.message());
    }

    @Test
    void jdbcUrl_shouldApplyConnectionAndSocketTimeouts() {
        String url = McpDatabaseConnectionTester.jdbcUrl("db.internal", 3306, "asset");

        assertTrue(url.startsWith("jdbc:mysql://db.internal:3306/asset?"));
        assertTrue(url.contains("allowPublicKeyRetrieval=true"));
        assertTrue(url.contains("connectTimeout=8000"));
        assertTrue(url.contains("socketTimeout=8000"));
    }

    private McpDatabaseConnectionTester.Input input(McpDatabaseConnectionTester.Bastion bastion) {
        return new McpDatabaseConnectionTester.Input(
                "db.internal", 3306, "asset", "readonly", "db-password", bastion
        );
    }
}
