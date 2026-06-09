package cn.aslight.workhub.mcp.security;

/**
 * Resolves local development literals and production environment references.
 */
public class SecretResolver {

    public String resolve(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("${") && trimmed.endsWith("}")) {
            String envName = trimmed.substring(2, trimmed.length() - 1);
            String envValue = System.getenv(envName);
            if (envValue == null || envValue.isBlank()) {
                throw new IllegalArgumentException("环境变量未配置：" + envName);
            }
            return envValue;
        }
        return value;
    }
}
