package cn.aslight.workhub.integration.ai;

public enum AiProviderType {
    HEURISTIC,
    OPENROUTER,
    MINIMAX;

    public static AiProviderType fromValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return HEURISTIC;
        }
        return switch (value.trim().toLowerCase()) {
            case "openrouter" -> OPENROUTER;
            case "minimax" -> MINIMAX;
            case "heuristic", "mock", "local" -> HEURISTIC;
            default -> throw new IllegalArgumentException("不支持的 AI provider: " + value);
        };
    }
}
