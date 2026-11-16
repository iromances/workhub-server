package cn.aslight.workhub.service.ai;

import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 提示词模板渲染器。
 *
 * <p>指令文本保存在 AI 任务配置的提示词模板中，业务调用方只提供数据变量；
 * 模板中的 {@code ${var}} 占位必须属于场景白名单且获得非空值，渲染后不允许残留占位。</p>
 */
@Component
public class AiPromptTemplateRenderer {

    private static final Pattern TEMPLATE_VARIABLE_PATTERN = Pattern.compile("\\$\\{([A-Za-z0-9_.-]+)}");

    private final ObjectMapper objectMapper;

    public AiPromptTemplateRenderer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 按场景定义渲染提示词模板。
     *
     * @throws IllegalArgumentException 模板为空、包含未授权变量、变量缺失或渲染后仍有残留占位
     */
    public String render(String template, Map<String, Object> variables,
                         AiUseCaseDefinitions.Definition definition) {
        // 模板尾部换行是有意义的字节，只做空值校验，不做 trim。
        if (template == null || template.isBlank()) {
            throw new IllegalArgumentException("AI 场景提示词模板为空: " + definition.code());
        }
        Map<String, Object> safeVariables = variables == null ? Map.of() : variables;
        Matcher matcher = TEMPLATE_VARIABLE_PATTERN.matcher(template);
        StringBuffer rendered = new StringBuffer();
        while (matcher.find()) {
            String variableName = matcher.group(1);
            if (!definition.allowedPromptVariables().contains(variableName)) {
                throw new IllegalArgumentException("AI 场景提示词包含未授权变量: " + variableName);
            }
            Object value = safeVariables.get(variableName);
            if (value == null) {
                throw new IllegalArgumentException("AI 场景提示词变量缺失: " + variableName);
            }
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(stringify(value)));
        }
        matcher.appendTail(rendered);
        if (TEMPLATE_VARIABLE_PATTERN.matcher(rendered).find()) {
            throw new IllegalArgumentException("AI 场景提示词存在未渲染变量");
        }
        return rendered.toString();
    }

    private String stringify(Object value) {
        if (value instanceof String string) {
            return string;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        return objectMapper.writeValueAsString(value);
    }
}
