package cn.aslight.workhub.service.workitem;

import java.util.List;
import java.util.Map;
import java.util.Set;

final class WorkItemStatusRules {

    static final String DRAFT_STATUS = "待整理";
    static final String INITIAL_STATUS = "待澄清";
    static final String STATUS_PENDING_EVALUATION = "待评估";
    static final String STATUS_PENDING_SCHEDULING = "待排期";
    static final String STATUS_IN_DEVELOPMENT = "开发中";
    static final String STATUS_TESTING = "测试中";
    static final String STATUS_PENDING_RELEASE = "待发布";
    static final String STATUS_COMPLETED = "已完成";
    static final String STATUS_REJECTED = "已拒绝";
    static final String STATUS_SUSPENDED = "已挂起";
    private static final Set<String> ALLOWED_STATUSES = Set.of(
            INITIAL_STATUS,
            STATUS_PENDING_EVALUATION,
            STATUS_PENDING_SCHEDULING,
            STATUS_IN_DEVELOPMENT,
            STATUS_TESTING,
            STATUS_PENDING_RELEASE,
            STATUS_COMPLETED,
            STATUS_REJECTED,
            STATUS_SUSPENDED
    );
    private static final Set<String> CLOSING_STATUSES = Set.of(STATUS_COMPLETED, STATUS_REJECTED, STATUS_SUSPENDED);
    private static final Set<String> TERMINAL_STATUSES = Set.of(STATUS_COMPLETED, STATUS_REJECTED, STATUS_SUSPENDED);
    private static final Map<String, List<String>> ALLOWED_TRANSITIONS = Map.of(
            INITIAL_STATUS, List.of(STATUS_PENDING_EVALUATION, STATUS_REJECTED, STATUS_SUSPENDED),
            STATUS_PENDING_EVALUATION, List.of(STATUS_PENDING_SCHEDULING, STATUS_REJECTED, STATUS_SUSPENDED),
            STATUS_PENDING_SCHEDULING, List.of(STATUS_IN_DEVELOPMENT, STATUS_REJECTED, STATUS_SUSPENDED),
            STATUS_IN_DEVELOPMENT, List.of(STATUS_TESTING, STATUS_REJECTED, STATUS_SUSPENDED),
            STATUS_TESTING, List.of(STATUS_PENDING_RELEASE, STATUS_REJECTED, STATUS_SUSPENDED),
            STATUS_PENDING_RELEASE, List.of(STATUS_COMPLETED, STATUS_REJECTED, STATUS_SUSPENDED)
    );

    private WorkItemStatusRules() {
    }

    static boolean isAllowedFormalStatus(String status) {
        return ALLOWED_STATUSES.contains(status);
    }

    static boolean requiresReason(String status) {
        return CLOSING_STATUSES.contains(status);
    }

    static boolean isTerminalStatus(String status) {
        return TERMINAL_STATUSES.contains(status);
    }

    static boolean canTransition(String fromStatus, String toStatus) {
        List<String> nextStatuses = ALLOWED_TRANSITIONS.get(fromStatus);
        return nextStatuses != null && nextStatuses.contains(toStatus);
    }
}
