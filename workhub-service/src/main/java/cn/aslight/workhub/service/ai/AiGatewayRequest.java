package cn.aslight.workhub.service.ai;

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
                               String schemaClasspathOverride) {

    public AiGatewayRequest {
        variables = variables == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(variables));
        allowedDirectories = allowedDirectories == null ? List.of() : List.copyOf(allowedDirectories);
        imagePaths = imagePaths == null ? List.of() : List.copyOf(imagePaths);
    }

    public AiGatewayRequest(String useCaseCode, Map<String, Object> variables, Path workingDirectory,
                            List<String> allowedDirectories, List<String> imagePaths, String outputSchema) {
        this(useCaseCode, variables, workingDirectory, allowedDirectories, imagePaths, outputSchema,
                null, null, null, null, null, null);
    }

    public static AiGatewayRequest structured(String useCaseCode,
                                              String prompt,
                                              Path workingDirectory,
                                              List<String> allowedDirectories,
                                              List<String> imagePaths,
                                              String outputSchema) {
        return new AiGatewayRequest(
                useCaseCode,
                Map.of("prompt", prompt == null ? "" : prompt),
                workingDirectory,
                allowedDirectories,
                imagePaths,
                outputSchema
        );
    }
}
