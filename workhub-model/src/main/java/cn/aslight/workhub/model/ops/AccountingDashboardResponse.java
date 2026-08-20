package cn.aslight.workhub.model.ops;

import java.util.List;

public record AccountingDashboardResponse(AccountingDashboardSummaryResponse summary,
                                          List<AccountingMonitorStatusResponse> monitors) {
}
