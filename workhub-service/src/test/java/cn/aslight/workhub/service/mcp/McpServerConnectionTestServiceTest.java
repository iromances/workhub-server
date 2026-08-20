package cn.aslight.workhub.service.mcp;

import cn.aslight.workhub.config.McpProperties;
import cn.aslight.workhub.dao.mcp.McpResourceMapper;
import cn.aslight.workhub.model.mcp.McpResourceEntity;
import cn.aslight.workhub.model.mcp.McpServerConnectionTestRequest;
import cn.aslight.workhub.model.mcp.McpServerConnectionTestResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpServerConnectionTestServiceTest {

    @Test
    void test_shouldUseCurrentFormForDirectConnection() {
        McpResourceMapper mapper = mock(McpResourceMapper.class);
        McpServerConnectionTester tester = mock(McpServerConnectionTester.class);
        when(tester.test(any())).thenReturn(new McpServerConnectionTester.Result(
                true, "DIRECT", null, 30L, "服务器 SSH 连接成功"
        ));
        McpServerConnectionTestService service = service(mapper, tester);
        McpServerConnectionTestRequest request = request();
        request.setSshPassword(" server-password ");

        McpServerConnectionTestResponse response = service.test(request, "admin", "127.0.0.1");

        assertTrue(response.success());
        assertEquals("DIRECT", response.connectionMode());
        verify(tester).test(new McpServerConnectionTester.Input(
                "server.internal", 22, "server-user", " server-password ", null, null
        ));
    }

    @Test
    void test_shouldReuseSavedTargetAndBastionPasswords() {
        McpResourceMapper mapper = mock(McpResourceMapper.class);
        McpServerConnectionTester tester = mock(McpServerConnectionTester.class);
        McpCryptoService cryptoService = cryptoService();
        McpResourceEntity existing = new McpResourceEntity();
        existing.setId(9L);
        existing.setResourceType("SERVER");
        existing.setSshPasswordEncrypted(cryptoService.encrypt("saved-server-password"));
        existing.setSshBastionEnabled(true);
        existing.setSshBastionPasswordEncrypted(cryptoService.encrypt("saved-bastion-password"));
        when(mapper.findById(9L)).thenReturn(existing);
        when(tester.test(any())).thenReturn(new McpServerConnectionTester.Result(
                true, "BASTION", null, 70L, "经堡垒机连接服务器成功"
        ));
        McpServerConnectionTestService service = new McpServerConnectionTestService(
                mapper, cryptoService, tester, null
        );
        McpServerConnectionTestRequest request = request();
        request.setId(9L);
        request.setSshBastionEnabled(true);
        request.setSshBastionHost("jump.internal");
        request.setSshBastionPort(22);
        request.setSshBastionUser("jump-user");

        McpServerConnectionTestResponse response = service.test(request, "admin", "127.0.0.1");

        assertTrue(response.success());
        assertEquals("BASTION", response.connectionMode());
        verify(tester).test(new McpServerConnectionTester.Input(
                "server.internal", 22, "server-user", "saved-server-password", null,
                new McpServerConnectionTester.Bastion(
                        "jump.internal", 22, "jump-user", "saved-bastion-password", null
                )
        ));
    }

    private McpServerConnectionTestService service(McpResourceMapper mapper,
                                                    McpServerConnectionTester tester) {
        return new McpServerConnectionTestService(mapper, cryptoService(), tester, null);
    }

    private McpServerConnectionTestRequest request() {
        McpServerConnectionTestRequest request = new McpServerConnectionTestRequest();
        request.setHost("server.internal");
        request.setPort(22);
        request.setUsername("server-user");
        request.setSshBastionEnabled(false);
        return request;
    }

    private McpCryptoService cryptoService() {
        McpProperties properties = new McpProperties();
        properties.setMasterKey("test-mcp-master-key");
        return new McpCryptoService(properties);
    }
}
