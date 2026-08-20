package cn.aslight.workhub.model.ops;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public record AccountingManualRunRequest(@NotNull Long configId,
                                         @NotNull LocalDateTime startTime,
                                         @NotNull LocalDateTime endTime,
                                         List<String> ruleCodes,
                                         String requestKey) {
}
