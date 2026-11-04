package cn.aslight.workhub.service.ai;

import cn.aslight.workhub.model.ai.AiProviderConfigEntity;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/** CLI 协议与模型厂商分开；GLM 也可以由其他 CLI 调用。 */
public final class AiCliType {
    private AiCliType() { }

    public static String resolve(String configured, String command) {
        if (configured != null && !configured.isBlank()) {
            String type = configured.trim().toUpperCase(Locale.ROOT);
            if (!Set.of("CODEX", "ZCODE").contains(type)) throw new IllegalArgumentException("CLI 类型只支持 CODEX 或 ZCODE");
            return type;
        }
        String file = command == null || command.isBlank() ? "" : Path.of(command).getFileName().toString().toLowerCase(Locale.ROOT);
        if (file.equals("zcode") || file.equals("zcode.cjs") || file.startsWith("zcode-")) return "ZCODE";
        if (file.equals("codex") || file.startsWith("codex-")) return "CODEX";
        throw new IllegalArgumentException("请明确选择 CLI 类型，不将其他 CLI 当作 Codex 执行");
    }

    public static boolean is(AiProviderConfigEntity provider, String type) {
        if (!"CLI".equalsIgnoreCase(provider.getChannelType())) return false;
        try { return type.equals(resolve(provider.getCliType(), provider.getCliCommand())); }
        catch (IllegalArgumentException ex) { return false; }
    }

    public static void validate(AiProviderConfigEntity provider) {
        String type = resolve(provider.getCliType(), provider.getCliCommand());
        if (!"ZCODE".equals(type)) return;
        selection(provider.getDefaultModel(), provider.getDefaultReasoningLevel());
        if (!"STANDARD".equalsIgnoreCase(provider.getDefaultSpeedMode())) throw new IllegalArgumentException("ZCode CLI 速度使用 STANDARD；不套用其他 CLI 的速度参数");
        for (String value : new String[]{provider.getCliRuntimeCommand(), provider.getCliBuiltinConfigPath(), provider.getCliPersonalConfigPath()}) {
            if (value != null && (value.length() > 1000 || value.indexOf('\0') >= 0 || value.indexOf('\n') >= 0)) throw new IllegalArgumentException("ZCode 运行路径无效");
        }
    }

    public static java.util.Map<String, Object> selection(String model, String reasoning) {
        if (model == null || model.length() > 128 || !model.equals(model.trim()) || model.chars().anyMatch(Character::isWhitespace)
                || model.contains("$") || model.indexOf('/') <= 0 || model.endsWith("/")) throw new IllegalArgumentException("ZCode 模型必须填写 Provider/Model 标识，推理档位单独填写");
        if (reasoning == null || reasoning.isBlank() || reasoning.length() > 32 || !reasoning.equals(reasoning.trim())) throw new IllegalArgumentException("ZCode 推理档位不能为空，须使用其模型配置中的值");
        int split = model.indexOf('/');
        return java.util.Map.of("providerId", model.substring(0, split), "modelId", model.substring(split + 1), "options", java.util.Map.of("reasoningLevel", reasoning));
    }
}
