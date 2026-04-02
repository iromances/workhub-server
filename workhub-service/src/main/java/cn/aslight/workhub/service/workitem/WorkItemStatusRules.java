package cn.aslight.workhub.service.workitem;

import java.util.Set;

final class WorkItemStatusRules {

    static final String DRAFT_STATUS = "待整理";
    static final String INITIAL_STATUS = "待澄清";
    private static final Set<String> ALLOWED_STATUSES = Set.of(
            "待澄清",
            "待评估",
            "待排期",
            "开发中",
            "测试中",
            "待发布",
            "已完成",
            "已拒绝",
            "已挂起"
    );
    private static final Set<String> CLOSING_STATUSES = Set.of("已完成", "已拒绝", "已挂起");

    private WorkItemStatusRules() {
    }

    static boolean isAllowedFormalStatus(String status) {
        return ALLOWED_STATUSES.contains(status);
    }

    static boolean requiresReason(String status) {
        return CLOSING_STATUSES.contains(status);
    }
}
