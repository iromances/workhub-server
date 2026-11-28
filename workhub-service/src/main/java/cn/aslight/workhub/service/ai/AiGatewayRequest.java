package cn.aslight.workhub.service.ai;

import cn.aslight.workhub.service.attachment.AttachmentService.AttachmentFileContext;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统内部统一 AI 网关请求。
 *
 * <p>业务调用方提供稳定 useCase、模板变量和受控执行上下文；受信任的服务端调用可覆盖 Provider 和调用参数，密钥不可覆盖。</p>
 */
public record AiGatewayRequest(String useCaseCode,
                               Map<String, Object> variables,
                               Path workingDirectory,
                               List<String> allowedDirectories,
                               List<String> imagePaths,
                               String outputSchema,
                               Long providerConfigIdOverride,
                               String modelOverride,
                               String reasoningLevelOverride,
                               String speedModeOverride,
                               Integer timeoutSecondsOverride,
                               String schemaClasspathOverride,
                               AiResultRecovery resultRecovery,
                               List<AttachmentFileContext> images) {

    public AiGatewayRequest(String useCaseCode, Map<String, Object> variables, Path workingDirectory,
                            List<String> allowedDirectories, List<String> imagePaths, String outputSchema,
                            Long providerConfigIdOverride, String modelOverride, String reasoningLevelOverride,
                            String speedModeOverride, Integer timeoutSecondsOverride, String schemaClasspathOverride,
                            AiResultRecovery resultRecovery) {
        this(useCaseCode, variables, workingDirectory, allowedDirectories, imagePaths, outputSchema, providerConfigIdOverride,
                modelOverride, reasoningLevelOverride, speedModeOverride, timeoutSecondsOverride, schemaClasspathOverride, resultRecovery, List.of());
    }

    public AiGatewayRequest withImages(List<AttachmentFileContext> images) {
        return new AiGatewayRequest(useCaseCode, variables, workingDirectory, allowedDirectories, imagePaths, outputSchema,
                providerConfigIdOverride, modelOverride, reasoningLevelOverride, speedModeOverride, timeoutSecondsOverride,
                schemaClasspathOverride, resultRecovery, images);
    }

    AiGatewayRequest withLocalImages(List<AttachmentFileContext> files) {
        var paths = new java.util.ArrayList<>(imagePaths);
        var dirs = new java.util.LinkedHashSet<>(allowedDirectories);
        for (var file : files) {
            Path path = Path.of(file.storagePath());
            paths.add(path.toString());
            dirs.add(path.getParent().toString());
        }
        return new AiGatewayRequest(useCaseCode, variables, workingDirectory, List.copyOf(dirs), paths, outputSchema,
                providerConfigIdOverride, modelOverride, reasoningLevelOverride, speedModeOverride, timeoutSecondsOverride,
                schemaClasspathOverride, resultRecovery);
    }

    public AiGatewayRequest(String useCaseCode, Map<String, Object> variables, Path workingDirectory,
                            List<String> allowedDirectories, List<String> imagePaths, String outputSchema,
                            Long providerConfigIdOverride, String modelOverride, String reasoningLevelOverride,
                            String speedModeOverride, Integer timeoutSecondsOverride, String schemaClasspathOverride) {
        this(useCaseCode, variables, workingDirectory, allowedDirectories, imagePaths, outputSchema,
                providerConfigIdOverride, modelOverride, reasoningLevelOverride, speedModeOverride, timeoutSecondsOverride, schemaClasspathOverride, null);
    }

    public AiGatewayRequest withResultRecovery(AiResultRecovery recovery) {
        return new AiGatewayRequest(useCaseCode, variables, workingDirectory, allowedDirectories, imagePaths, outputSchema,
                providerConfigIdOverride, modelOverride, reasoningLevelOverride, speedModeOverride, timeoutSecondsOverride, schemaClasspathOverride, recovery, images);
    }

    public AiGatewayRequest {
        images = images == null ? List.of() : List.copyOf(images);
        variables = variables == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(variables));
        allowedDirectories = allowedDirectories == null ? List.of() : List.copyOf(allowedDirectories);
        imagePaths = imagePaths == null ? List.of() : List.copyOf(imagePaths);
    }

    public AiGatewayRequest(String useCaseCode, Map<String, Object> variables, Path workingDirectory,
                            List<String> allowedDirectories, List<String> imagePaths, String outputSchema) {
        this(useCaseCode, variables, workingDirectory, allowedDirectories, imagePaths, outputSchema,
                null, null, null, null, null, null);
    }

    public static AiGatewayRequest withVariables(String useCaseCode,
                                                 Map<String, Object> variables,
                                                 Path workingDirectory,
                                                 List<String> allowedDirectories,
                                                 List<String> imagePaths,
                                                 String outputSchema) {
        return new AiGatewayRequest(
                useCaseCode,
                variables,
                workingDirectory,
                allowedDirectories,
                imagePaths,
                outputSchema
        );
    }
}
