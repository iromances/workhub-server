package cn.aslight.workhub.service.ai;

import cn.aslight.workhub.model.ai.AiProviderConfigEntity;

import cn.aslight.workhub.service.attachment.AttachmentService.AttachmentFileContext;

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
                       String outputSchema,
                       List<AttachmentFileContext> images) {

    AiApiInvocation(String useCaseCode, AiProviderConfigEntity provider, String apiKey, String model,
                    String reasoningLevel, String speedMode, Integer timeoutSeconds, String prompt,
                    List<String> imagePaths, String outputSchema) {
        this(useCaseCode, provider, apiKey, model, reasoningLevel, speedMode, timeoutSeconds, prompt,
                imagePaths, outputSchema, List.of());
    }

    AiApiInvocation {
        imagePaths = imagePaths == null ? List.of() : List.copyOf(imagePaths);
    }
}
