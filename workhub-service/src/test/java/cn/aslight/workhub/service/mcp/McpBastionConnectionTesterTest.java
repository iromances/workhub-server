package cn.aslight.workhub.service.mcp;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpBastionConnectionTesterTest {

    @Test
    void test_shouldReturnSuccessWithoutPuttingPasswordInCommand() throws Exception {
        Process process = runningProcess();
        AtomicReference<List<String>> command = new AtomicReference<>();
        AtomicReference<Map<String, String>> environment = new AtomicReference<>();
        McpBastionConnectionTester tester = new McpBastionConnectionTester(
                (actualCommand, actualEnvironment) -> {
                    command.set(actualCommand);
                    environment.set(actualEnvironment);
                    return process;
                },
                100L,
                controlSocket -> true
        );

        McpBastionConnectionTester.Result result =
                tester.test("10.10.0.8", 22, "workhub", "plain-password", null);

        assertTrue(result.success());
        assertEquals("连接成功", result.message());
        assertEquals("plain-password", environment.get().get("SSHPASS"));
        assertFalse(command.get().contains("plain-password"));
        assertEquals(List.of("sshpass", "-e", "ssh"), command.get().subList(0, 3));
        assertTrue(command.get().contains("-N"));
        assertTrue(command.get().stream().anyMatch(value -> value.startsWith("ControlPath=/tmp/workhub-ssh-")));
        assertFalse(command.get().contains("true"));
        verify(process).destroyForcibly();
    }

    @Test
    void test_shouldClassifyAuthenticationFailure() throws Exception {
        Process process = finishedProcess(255, "Permission denied (publickey,password).");
        McpBastionConnectionTester tester = new McpBastionConnectionTester(
                (command, environment) -> process,
                100L,
                controlSocket -> false
        );

        McpBastionConnectionTester.Result result =
                tester.test("10.10.0.8", 22, "workhub", null, "/tmp/test-key");

        assertFalse(result.success());
        assertEquals("认证失败", result.message());
    }

    @Test
    void test_shouldStopTimedOutProcess() throws Exception {
        Process process = mock(Process.class);
        when(process.isAlive()).thenReturn(true);
        when(process.destroyForcibly()).thenReturn(process);
        McpBastionConnectionTester tester = new McpBastionConnectionTester(
                (command, environment) -> process,
                5L,
                controlSocket -> false
        );

        McpBastionConnectionTester.Result result =
                tester.test("10.10.0.8", 22, "workhub", null, "/tmp/test-key");

        assertFalse(result.success());
        assertEquals("连接超时", result.message());
        verify(process).destroyForcibly();
    }

    @Test
    void test_shouldExplainMissingSshpass() {
        McpBastionConnectionTester tester = new McpBastionConnectionTester((command, environment) -> {
            throw new IOException("Cannot run program \"sshpass\"");
        });

        McpBastionConnectionTester.Result result =
                tester.test("10.10.0.8", 22, "workhub", "plain-password", null);

        assertFalse(result.success());
        assertEquals("服务环境未安装 sshpass，无法测试密码认证", result.message());
    }

    private Process finishedProcess(int exitCode, String output) throws Exception {
        Process process = mock(Process.class);
        when(process.isAlive()).thenReturn(false);
        when(process.exitValue()).thenReturn(exitCode);
        when(process.getInputStream()).thenReturn(new ByteArrayInputStream(
                output.getBytes(StandardCharsets.UTF_8)
        ));
        return process;
    }

    private Process runningProcess() {
        Process process = mock(Process.class);
        when(process.isAlive()).thenReturn(true);
        when(process.destroyForcibly()).thenReturn(process);
        return process;
    }
}
