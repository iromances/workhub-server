package cn.aslight.workhub.model.ops;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AccountingMonitorSaveRequest(
        @NotBlank @Size(max = 32) String businessLineCode,
        @NotBlank @Size(max = 32) String environmentCode,
        @NotBlank @Size(max = 128) String systemName,
        @NotBlank @Size(max = 128) String displayName,
        @Size(max = 128) String databaseTargetKey,
        @NotBlank @Size(max = 128) String schemaName,
        @NotBlank @Size(max = 64) String ruleProfile,
        @NotNull Boolean enabled,
        @NotNull Boolean dailyEnabled,
        @Size(max = 500) String remark) {
}
