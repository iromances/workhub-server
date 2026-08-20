package cn.aslight.workhub.service.ai;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 内置 AI 场景及其不可由数据库提升的安全能力边界。
 */
public final class AiUseCaseDefinitions {

    public static final String STRUCTURED_EXTRACT = "intake.structured.extract";
    public static final String SQL_DRAFT_GENERATE = "intake.sql-draft.generate";
    public static final String CLARIFICATION_ANALYZE = "intake.clarification.analyze";
    public static final String DEVELOPMENT_ANALYZE = "intake.development.analyze";
    public static final String DEVELOPMENT_ADJUST = "intake.development.adjust";
    public static final String PROVIDER_SMOKE = "system.provider.smoke";

    private static final Map<String, Definition> DEFINITIONS;

    static {
        Map<String, Definition> definitions = new LinkedHashMap<>();
        definitions.put(STRUCTURED_EXTRACT, new Definition(STRUCTURED_EXTRACT, "需求结构化提取", Capability.IMAGE_INPUT,
                Set.of("API", "CLI"), Set.of("prompt")));
        definitions.put(SQL_DRAFT_GENERATE, new Definition(SQL_DRAFT_GENERATE, "SQL草稿生成", Capability.IMAGE_INPUT,
                Set.of("API", "CLI"), Set.of("prompt")));
        definitions.put(CLARIFICATION_ANALYZE, new Definition(CLARIFICATION_ANALYZE, "需求澄清分析", Capability.LOCAL_REPOSITORY_READ,
                Set.of("CLI"), Set.of("prompt")));
        definitions.put(DEVELOPMENT_ANALYZE, new Definition(DEVELOPMENT_ANALYZE, "研发方案分析", Capability.LOCAL_REPOSITORY_READ,
                Set.of("CLI"), Set.of("prompt")));
        definitions.put(DEVELOPMENT_ADJUST, new Definition(DEVELOPMENT_ADJUST, "研发方案调整", Capability.LOCAL_REPOSITORY_READ,
                Set.of("CLI"), Set.of("prompt")));
        DEFINITIONS = Map.copyOf(definitions);
    }

    private AiUseCaseDefinitions() {
    }

    public static Definition require(String useCaseCode) {
        Definition definition = DEFINITIONS.get(useCaseCode);
        if (definition == null) {
            throw new IllegalArgumentException("未注册的 AI 场景: " + useCaseCode);
        }
        return definition;
    }

    public static Map<String, Definition> all() {
        return DEFINITIONS;
    }

    public enum Capability {
        MODEL_ONLY,
        IMAGE_INPUT,
        LOCAL_REPOSITORY_READ
    }

    public record Definition(String code,
                             String name,
                             Capability capability,
                             Set<String> allowedChannelTypes,
                             Set<String> allowedPromptVariables) {

        public void requireChannel(String channelType) {
            String normalized = channelType == null ? "" : channelType.trim().toUpperCase();
            if (!allowedChannelTypes.contains(normalized)) {
                throw new IllegalStateException("AI 场景 " + code + " 不允许使用 " + channelType + " 通道");
            }
        }
    }
}
