package cn.aslight.workhub.service.mcp;

import cn.aslight.workhub.config.McpProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpCryptoServiceTest {

    @Test
    void encryptAndDecrypt_shouldRoundTripMcpSecret() {
        McpProperties properties = new McpProperties();
        properties.setMasterKey("test-mcp-master-key");
        McpCryptoService service = new McpCryptoService(properties);

        String cipherText = service.encrypt("db-password-123");

        assertNotEquals("db-password-123", cipherText);
        assertTrue(cipherText.startsWith("mcp:v1:"));
        assertEquals("db-password-123", service.decrypt(cipherText));
    }
}
