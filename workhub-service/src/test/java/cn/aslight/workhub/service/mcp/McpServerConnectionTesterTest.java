package cn.aslight.workhub.service.mcp;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpServerConnectionTesterTest {

    @Test
    void test_shouldConnectDirectlyWithoutRemoteCommand() {
        McpServerConnectionTester tester = new McpServerConnectionTester(
                input -> new McpServerConnectionTester.TunnelSession(input.host(), input.port(), () -> {
                }),
                (host, port, username, password, identityFile) ->
                        new McpBastionConnectionTester.Result(true, 20L, "连接成功")
        );

        McpServerConnectionTester.Result result = tester.test(input(null));

        assertTrue(result.success());
        assertEquals("DIRECT", result.connectionMode());
        assertNull(result.failureStage());
        assertEquals("服务器 SSH 连接成功", result.message());
    }

    @Test
    void test_shouldProbeTargetThroughBastionTunnel() {
        McpServerConnectionTester.Bastion bastion = bastion();
        McpServerConnectionTester tester = new McpServerConnectionTester(
                input -> new McpServerConnectionTester.TunnelSession("127.0.0.1", 45321, () -> {
                }),
                (host, port, username, password, identityFile) -> {
                    assertEquals("127.0.0.1", host);
                    assertEquals(45321, port);
                    assertEquals("server-user", username);
                    return new McpBastionConnectionTester.Result(true, 20L, "连接成功");
                }
        );

        McpServerConnectionTester.Result result = tester.test(input(bastion));

        assertTrue(result.success());
        assertEquals("BASTION", result.connectionMode());
        assertNull(result.failureStage());
        assertEquals("经堡垒机连接服务器成功", result.message());
    }

    @Test
    void test_shouldClassifyBastionFailure() {
        McpServerConnectionTester tester = new McpServerConnectionTester(
                input -> {
                    throw new IOException("堡垒机认证失败");
                },
                (host, port, username, password, identityFile) -> {
                    throw new AssertionError("堡垒机失败后不应测试目标服务器");
                }
        );

        McpServerConnectionTester.Result result = tester.test(input(bastion()));

        assertFalse(result.success());
        assertEquals("BASTION", result.failureStage());
        assertEquals("堡垒机认证失败", result.message());
    }

    @Test
    void test_shouldClassifyTargetServerFailure() {
        McpServerConnectionTester tester = new McpServerConnectionTester(
                input -> new McpServerConnectionTester.TunnelSession(input.host(), input.port(), () -> {
                }),
                (host, port, username, password, identityFile) ->
                        new McpBastionConnectionTester.Result(false, 20L, "认证失败")
        );

        McpServerConnectionTester.Result result = tester.test(input(null));

        assertFalse(result.success());
        assertEquals("SERVER", result.failureStage());
        assertEquals("服务器 SSH 认证失败", result.message());
    }

    private McpServerConnectionTester.Input input(McpServerConnectionTester.Bastion bastion) {
        return new McpServerConnectionTester.Input(
                "server.internal", 22, "server-user", "server-password", null, bastion
        );
    }

    private McpServerConnectionTester.Bastion bastion() {
        return new McpServerConnectionTester.Bastion(
                "jump.internal", 22, "jump-user", "jump-password", null
        );
    }
}
