package cn.aslight.workhub.service.ai;

import cn.aslight.workhub.model.ai.AiProviderConfigEntity;

import java.util.List;

/**
 * 已完成配置解析和提示词渲染的 API 调用参数。
 */
record AiApiInvocation(String useCaseCode,
                       AiProviderConfigEntity provider,
                       String apiKey,
                       String model,
                       String reasoningLevel,
                       String speedMode,
                       Integer timeoutSeconds,
                       String prompt,
                       List<String> imagePaths,
                       String outputSchema) {

    AiApiInvocation {
        imagePaths = imagePaths == null ? List.of() : List.copyOf(imagePaths);
    }
}
