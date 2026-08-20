package cn.aslight.workhub.service.mcp;

import cn.aslight.workhub.config.McpProperties;
import cn.aslight.workhub.dao.mcp.McpBastionMapper;
import cn.aslight.workhub.model.mcp.McpBastionConnectionTestRequest;
import cn.aslight.workhub.model.mcp.McpBastionConnectionTestResponse;
import cn.aslight.workhub.model.mcp.McpBastionEntity;
import cn.aslight.workhub.model.mcp.McpBastionResponse;
import cn.aslight.workhub.model.mcp.McpBastionSaveRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpBastionServiceTest {

    @Test
    void create_shouldEncryptPasswordAndOnlyExposeConfiguredFlag() {
        McpBastionMapper mapper = mock(McpBastionMapper.class);
        McpBastionService service = new McpBastionService(mapper, cryptoService());
        AtomicReference<McpBastionEntity> saved = new AtomicReference<>();
        when(mapper.findByName("生产堡垒机")).thenReturn(null);
        org.mockito.Mockito.doAnswer(invocation -> {
            McpBastionEntity entity = invocation.getArgument(0);
            entity.setId(1L);
            saved.set(entity);
            return null;
        }).when(mapper).insert(any());
        when(mapper.findById(1L)).thenAnswer(invocation -> saved.get());

        McpBastionResponse response = service.create(request(), "admin", "127.0.0.1");

        ArgumentCaptor<McpBastionEntity> captor = ArgumentCaptor.forClass(McpBastionEntity.class);
        verify(mapper).insert(captor.capture());
        assertNotEquals("plain-password", captor.getValue().getPasswordEncrypted());
        assertTrue(captor.getValue().getPasswordEncrypted().startsWith("mcp:v1:"));
        assertTrue(response.passwordConfigured());
        assertEquals("生产堡垒机", response.name());
    }

    @Test
    void update_shouldKeepExistingPasswordWhenRequestPasswordBlank() {
        McpBastionMapper mapper = mock(McpBastionMapper.class);
        McpBastionService service = new McpBastionService(mapper, cryptoService());
        McpBastionEntity existing = entity();
        String existingCipherText = existing.getPasswordEncrypted();
        AtomicReference<McpBastionEntity> saved = new AtomicReference<>(existing);
        when(mapper.findById(1L)).thenAnswer(invocation -> saved.get());
        when(mapper.findByName("生产堡垒机")).thenReturn(existing);
        when(mapper.update(any())).thenAnswer(invocation -> {
            saved.set(invocation.getArgument(0));
            return 1;
        });
        McpBastionSaveRequest request = request();
        request.setPassword(" ");

        McpBastionResponse response = service.update(1L, request, "admin", "127.0.0.1");

        assertTrue(response.passwordConfigured());
        assertEquals(existingCipherText, saved.get().getPasswordEncrypted());
    }

    @Test
    void update_shouldRejectDisablingReferencedBastion() {
        McpBastionMapper mapper = mock(McpBastionMapper.class);
        McpBastionService service = new McpBastionService(mapper, cryptoService());
        McpBastionEntity existing = entity();
        when(mapper.findById(1L)).thenReturn(existing);
        when(mapper.findByName("生产堡垒机")).thenReturn(existing);
        when(mapper.countDatabaseReferences(1L)).thenReturn(1);
        McpBastionSaveRequest request = request();
        request.setEnabled(false);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.update(1L, request, "admin", "127.0.0.1")
        );

        assertEquals("堡垒机已被数据库目标引用，解除引用后才能停用", exception.getMessage());
    }

    @Test
    void testConnection_shouldTestCurrentUnsavedForm() {
        McpBastionMapper mapper = mock(McpBastionMapper.class);
        McpBastionConnectionTester tester = mock(McpBastionConnectionTester.class);
        when(tester.test("new-host", 2222, "new-user", "new-password", null))
                .thenReturn(new McpBastionConnectionTester.Result(true, 36L, "连接成功"));
        McpBastionService service = new McpBastionService(
                mapper,
                cryptoService(),
                null,
                tester
        );
        McpBastionConnectionTestRequest request = testRequest();
        request.setHost("new-host");
        request.setPort(2222);
        request.setUsername("new-user");
        request.setPassword("new-password");

        McpBastionConnectionTestResponse response =
                service.testConnection(request, "admin", "127.0.0.1");

        assertTrue(response.success());
        assertEquals(36L, response.durationMs());
        assertEquals("连接成功", response.message());
    }

    @Test
    void testConnection_shouldReuseStoredPasswordWhenEditPasswordBlank() {
        McpBastionMapper mapper = mock(McpBastionMapper.class);
        McpBastionConnectionTester tester = mock(McpBastionConnectionTester.class);
        McpBastionEntity existing = entity();
        when(mapper.findById(1L)).thenReturn(existing);
        when(tester.test("10.10.0.9", 22, "workhub", "plain-password", null))
                .thenReturn(new McpBastionConnectionTester.Result(false, 51L, "认证失败"));
        McpBastionService service = new McpBastionService(
                mapper,
                cryptoService(),
                null,
                tester
        );
        McpBastionConnectionTestRequest request = testRequest();
        request.setId(1L);
        request.setHost("10.10.0.9");
        request.setPassword(" ");

        McpBastionConnectionTestResponse response =
                service.testConnection(request, "admin", "127.0.0.1");

        assertEquals(false, response.success());
        assertEquals("认证失败", response.message());
        verify(tester).test("10.10.0.9", 22, "workhub", "plain-password", null);
    }

    private McpBastionSaveRequest request() {
        McpBastionSaveRequest request = new McpBastionSaveRequest();
        request.setName("生产堡垒机");
        request.setHost("10.10.0.8");
        request.setPort(22);
        request.setUsername("workhub");
        request.setPassword("plain-password");
        request.setEnabled(true);
        return request;
    }

    private McpBastionEntity entity() {
        McpBastionEntity entity = new McpBastionEntity();
        entity.setId(1L);
        entity.setName("生产堡垒机");
        entity.setHost("10.10.0.8");
        entity.setPort(22);
        entity.setUsername("workhub");
        entity.setPasswordEncrypted(cryptoService().encrypt("plain-password"));
        entity.setEnabled(true);
        return entity;
    }

    private McpBastionConnectionTestRequest testRequest() {
        McpBastionConnectionTestRequest request = new McpBastionConnectionTestRequest();
        request.setHost("10.10.0.8");
        request.setPort(22);
        request.setUsername("workhub");
        return request;
    }

    private McpCryptoService cryptoService() {
        McpProperties properties = new McpProperties();
        properties.setMasterKey("test-mcp-master-key");
        return new McpCryptoService(properties);
    }
}
