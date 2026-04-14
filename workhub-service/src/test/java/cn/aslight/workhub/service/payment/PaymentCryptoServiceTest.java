package cn.aslight.workhub.service.payment;

import cn.aslight.workhub.config.PaymentProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentCryptoServiceTest {

    @Test
    void encryptAndDecrypt_shouldRoundTrip() {
        PaymentProperties properties = new PaymentProperties();
        properties.setMasterKey("test-master-key");
        properties.setMaskVisibleSuffix(4);
        PaymentCryptoService service = new PaymentCryptoService(properties);

        String cipherText = service.encrypt("merchant-secret-123456");

        assertNotEquals("merchant-secret-123456", cipherText);
        assertEquals("merchant-secret-123456", service.decrypt(cipherText));
        assertTrue(service.mask("merchant-secret-123456").endsWith("3456"));
        assertEquals(64, service.fingerprint("merchant-secret-123456").length());
    }
}
