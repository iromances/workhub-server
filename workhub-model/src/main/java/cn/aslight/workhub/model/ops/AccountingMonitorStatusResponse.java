package cn.aslight.workhub.model.ops;

public record AccountingMonitorStatusResponse(AccountingMonitorConfigEntity config,
                                              AccountingRunEntity latestRun) {
}
