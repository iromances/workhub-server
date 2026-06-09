package cn.aslight.workhub.service.mcp;

import cn.aslight.workhub.config.McpProperties;
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
 * MCP 访问凭据加解密服务。
 */
@Service
public class McpCryptoService {

    private static final String PREFIX = "mcp:v1:";
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_SIZE = 12;
    private static final int TAG_SIZE_BITS = 128;

    private final McpProperties mcpProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public McpCryptoService(McpProperties mcpProperties) {
        this.mcpProperties = mcpProperties;
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
            throw new IllegalStateException("MCP 凭据加密失败", ex);
        }
    }



    public String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isBlank()) {
            return null;
        }
        if (!cipherText.startsWith(PREFIX)) {
            return cipherText;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(cipherText.substring(PREFIX.length()));
            byte[] iv = Arrays.copyOfRange(combined, 0, IV_SIZE);
            byte[] encrypted = Arrays.copyOfRange(combined, IV_SIZE, combined.length);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, buildSecretKey(), new GCMParameterSpec(TAG_SIZE_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("MCP 凭据解密失败", ex);
        }
    }

    private SecretKeySpec buildSecretKey() throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                mcpProperties.getMasterKey().getBytes(StandardCharsets.UTF_8)
        );
        return new SecretKeySpec(digest, "AES");
    }
}
