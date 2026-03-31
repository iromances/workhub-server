package cn.aslight.workhub.domain.workitem.service;

import cn.aslight.workhub.domain.workitem.dto.WorkItemTransitionRequest;
import cn.aslight.workhub.domain.workitem.dto.WorkItemTransitionResponse;
import cn.aslight.workhub.domain.workitem.mapper.WorkItemTransitionLogMapper;
import cn.aslight.workhub.domain.workitem.model.WorkItemEntity;
import cn.aslight.workhub.domain.workitem.model.WorkItemTransitionLogEntity;
import cn.aslight.workhub.integration.wecom.WecomRobotNotifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class WorkItemTransitionService {

    private final WorkItemTransitionLogMapper workItemTransitionLogMapper;
    private final WorkItemService workItemService;
    private final WecomRobotNotifier wecomRobotNotifier;

    public WorkItemTransitionService(WorkItemTransitionLogMapper workItemTransitionLogMapper,
                                     WorkItemService workItemService,
                                     WecomRobotNotifier wecomRobotNotifier) {
        this.workItemTransitionLogMapper = workItemTransitionLogMapper;
        this.workItemService = workItemService;
        this.wecomRobotNotifier = wecomRobotNotifier;
    }

    public List<WorkItemTransitionResponse> list(Long workItemId) {
        workItemService.requireExisting(workItemId);
        return workItemTransitionLogMapper.findByWorkItemId(workItemId);
    }

    @Transactional
    public WorkItemTransitionResponse transition(Long workItemId,
                                                 WorkItemTransitionRequest request,
                                                 String operatorUserName) {
        WorkItemEntity workItem = workItemService.requireExisting(workItemId);
        String toStatus = request.getToStatus().trim();
        String reason = trimToNull(request.getReason());

        if (!WorkItemStatusRules.isAllowedFormalStatus(toStatus)) {
            throw new IllegalArgumentException("目标状态不合法");
        }
        if (WorkItemStatusRules.DRAFT_STATUS.equals(toStatus)) {
            throw new IllegalArgumentException("正式工作项不能流转到待整理");
        }
        if (toStatus.equals(workItem.getStatus())) {
            throw new IllegalArgumentException("工作项已经处于目标状态");
        }
        if (WorkItemStatusRules.requiresReason(toStatus) && reason == null) {
            throw new IllegalArgumentException("关闭类状态必须填写结论说明");
        }

        LocalDateTime finishedAt = "已完成".equals(toStatus) ? LocalDateTime.now() : null;
        workItemService.updateStatus(workItemId, toStatus, finishedAt);

        WorkItemTransitionLogEntity entity = new WorkItemTransitionLogEntity();
        entity.setWorkItemId(workItemId);
        entity.setFromStatus(workItem.getStatus());
        entity.setToStatus(toStatus);
        entity.setReason(reason);
        entity.setOperatorUserName(operatorUserName);
        workItemTransitionLogMapper.insert(entity);

        wecomRobotNotifier.notifyStatusChanged(
                workItemService.detail(workItemId),
                workItem.getStatus(),
                toStatus,
                reason,
                operatorUserName
        );

        return workItemTransitionLogMapper.findByWorkItemId(workItemId).stream()
                .filter(item -> item.id().equals(entity.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("状态流转写入后读取失败"));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
