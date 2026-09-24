package cn.aslight.workhub.model.ai;

import jakarta.validation.constraints.NotBlank;

public record CodexModelCatalogRequest(
        @NotBlank(message = "CLI 命令不能为空") String cliCommand,
        String cliWorkingDirectory,
        Boolean forceRefresh) {
}
