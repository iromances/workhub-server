package cn.aslight.workhub.service.mcp;

import cn.aslight.workhub.config.McpProperties;
import cn.aslight.workhub.dao.mcp.McpResourceMapper;
import cn.aslight.workhub.model.mcp.McpBastionEntity;
import cn.aslight.workhub.model.mcp.McpDatabaseConnectionTestRequest;
import cn.aslight.workhub.model.mcp.McpDatabaseConnectionTestResponse;
import cn.aslight.workhub.model.mcp.McpResourceEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpDatabaseConnectionTestServiceTest {

    @Test
    void test_shouldUseCurrentFormForDirectConnection() {
        McpResourceMapper mapper = mock(McpResourceMapper.class);
        McpBastionService bastionService = mock(McpBastionService.class);
        McpDatabaseConnectionTester tester = mock(McpDatabaseConnectionTester.class);
        when(tester.test(any())).thenReturn(new McpDatabaseConnectionTester.Result(
                true, "DIRECT", null, 31L, "直连数据库成功"
        ));
        McpDatabaseConnectionTestService service = service(mapper, bastionService, tester);
        McpDatabaseConnectionTestRequest request = request();
        request.setPassword(" current-password ");

        McpDatabaseConnectionTestResponse response = service.test(request, "admin", "127.0.0.1");

        assertTrue(response.success());
        assertEquals("DIRECT", response.connectionMode());
        assertEquals("直连数据库成功", response.message());
        verify(tester).test(new McpDatabaseConnectionTester.Input(
                "db.internal", 3306, "asset", "readonly", " current-password ", null
        ));
    }

    @Test
    void test_shouldReuseSavedDatabasePasswordAndSelectedBastion() {
        McpResourceMapper mapper = mock(McpResourceMapper.class);
        McpBastionService bastionService = mock(McpBastionService.class);
        McpDatabaseConnectionTester tester = mock(McpDatabaseConnectionTester.class);
        McpCryptoService cryptoService = cryptoService();
        McpResourceEntity existing = new McpResourceEntity();
        existing.setId(7L);
        existing.setResourceType("DATABASE");
        existing.setPasswordEncrypted(cryptoService.encrypt("saved-db-password"));
        existing.setSshBastionEnabled(true);
        existing.setBastionId(1L);
        when(mapper.findById(7L)).thenReturn(existing);
        McpBastionEntity bastion = new McpBastionEntity();
        bastion.setId(1L);
        bastion.setName("生产堡垒机");
        bastion.setHost("jump.internal");
        bastion.setPort(22);
        bastion.setUsername("ops");
        bastion.setEnabled(true);
        when(bastionService.requireEnabled(1L)).thenReturn(bastion);
        when(bastionService.decryptPassword(bastion)).thenReturn("ssh-password");
        when(tester.test(any())).thenReturn(new McpDatabaseConnectionTester.Result(
                true, "BASTION", null, 88L, "经堡垒机连接数据库成功"
        ));
        McpDatabaseConnectionTestService service = new McpDatabaseConnectionTestService(
                mapper, cryptoService, bastionService, tester, null
        );
        McpDatabaseConnectionTestRequest request = request();
        request.setId(7L);
        request.setSshBastionEnabled(true);
        request.setBastionId(1L);

        McpDatabaseConnectionTestResponse response = service.test(request, "admin", "127.0.0.1");

        assertTrue(response.success());
        assertEquals("BASTION", response.connectionMode());
        assertEquals("生产堡垒机", response.bastionName());
        assertEquals("经“生产堡垒机”连接数据库成功", response.message());
        verify(tester).test(new McpDatabaseConnectionTester.Input(
                "db.internal", 3306, "asset", "readonly", "saved-db-password",
                new McpDatabaseConnectionTester.Bastion(
                        "jump.internal", 22, "ops", "ssh-password", null
                )
        ));
    }

    private McpDatabaseConnectionTestService service(McpResourceMapper mapper,
                                                      McpBastionService bastionService,
                                                      McpDatabaseConnectionTester tester) {
        return new McpDatabaseConnectionTestService(
                mapper, cryptoService(), bastionService, tester, null
        );
    }

    private McpDatabaseConnectionTestRequest request() {
        McpDatabaseConnectionTestRequest request = new McpDatabaseConnectionTestRequest();
        request.setHost("db.internal");
        request.setPort(3306);
        request.setDatabaseSchema("asset");
        request.setUsername("readonly");
        request.setSshBastionEnabled(false);
        return request;
    }

    private McpCryptoService cryptoService() {
        McpProperties properties = new McpProperties();
        properties.setMasterKey("test-mcp-master-key");
        return new McpCryptoService(properties);
    }
}
