package cn.aslight.workhub.service.system;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * 一次性密码生成器。
 */
@Component
public class PasswordGenerator {

    private static final char[] CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%".toCharArray();
    private final SecureRandom random = new SecureRandom();

    public String generate() {
        StringBuilder builder = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            builder.append(CHARS[random.nextInt(CHARS.length)]);
        }
        return builder.toString();
    }
}
