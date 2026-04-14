package cn.aslight.workhub.service.payment;

import cn.aslight.workhub.config.PaymentProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 支付敏感配置加解密服务。
 */
@Service
public class PaymentCryptoService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_SIZE = 12;
    private static final int TAG_SIZE_BITS = 128;

    private final PaymentProperties paymentProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public PaymentCryptoService(PaymentProperties paymentProperties) {
        this.paymentProperties = paymentProperties;
    }

    public String encrypt(String plainText) {
        try {
            byte[] iv = new byte[IV_SIZE];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, buildSecretKey(), new GCMParameterSpec(TAG_SIZE_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception ex) {
            throw new IllegalStateException("支付敏感信息加密失败", ex);
        }
    }

    public String decrypt(String cipherText) {
        try {
            byte[] combined = Base64.getDecoder().decode(cipherText);
            byte[] iv = Arrays.copyOfRange(combined, 0, IV_SIZE);
            byte[] encrypted = Arrays.copyOfRange(combined, IV_SIZE, combined.length);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, buildSecretKey(), new GCMParameterSpec(TAG_SIZE_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("支付敏感信息解密失败", ex);
        }
    }

    public String mask(String rawValue) {
        String value = PaymentCatalogs.requireText(rawValue, "secretValue");
        int visible = Math.max(0, paymentProperties.getMaskVisibleSuffix());
        if (value.length() <= visible) {
            return "*".repeat(value.length());
        }
        return "*".repeat(Math.max(4, value.length() - visible)) + value.substring(value.length() - visible);
    }

    public String fingerprint(String rawValue) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawValue.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("支付敏感信息指纹生成失败", ex);
        }
    }

    public String algorithm() {
        return ALGORITHM;
    }

    private SecretKeySpec buildSecretKey() throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                paymentProperties.getMasterKey().getBytes(StandardCharsets.UTF_8)
        );
        return new SecretKeySpec(digest, "AES");
    }
}
