package cn.aslight.workhub.service.workitem;

import cn.aslight.workhub.dao.workitem.WorkItemTransitionLogMapper;
import cn.aslight.workhub.integration.wecom.WecomRobotNotifier;
import cn.aslight.workhub.model.workitem.WorkItemDetailResponse;
import cn.aslight.workhub.model.workitem.WorkItemEntity;
import cn.aslight.workhub.model.workitem.WorkItemTransitionLogEntity;
import cn.aslight.workhub.model.workitem.WorkItemTransitionRequest;
import cn.aslight.workhub.model.workitem.WorkItemTransitionResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkItemTransitionServiceTest {

    @Test
    void transition_shouldPauseWorkItemWithPreviousStatusReasonAndPauseDate() {
        WorkItemTransitionLogMapper transitionLogMapper = mock(WorkItemTransitionLogMapper.class);
        WorkItemService workItemService = mock(WorkItemService.class);
        WecomRobotNotifier notifier = mock(WecomRobotNotifier.class);
        WorkItemTransitionService service = new WorkItemTransitionService(transitionLogMapper, workItemService, notifier);

        WorkItemEntity workItem = workItem(101L, "开发中");
        when(workItemService.requireExisting(101L)).thenReturn(workItem);
        when(workItemService.detail(101L)).thenReturn(detail(101L, "已暂停"));
        doAnswer(invocation -> {
            invocation.getArgument(0, WorkItemTransitionLogEntity.class).setId(501L);
            return 1;
        }).when(transitionLogMapper).insert(any(WorkItemTransitionLogEntity.class));
        when(transitionLogMapper.findByWorkItemId(101L)).thenReturn(List.of(
                new WorkItemTransitionResponse(501L, "开发中", "已暂停", "等待外部排期", "admin",
                        LocalDateTime.of(2026, 6, 4, 10, 0))
        ));

        WorkItemTransitionRequest request = new WorkItemTransitionRequest();
        request.setToStatus("已暂停");
        request.setReason("等待外部排期");
        request.setPauseDate(LocalDate.of(2026, 6, 4));

        WorkItemTransitionResponse response = service.transition(101L, request, "admin");

        assertEquals("开发中", response.fromStatus());
        assertEquals("已暂停", response.toStatus());
        verify(workItemService).pauseStatus(101L, "开发中", "等待外部排期", LocalDate.of(2026, 6, 4));
        verify(notifier).notifyStatusChanged(any(WorkItemDetailResponse.class), eq("开发中"), eq("已暂停"),
                eq("等待外部排期"), eq("admin"));
    }

    @Test
    void transition_shouldRestorePausedWorkItemOnlyToPreviousStatus() {
        WorkItemTransitionLogMapper transitionLogMapper = mock(WorkItemTransitionLogMapper.class);
        WorkItemService workItemService = mock(WorkItemService.class);
        WecomRobotNotifier notifier = mock(WecomRobotNotifier.class);
        WorkItemTransitionService service = new WorkItemTransitionService(transitionLogMapper, workItemService, notifier);

        WorkItemEntity workItem = workItem(102L, "已暂停");
        workItem.setPausePreviousStatus("测试中");
        workItem.setPauseReason("等待联调环境");
        workItem.setPauseDate(LocalDate.of(2026, 6, 3));
        when(workItemService.requireExisting(102L)).thenReturn(workItem);
        when(workItemService.detail(102L)).thenReturn(detail(102L, "测试中"));
        doAnswer(invocation -> {
            invocation.getArgument(0, WorkItemTransitionLogEntity.class).setId(502L);
            return 1;
        }).when(transitionLogMapper).insert(any(WorkItemTransitionLogEntity.class));
        when(transitionLogMapper.findByWorkItemId(102L)).thenReturn(List.of(
                new WorkItemTransitionResponse(502L, "已暂停", "测试中", "恢复处理", "admin",
                        LocalDateTime.of(2026, 6, 4, 11, 0))
        ));

        WorkItemTransitionRequest request = new WorkItemTransitionRequest();
        request.setToStatus("测试中");
        request.setReason("恢复处理");

        WorkItemTransitionResponse response = service.transition(102L, request, "admin");

        assertEquals("已暂停", response.fromStatus());
        assertEquals("测试中", response.toStatus());
        verify(workItemService).restorePausedStatus(102L, "测试中");
    }

    @Test
    void transition_shouldRejectPausedWorkItemRestoreToDifferentStatus() {
        WorkItemTransitionLogMapper transitionLogMapper = mock(WorkItemTransitionLogMapper.class);
        WorkItemService workItemService = mock(WorkItemService.class);
        WorkItemTransitionService service = new WorkItemTransitionService(
                transitionLogMapper,
                workItemService,
                mock(WecomRobotNotifier.class)
        );

        WorkItemEntity workItem = workItem(103L, "已暂停");
        workItem.setPausePreviousStatus("待排期");
        when(workItemService.requireExisting(103L)).thenReturn(workItem);

        WorkItemTransitionRequest request = new WorkItemTransitionRequest();
        request.setToStatus("开发中");
        request.setReason("试图跳级恢复");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.transition(103L, request, "admin")
        );

        assertEquals("暂停工作项只能恢复到暂停前状态", exception.getMessage());
        verify(workItemService, never()).restorePausedStatus(eq(103L), any());
        verify(transitionLogMapper, never()).insert(any());
    }

    private WorkItemEntity workItem(Long id, String status) {
        WorkItemEntity entity = new WorkItemEntity();
        entity.setId(id);
        entity.setStatus(status);
        return entity;
    }

    private WorkItemDetailResponse detail(Long id, String status) {
        return new WorkItemDetailResponse(
                id,
                "REQ-1",
                1L,
                "测试项目",
                null,
                null,
                null,
                null,
                "需求",
                "测试工作项",
                null,
                "人工录入",
                "后台",
                "P1",
                null,
                status,
                "admin",
                "owner",
                "follower",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                LocalDateTime.of(2026, 6, 1, 10, 0),
                LocalDateTime.of(2026, 6, 1, 10, 0)
        );
    }
}
