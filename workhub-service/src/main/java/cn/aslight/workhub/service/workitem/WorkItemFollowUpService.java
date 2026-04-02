package cn.aslight.workhub.service.workitem;

import cn.aslight.workhub.model.workitem.WorkItemFollowUpRequest;
import cn.aslight.workhub.model.workitem.WorkItemFollowUpResponse;
import cn.aslight.workhub.dao.workitem.WorkItemFollowUpMapper;
import cn.aslight.workhub.model.workitem.WorkItemFollowUpEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 工作项跟踪记录服务。
 */
@Service
public class WorkItemFollowUpService {

    private final WorkItemFollowUpMapper workItemFollowUpMapper;
    private final WorkItemService workItemService;

    public WorkItemFollowUpService(WorkItemFollowUpMapper workItemFollowUpMapper, WorkItemService workItemService) {
        this.workItemFollowUpMapper = workItemFollowUpMapper;
        this.workItemService = workItemService;
    }

    public List<WorkItemFollowUpResponse> list(Long workItemId) {
        workItemService.requireExisting(workItemId);
        return workItemFollowUpMapper.findByWorkItemId(workItemId);
    }

    @Transactional
    public WorkItemFollowUpResponse add(Long workItemId, WorkItemFollowUpRequest request, String operatorUserName) {
        workItemService.requireExisting(workItemId);
        return addInternal(workItemId, request.getContent().trim(), operatorUserName);
    }

    @Transactional
    public WorkItemFollowUpResponse addSystemNote(Long workItemId, String content, String operatorUserName) {
        workItemService.requireExisting(workItemId);
        return addInternal(workItemId, content.trim(), operatorUserName);
    }

    private WorkItemFollowUpResponse addInternal(Long workItemId, String content, String operatorUserName) {
        WorkItemFollowUpEntity entity = new WorkItemFollowUpEntity();
        entity.setWorkItemId(workItemId);
        entity.setContent(content);
        entity.setOperatorUserName(operatorUserName);
        workItemFollowUpMapper.insert(entity);

        return workItemFollowUpMapper.findByWorkItemId(workItemId).stream()
                .filter(item -> item.id().equals(entity.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("跟踪记录写入后读取失败"));
    }
}
