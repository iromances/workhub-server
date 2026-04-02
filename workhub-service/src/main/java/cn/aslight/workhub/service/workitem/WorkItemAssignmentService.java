package cn.aslight.workhub.service.workitem;

import cn.aslight.workhub.model.workitem.WorkItemAssignRequest;
import cn.aslight.workhub.model.workitem.WorkItemDetailResponse;
import cn.aslight.workhub.dao.workitem.WorkItemMapper;
import cn.aslight.workhub.model.workitem.WorkItemEntity;
import cn.aslight.workhub.integration.wecom.WecomRobotNotifier;
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

    public WorkItemAssignmentService(WorkItemMapper workItemMapper,
                                     WorkItemService workItemService,
                                     WorkItemFollowUpService workItemFollowUpService,
                                     WecomRobotNotifier wecomRobotNotifier) {
        this.workItemMapper = workItemMapper;
        this.workItemService = workItemService;
        this.workItemFollowUpService = workItemFollowUpService;
        this.wecomRobotNotifier = wecomRobotNotifier;
    }

    @Transactional
    public WorkItemDetailResponse assign(Long workItemId, WorkItemAssignRequest request, String operatorUserName) {
        WorkItemEntity workItem = workItemService.requireExisting(workItemId);

        String newOwner = request.getOwnerUserName().trim();
        String newFollower = request.getFollowerUserName().trim();
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
