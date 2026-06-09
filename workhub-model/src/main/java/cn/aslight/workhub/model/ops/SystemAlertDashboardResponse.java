package cn.aslight.workhub.model.ops;

import java.util.List;

public record SystemAlertDashboardResponse(Integer totalCount,
                                           Integer page,
                                           Integer pageSize,
                                           List<SystemAlertSubsystemResponse> subsystems,
                                           List<SystemAlertSubsystemSummaryResponse> summaries,
                                           List<SystemAlertEventResponse> events) {
}
