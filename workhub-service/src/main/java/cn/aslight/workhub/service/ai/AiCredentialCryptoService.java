package cn.aslight.workhub.service.ai;

import cn.aslight.workhub.config.AiProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * AI 供应商凭据加解密服务。
 */
@Service
public class AiCredentialCryptoService {

    private static final String PREFIX = "ai:v1:";
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_SIZE = 12;
    private static final int TAG_SIZE_BITS = 128;
    private static final int MASK_VISIBLE_SUFFIX = 4;
    private static final int MASK_PREFIX_LENGTH = 10;

    private final AiProperties aiProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public AiCredentialCryptoService(AiProperties aiProperties) {
        this.aiProperties = aiProperties;
    }

    public String encrypt(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_SIZE];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, buildSecretKey(), new GCMParameterSpec(TAG_SIZE_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception ex) {
            throw new IllegalStateException("AI 供应商凭据加密失败", ex);
        }
    }

    /**
     * 解密新版密文；没有版本前缀的数据按历史明文原样读取。
     */
    public String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isBlank()) {
            return null;
        }
        if (!isEncrypted(cipherText)) {
            return cipherText;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(cipherText.substring(PREFIX.length()));
            if (combined.length <= IV_SIZE) {
                throw new IllegalArgumentException("AI 供应商凭据密文长度非法");
            }
            byte[] iv = Arrays.copyOfRange(combined, 0, IV_SIZE);
            byte[] encrypted = Arrays.copyOfRange(combined, IV_SIZE, combined.length);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, buildSecretKey(), new GCMParameterSpec(TAG_SIZE_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("AI 供应商凭据解密失败", ex);
        }
    }

    public String mask(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        if (rawValue.length() <= MASK_VISIBLE_SUFFIX) {
            return "*".repeat(rawValue.length());
        }
        return "*".repeat(MASK_PREFIX_LENGTH)
                + rawValue.substring(rawValue.length() - MASK_VISIBLE_SUFFIX);
    }

    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    private SecretKeySpec buildSecretKey() throws Exception {
        String masterKey = aiProperties.getMasterKey();
        if (masterKey == null || masterKey.isBlank()) {
            throw new IllegalStateException("AI 凭据主密钥不能为空");
        }
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                masterKey.getBytes(StandardCharsets.UTF_8)
        );
        return new SecretKeySpec(digest, "AES");
    }
}
