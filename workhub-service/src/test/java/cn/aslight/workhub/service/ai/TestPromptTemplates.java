package cn.aslight.workhub.service.ai;

import cn.aslight.workhub.model.ai.AiUseCaseConfigEntity;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从提示词迁移 SQL 解析内置 AI 提示词模板，供测试按迁移文本渲染校验。
 */
public final class TestPromptTemplates {

    private static final List<String> MIGRATION_FILES = List.of(
            "V20261105_193000__move_ai_prompts_into_use_case_config.sql",
            "V20261105_203000__intake_sdd_standard_reviewer_and_submit_test_review.sql",
            "V20261110_130000__remove_agent_tool_layer.sql",
            "V20261112_160000__agent_platform_guide.sql",
            "V20261112_161000__tighten_clarification_prompt.sql",
            "V20261114_210000__remove_development_analysis_risks_questions.sql",
            "V20261114_213000__freeze_clarification_prompt.sql");
    private static final Pattern TEMPLATE_BLOCK = Pattern.compile("SET @tpl := '([\\s\\S]*?)';");
    private static final Pattern USE_CASE_CODE = Pattern.compile("'((?:intake|agent)\\.[a-z0-9.-]+)'");

    private static volatile Map<String, String> templates;

    private TestPromptTemplates() {
    }

    public static String template(String useCaseCode) {
        Map<String, String> loaded = templates;
        if (loaded == null) {
            synchronized (TestPromptTemplates.class) {
                if (templates == null) {
                    templates = load();
                }
                loaded = templates;
            }
        }
        String template = loaded.get(useCaseCode);
        if (template == null) {
            throw new IllegalStateException("迁移文件中未找到提示词模板: " + useCaseCode);
        }
        return template;
    }

    public static String render(String useCaseCode, Map<String, Object> variables) {
        try {
            return new AiPromptTemplateRenderer(new ObjectMapper())
                    .render(template(useCaseCode), variables, AiUseCaseDefinitions.require(useCaseCode));
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("提示词模板渲染失败: " + useCaseCode, ex);
        }
    }

    /**
     * 以迁移文件中的模板返回启用配置，供 Agent 适配器测试使用。
     */
    public static AiUseCaseConfigService stubEnabledConfigService() {
        return new AiUseCaseConfigService(null, null) {
            @Override
            public AiUseCaseConfigEntity requireEnabledByCode(String useCaseCode) {
                AiUseCaseConfigEntity entity = new AiUseCaseConfigEntity();
                entity.setUseCaseCode(useCaseCode);
                entity.setEnabled(true);
                entity.setPromptTemplate(template(useCaseCode));
                return entity;
            }
        };
    }

    /**
     * 模拟配置缺失/停用，requireEnabledByCode 抛出与生产一致的异常。
     */
    public static AiUseCaseConfigService missingConfigService() {
        return new AiUseCaseConfigService(null, null) {
            @Override
            public AiUseCaseConfigEntity requireEnabledByCode(String useCaseCode) {
                throw new IllegalStateException("AI 场景配置不存在: " + useCaseCode);
            }
        };
    }

    private static Map<String, String> load() {
        Map<String, String> result = new LinkedHashMap<>();
        for (String migrationFile : MIGRATION_FILES) {
            Path path = Path.of("..", "workhub-bootstrap", "src", "main", "resources", "db", "schema", "mysql", migrationFile);
            if (!Files.isRegularFile(path)) {
                path = Path.of("workhub-bootstrap", "src", "main", "resources", "db", "schema", "mysql", migrationFile);
            }
            String sql;
            try {
                sql = Files.readString(path);
            } catch (IOException ex) {
                throw new IllegalStateException("无法读取提示词迁移文件: " + path.toAbsolutePath(), ex);
            }
            Matcher block = TEMPLATE_BLOCK.matcher(sql);
            while (block.find()) {
                String template = block.group(1);
                Matcher code = USE_CASE_CODE.matcher(sql);
                code.region(block.end(), sql.length());
                if (!code.find()) {
                    throw new IllegalStateException("提示词模板缺少场景编码: " + template.substring(0, 40));
                }
                // 后续迁移的模板覆盖早期版本，与 Flyway 升版语义一致。
                result.put(code.group(1), template);
            }
        }
        if (result.isEmpty()) {
            throw new IllegalStateException("提示词迁移文件未解析到任何模板");
        }
        return Map.copyOf(result);
    }
}
