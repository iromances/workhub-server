package cn.aslight.workhub.service.ai;

import java.util.LinkedHashMap;
import java.util.Locale;
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
    public static final String SDD_ANALYZE = "intake.development.sdd-analyze";
    public static final String SUBMIT_TEST_REVIEW = "intake.submit-test.review";
    public static final String AGENT_API_STEP = "agent.api.step";
    public static final String AGENT_CLI_STEP = "agent.cli.step";
    public static final String AGENT_CHAT_CONTEXT = "agent.chat.context";
    public static final String PROVIDER_SMOKE = "system.provider.smoke";

    private static final Set<String> STRUCTURED_EXTRACT_VARIABLES = Set.of(
            "sourceChannel", "manualNotes", "attachmentList", "attachmentSummaries");
    private static final Set<String> SQL_DRAFT_VARIABLES = Set.of(
            "intakeRecord", "rawContent", "structuredData", "attachmentSummaries", "attachmentList");
    private static final Set<String> CLARIFICATION_VARIABLES = Set.of(
            "projectInfo", "requirementMarkdown", "intakeInfo", "structuredFields",
            "attachmentSummaries", "knowledgeBase", "rawContent");
    private static final Set<String> DEVELOPMENT_VARIABLES = Set.of(
            "projectInfo", "developers", "systemTags", "requirementMarkdown", "intakeInfo",
            "structuredFields", "attachmentSummaries", "knowledgeBase", "rawContent");
    private static final Set<String> DEVELOPMENT_ADJUST_VARIABLES = Set.of(
            "developers", "currentDraft", "userMessage");
    private static final Set<String> SDD_ANALYZE_VARIABLES = Set.of(
            "sddProcessSpec", "projectInfo", "developers", "systemTags", "requirementMarkdown",
            "intakeInfo", "structuredFields", "attachmentSummaries", "knowledgeBase", "rawContent");
    private static final Set<String> SUBMIT_TEST_REVIEW_VARIABLES = Set.of(
            "developmentBranch", "diffSummary", "verificationEvidence", "intakeInfo", "workItems");
    private static final Set<String> AGENT_API_STEP_VARIABLES = Set.of(
            "agentInstructions", "taskSpecification", "dataWindow", "platformGuide", "trajectory");
    private static final Set<String> AGENT_CLI_STEP_VARIABLES = Set.of(
            "agentInstructions", "taskSpecification", "dataWindow", "platformGuide", "trajectory");
    private static final Set<String> AGENT_CHAT_CONTEXT_VARIABLES = Set.of(
            "agentName", "positionName", "agentInstructions", "scopeSummary", "platformGuide");

    private static final Map<String, Definition> DEFINITIONS;

    static {
        Map<String, Definition> definitions = new LinkedHashMap<>();
        Set<String> allCliTypes = Set.of("CODEX", "ZCODE");
        definitions.put(STRUCTURED_EXTRACT, new Definition(STRUCTURED_EXTRACT, "需求结构化提取", Capability.IMAGE_INPUT,
                Set.of("API", "CLI"), allCliTypes, STRUCTURED_EXTRACT_VARIABLES));
        definitions.put(SQL_DRAFT_GENERATE, new Definition(SQL_DRAFT_GENERATE, "SQL草稿生成", Capability.IMAGE_INPUT,
                Set.of("API", "CLI"), allCliTypes, SQL_DRAFT_VARIABLES));
        definitions.put(CLARIFICATION_ANALYZE, new Definition(CLARIFICATION_ANALYZE, "需求澄清分析", Capability.LOCAL_REPOSITORY_READ,
                Set.of("CLI"), allCliTypes, CLARIFICATION_VARIABLES));
        definitions.put(DEVELOPMENT_ANALYZE, new Definition(DEVELOPMENT_ANALYZE, "研发方案分析", Capability.LOCAL_REPOSITORY_READ,
                Set.of("CLI"), allCliTypes, DEVELOPMENT_VARIABLES));
        definitions.put(DEVELOPMENT_ADJUST, new Definition(DEVELOPMENT_ADJUST, "研发方案调整", Capability.LOCAL_REPOSITORY_READ,
                Set.of("CLI"), allCliTypes, DEVELOPMENT_ADJUST_VARIABLES));
        definitions.put(SDD_ANALYZE, new Definition(SDD_ANALYZE, "SDD 设计评估", Capability.LOCAL_REPOSITORY_READ,
                Set.of("CLI"), allCliTypes, SDD_ANALYZE_VARIABLES));
        definitions.put(SUBMIT_TEST_REVIEW, new Definition(SUBMIT_TEST_REVIEW, "提测评审", Capability.MODEL_ONLY,
                Set.of("API", "CLI"), allCliTypes, SUBMIT_TEST_REVIEW_VARIABLES));
        definitions.put(AGENT_API_STEP, new Definition(AGENT_API_STEP, "Agent API 步进", Capability.MODEL_ONLY,
                Set.of("API"), Set.of(), AGENT_API_STEP_VARIABLES));
        definitions.put(AGENT_CLI_STEP, new Definition(AGENT_CLI_STEP, "Agent CLI 步进", Capability.MODEL_ONLY,
                Set.of("CLI"), allCliTypes, AGENT_CLI_STEP_VARIABLES));
        definitions.put(AGENT_CHAT_CONTEXT, new Definition(AGENT_CHAT_CONTEXT, "Agent 对话工位指引", Capability.MODEL_ONLY,
                Set.of("API", "CLI"), allCliTypes, AGENT_CHAT_CONTEXT_VARIABLES));
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

    public static Definition find(String useCaseCode) {
        return DEFINITIONS.get(useCaseCode);
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
                             Set<String> allowedCliTypes,
                             Set<String> allowedPromptVariables) {

        public void requireChannel(String channelType) {
            String normalized = channelType == null ? "" : channelType.trim().toUpperCase();
            if (!allowedChannelTypes.contains(normalized)) {
                throw new IllegalStateException("AI 场景 " + code + " 不允许使用 " + channelType + " 通道");
            }
        }

        public boolean allowsCliType(String cliType) {
            return allowedCliTypes.contains(cliType == null ? "" : cliType.trim().toUpperCase(Locale.ROOT));
        }
    }
}
