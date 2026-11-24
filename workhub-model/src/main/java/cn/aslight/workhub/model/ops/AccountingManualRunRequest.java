package cn.aslight.workhub.model.ops;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * REQ-d227e09d：手动触发场景核对（按核对日，窗口为该日 00:00 至次日 00:00）。
 */
public record AccountingManualRunRequest(@NotNull Long configId,
                                         LocalDate checkDate,
                                         String requestKey) {
}
