package cn.aslight.workhub.service.ai;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 校验提示词迁移文件中的模板占位与代码变量白名单一一对应。
 */
class PromptTemplateMigrationConsistencyTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([A-Za-z0-9_.-]+)}");

    @Test
    void migrationTemplates_shouldCoverExactlyTheWhitelistedVariables() {
        for (AiUseCaseDefinitions.Definition definition : AiUseCaseDefinitions.all().values()) {
            String template = TestPromptTemplates.template(definition.code());
            Set<String> placeholders = new LinkedHashSet<>();
            Matcher matcher = PLACEHOLDER.matcher(template);
            while (matcher.find()) {
                placeholders.add(matcher.group(1));
            }
            assertEquals(definition.allowedPromptVariables(), placeholders,
                    "场景 " + definition.code() + " 的模板占位与变量白名单不一致");
        }
    }

    @Test
    void migrationTemplates_shouldContainSevenUseCasesIncludingAgentSteps() {
        for (String code : AiUseCaseDefinitions.all().keySet()) {
            assertTrue(TestPromptTemplates.template(code).contains("${"),
                    "场景 " + code + " 的模板缺少数据占位");
        }
        assertEquals("你是用户通过 WorkHub 配置的 Agent，依据职责描述完成分派目标。WorkHub 提供持久化轨迹与人工审核。",
                TestPromptTemplates.template(AiUseCaseDefinitions.AGENT_API_STEP).split("\n")[0]);
        assertEquals("你是企业通过 WorkHub 配置的 Agent，依据职责描述自主规划并执行完整任务。",
                TestPromptTemplates.template(AiUseCaseDefinitions.AGENT_CLI_STEP).split("\n")[0]);
        assertTrue(TestPromptTemplates.template(AiUseCaseDefinitions.AGENT_API_STEP).lines()
                .noneMatch(line -> line.contains("TOOL_CALL") || line.contains("toolCatalog")));
        assertTrue(TestPromptTemplates.template(AiUseCaseDefinitions.AGENT_CLI_STEP).lines()
                .noneMatch(line -> line.contains("change.prepare") || line.contains("build.test") || line.contains("release.prepare")));
    }
}
