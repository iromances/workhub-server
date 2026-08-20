package cn.aslight.workhub.service.ai;

import cn.aslight.workhub.config.AiProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiCredentialCryptoServiceTest {

    @Test
    void encryptAndDecrypt_shouldRoundTripWithVersionedCipherText() {
        AiCredentialCryptoService service = cryptoService("test-ai-master-key");

        String firstCipherText = service.encrypt("provider-key-123456");
        String secondCipherText = service.encrypt("provider-key-123456");

        assertTrue(firstCipherText.startsWith("ai:v1:"));
        assertNotEquals("provider-key-123456", firstCipherText);
        assertNotEquals(firstCipherText, secondCipherText);
        assertEquals("provider-key-123456", service.decrypt(firstCipherText));
    }

    @Test
    void decrypt_shouldReadLegacyPlainText() {
        AiCredentialCryptoService service = cryptoService("test-ai-master-key");

        assertEquals("legacy-provider-key", service.decrypt("legacy-provider-key"));
        assertNull(service.decrypt(null));
        assertNull(service.encrypt(" "));
    }

    @Test
    void mask_shouldOnlyExposeLastFourCharacters() {
        AiCredentialCryptoService service = cryptoService("test-ai-master-key");

        assertEquals("**********3456", service.mask("provider-key-123456"));
        assertEquals("***", service.mask("key"));
        assertNull(service.mask(" "));
    }

    @Test
    void decrypt_shouldRejectCipherTextEncryptedByAnotherMasterKey() {
        String cipherText = cryptoService("first-master-key").encrypt("provider-key");

        assertThrows(IllegalStateException.class,
                () -> cryptoService("second-master-key").decrypt(cipherText));
    }

    @Test
    void encrypt_shouldRequireExplicitMasterKey() {
        AiProperties properties = new AiProperties();
        AiCredentialCryptoService service = new AiCredentialCryptoService(properties);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.encrypt("provider-key")
        );

        assertTrue(error.getMessage().contains("加密失败"));
    }

    private AiCredentialCryptoService cryptoService(String masterKey) {
        AiProperties properties = new AiProperties();
        properties.setMasterKey(masterKey);
        return new AiCredentialCryptoService(properties);
    }
}
