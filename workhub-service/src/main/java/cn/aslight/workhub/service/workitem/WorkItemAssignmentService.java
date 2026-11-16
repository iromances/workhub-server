package cn.aslight.workhub.service.workitem;

import cn.aslight.workhub.model.workitem.WorkItemAssignRequest;
import cn.aslight.workhub.model.workitem.WorkItemDetailResponse;
import cn.aslight.workhub.dao.system.UserMapper;
import cn.aslight.workhub.dao.workitem.WorkItemMapper;
import cn.aslight.workhub.model.workitem.WorkItemEntity;
import cn.aslight.workhub.integration.wecom.WecomRobotNotifier;
import cn.aslight.workhub.model.system.UserOptionResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 工作项指派服务。
 */
@Service
public class WorkItemAssignmentService {

    private final WorkItemMapper workItemMapper;
    private final WorkItemService workItemService;
    private final WorkItemFollowUpService workItemFollowUpService;
    private final WecomRobotNotifier wecomRobotNotifier;
    private final UserMapper userMapper;

    public WorkItemAssignmentService(WorkItemMapper workItemMapper,
                                     WorkItemService workItemService,
                                     WorkItemFollowUpService workItemFollowUpService,
                                     WecomRobotNotifier wecomRobotNotifier,
                                     UserMapper userMapper) {
        this.workItemMapper = workItemMapper;
        this.workItemService = workItemService;
        this.workItemFollowUpService = workItemFollowUpService;
        this.wecomRobotNotifier = wecomRobotNotifier;
        this.userMapper = userMapper;
    }

    @Transactional
    public WorkItemDetailResponse assign(Long workItemId, WorkItemAssignRequest request, String operatorUserName) {
        WorkItemEntity workItem = workItemService.requireExisting(workItemId);

        String newOwner = request.getOwnerUserName().trim();
        String newFollower = request.getFollowerUserName().trim();
        requireActiveAccount(newOwner, "负责人");
        requireActiveAccount(newFollower, "跟进人");
        String reason = trimToNull(request.getReason());
        if (newOwner.equals(workItem.getOwnerUserName()) && newFollower.equals(workItem.getFollowerUserName())) {
            throw new IllegalArgumentException("负责人和跟进人未发生变化");
        }

        String previousOwner = workItem.getOwnerUserName();
        String previousFollower = workItem.getFollowerUserName();
        workItemMapper.updateAssignment(workItemId, newOwner, newFollower);

        String followUp = "指派调整：负责人 "
                + safe(previousOwner) + " -> " + newOwner
                + "，跟进人 " + safe(previousFollower) + " -> " + newFollower
                + (reason == null ? "" : "，说明：" + reason);
        workItemFollowUpService.addSystemNote(workItemId, followUp, operatorUserName);

        WorkItemDetailResponse detail = workItemService.detail(workItemId);
        wecomRobotNotifier.notifyAssignment(detail, previousOwner, previousFollower, reason, operatorUserName);
        return detail;
    }

    /**
     * 指派对象必须是账号管理中的活跃账号。
     */
    private void requireActiveAccount(String userName, String label) {
        if (userName == null || userName.isBlank()) {
            return;
        }
        boolean exists = userMapper.findActiveUsers().stream()
                .anyMatch((UserOptionResponse user) -> userName.equals(user.userName()));
        if (!exists) {
            throw new IllegalArgumentException("工作项" + label + "「" + userName + "」不是有效账号，请先在账号管理中创建");
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String safe(String value) {
        return value == null || value.trim().isEmpty() ? "-" : value.trim();
    }
}
