package cn.aslight.workhub.service.ai;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiPromptTemplateRendererTest {

    private final AiPromptTemplateRenderer renderer = new AiPromptTemplateRenderer(new ObjectMapper());

    @Test
    void render_shouldReplaceWhitelistedVariablesAndKeepOtherText() throws Exception {
        var definition = AiUseCaseDefinitions.require(AiUseCaseDefinitions.STRUCTURED_EXTRACT);
        Map<String, Object> variables = new HashMap<>();
        variables.put("sourceChannel", "需求录入");
        variables.put("manualNotes", "导出订单");
        variables.put("attachmentList", "");
        variables.put("attachmentSummaries", "");

        String prompt = renderer.render("来源渠道：${sourceChannel}\n说明：${manualNotes}${attachmentList}${attachmentSummaries}结尾",
                variables, definition);

        assertEquals("来源渠道：需求录入\n说明：导出订单结尾", prompt);
    }

    @Test
    void render_shouldRejectVariableOutsideWhitelist() {
        var definition = AiUseCaseDefinitions.require(AiUseCaseDefinitions.STRUCTURED_EXTRACT);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> renderer.render("模板 ${prompt}", Map.of("prompt", "x"), definition));

        assertTrue(ex.getMessage().contains("未授权变量"));
        assertTrue(ex.getMessage().contains("prompt"));
    }

    @Test
    void render_shouldRejectMissingVariableValue() {
        var definition = AiUseCaseDefinitions.require(AiUseCaseDefinitions.SQL_DRAFT_GENERATE);
        Map<String, Object> variables = new HashMap<>();
        variables.put("intakeRecord", "x");
        variables.put("rawContent", "y");
        variables.put("structuredData", "");
        variables.put("attachmentSummaries", "");
        // attachmentList 缺失

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> renderer.render("模板 ${attachmentList}", variables, definition));

        assertTrue(ex.getMessage().contains("变量缺失"));
    }

    @Test
    void render_shouldRejectLeftoverPlaceholderAfterReplacement() {
        var definition = AiUseCaseDefinitions.require(AiUseCaseDefinitions.STRUCTURED_EXTRACT);
        Map<String, Object> variables = new HashMap<>();
        variables.put("sourceChannel", "渠道");
        variables.put("manualNotes", "残留 ${sourceChannel} 的内容");
        variables.put("attachmentList", "");
        variables.put("attachmentSummaries", "");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> renderer.render("说明：${manualNotes}", variables, definition));

        assertTrue(ex.getMessage().contains("未渲染变量"));
    }

    @Test
    void render_shouldSerializeNonStringValuesAsJson() throws Exception {
        var definition = AiUseCaseDefinitions.require(AiUseCaseDefinitions.DEVELOPMENT_ADJUST);
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("developers", "- dev-a / 研发A");
        variables.put("currentDraft", Map.of("status", "DRAFT"));
        variables.put("userMessage", "调整任务");

        String prompt = renderer.render("草稿：${currentDraft} 反馈：${userMessage} 人员：${developers}", variables, definition);

        assertTrue(prompt.contains("草稿：{\"status\":\"DRAFT\"}"));
    }

    @Test
    void render_shouldRejectBlankTemplate() {
        var definition = AiUseCaseDefinitions.require(AiUseCaseDefinitions.STRUCTURED_EXTRACT);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> renderer.render("  ", Map.of(), definition));

        assertTrue(ex.getMessage().contains("提示词模板为空"));
    }
}
