package cn.aslight.workhub.model.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ZCodeModelCatalogRequest(
        @NotBlank(message = "CLI 命令不能为空") @Size(max = 1000) String cliCommand,
        @Size(max = 1000) String cliRuntimeCommand,
        @Size(max = 1000) String cliBuiltinConfigPath,
        @Size(max = 1000) String cliPersonalConfigPath,
        Boolean forceRefresh) {
    public AiProviderConfigEntity provider() {
        var provider = new AiProviderConfigEntity();
        provider.setCliCommand(cliCommand);
        provider.setCliRuntimeCommand(cliRuntimeCommand);
        provider.setCliBuiltinConfigPath(cliBuiltinConfigPath);
        provider.setCliPersonalConfigPath(cliPersonalConfigPath);
        return provider;
    }
}
