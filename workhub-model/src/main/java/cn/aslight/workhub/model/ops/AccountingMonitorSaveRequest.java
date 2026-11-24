package cn.aslight.workhub.model.ops;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalTime;

public record AccountingMonitorSaveRequest(
        @NotBlank @Size(max = 32) String businessLineCode,
        @NotBlank @Size(max = 32) String environmentCode,
        @NotBlank @Size(max = 128) String systemName,
        @NotBlank @Size(max = 128) String displayName,
        @NotBlank @Size(max = 32) String scenarioType,
        LocalTime expectedTime,
        @NotNull LocalTime checkTime,
        @Size(max = 64) String expectedCountSqlCode,
        Integer expectedFixedCount,
        @Size(max = 64) String actualCountSqlCode,
        @Size(max = 64) String successCountSqlCode,
        @NotNull BigDecimal toleranceRate,
        @NotNull Boolean alertEnabled,
        @NotNull Boolean enabled,
        @Size(max = 500) String remark) {
}
