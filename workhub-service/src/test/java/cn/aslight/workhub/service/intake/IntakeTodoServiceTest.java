package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.dao.intake.IntakeHistoryMapper;
import cn.aslight.workhub.dao.intake.IntakeMapper;
import cn.aslight.workhub.dao.intake.IntakeTodoMapper;
import cn.aslight.workhub.model.intake.IntakeHistoryEntity;
import cn.aslight.workhub.model.intake.IntakeRecordEntity;
import cn.aslight.workhub.model.intake.IntakeTodoCreateRequest;
import cn.aslight.workhub.model.intake.IntakeTodoEntity;
import cn.aslight.workhub.model.intake.IntakeTodoStatusRequest;
import cn.aslight.workhub.model.intake.IntakeTodoUpdateRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

class IntakeTodoServiceTest {

    @Test
    void create_shouldAddPendingTodoAndRecordHistory() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeTodoMapper todoMapper = mock(IntakeTodoMapper.class);
        IntakeHistoryMapper historyMapper = mock(IntakeHistoryMapper.class);
        IntakeTodoService service = new IntakeTodoService(intakeMapper, todoMapper, historyMapper);

        IntakeRecordEntity intake = new IntakeRecordEntity();
        intake.setId(11L);
        when(intakeMapper.findById(11L)).thenReturn(intake);
        doAnswer(invocation -> {
            invocation.getArgument(0, IntakeTodoEntity.class).setId(101L);
            return 1;
        }).when(todoMapper).insert(any(IntakeTodoEntity.class));
        when(todoMapper.findByIntakeId(11L)).thenReturn(List.of(
                responseEntity(101L, "待处理", null)
        ));

        IntakeTodoCreateRequest request = new IntakeTodoCreateRequest();
        request.setTitle("确认资方接口口径");
        request.setContent("需要和资方确认还款状态映射。");
        request.setAssigneeUserName("shihao");
        request.setPlannedAt(LocalDateTime.of(2026, 6, 5, 10, 0));

        var response = service.create(11L, request, "admin");

        assertEquals("待处理", response.status());
        assertEquals("确认资方接口口径", response.title());
        ArgumentCaptor<IntakeTodoEntity> todoCaptor = forClass(IntakeTodoEntity.class);
        verify(todoMapper).insert(todoCaptor.capture());
        assertEquals(11L, todoCaptor.getValue().getIntakeId());
        assertEquals("待处理", todoCaptor.getValue().getTodoStatus());
        ArgumentCaptor<IntakeHistoryEntity> historyCaptor = forClass(IntakeHistoryEntity.class);
        verify(historyMapper).insert(historyCaptor.capture());
        assertEquals("新增需求待办", historyCaptor.getValue().getActionSummary());
    }

    @Test
    void updateStatus_shouldRequireResultWhenCompletingTodo() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeTodoMapper todoMapper = mock(IntakeTodoMapper.class);
        IntakeTodoService service = new IntakeTodoService(intakeMapper, todoMapper, mock(IntakeHistoryMapper.class));

        IntakeRecordEntity intake = new IntakeRecordEntity();
        intake.setId(12L);
        when(intakeMapper.findById(12L)).thenReturn(intake);
        when(todoMapper.findById(201L)).thenReturn(responseEntity(201L, "处理中", null));

        IntakeTodoStatusRequest request = new IntakeTodoStatusRequest();
        request.setStatus("已完成");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.updateStatus(12L, 201L, request, "admin")
        );

        assertEquals("待办完成时必须填写处理结果", exception.getMessage());
    }

    @Test
    void updateStatus_shouldCompleteTodoWithResultAndHistory() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeTodoMapper todoMapper = mock(IntakeTodoMapper.class);
        IntakeHistoryMapper historyMapper = mock(IntakeHistoryMapper.class);
        IntakeTodoService service = new IntakeTodoService(intakeMapper, todoMapper, historyMapper);

        IntakeRecordEntity intake = new IntakeRecordEntity();
        intake.setId(13L);
        when(intakeMapper.findById(13L)).thenReturn(intake);
        when(todoMapper.findById(301L)).thenReturn(responseEntity(301L, "处理中", null));
        when(todoMapper.findByIntakeId(13L)).thenReturn(List.of(
                responseEntity(301L, "已完成", "已确认接口按 SUCCESS/FAIL 映射。")
        ));

        IntakeTodoStatusRequest request = new IntakeTodoStatusRequest();
        request.setStatus("已完成");
        request.setProcessResult("已确认接口按 SUCCESS/FAIL 映射。");

        var response = service.updateStatus(13L, 301L, request, "admin");

        assertEquals("已完成", response.status());
        assertEquals("已确认接口按 SUCCESS/FAIL 映射。", response.processResult());
        verify(todoMapper).updateStatus(any());
        ArgumentCaptor<IntakeHistoryEntity> historyCaptor = forClass(IntakeHistoryEntity.class);
        verify(historyMapper).insert(historyCaptor.capture());
        assertEquals("更新需求待办状态", historyCaptor.getValue().getActionSummary());
    }

    @Test
    void update_shouldModifyTodoBodyAndRecordHistory() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeTodoMapper todoMapper = mock(IntakeTodoMapper.class);
        IntakeHistoryMapper historyMapper = mock(IntakeHistoryMapper.class);
        IntakeTodoService service = new IntakeTodoService(intakeMapper, todoMapper, historyMapper);

        IntakeRecordEntity intake = new IntakeRecordEntity();
        intake.setId(14L);
        when(intakeMapper.findById(14L)).thenReturn(intake);
        when(todoMapper.findById(401L)).thenReturn(responseEntity(401L, "待处理", null));
        when(todoMapper.findByIntakeId(14L)).thenReturn(List.of(
                responseEntity(401L, "待处理", null)
        ));

        IntakeTodoUpdateRequest request = new IntakeTodoUpdateRequest();
        request.setTitle("补充测试验收点");
        request.setContent("覆盖失败回调和重复通知。");
        request.setAssigneeUserName("qa");

        service.update(14L, 401L, request, "admin");

        verify(todoMapper).updateEditableFields(any());
        ArgumentCaptor<IntakeHistoryEntity> historyCaptor = forClass(IntakeHistoryEntity.class);
        verify(historyMapper).insert(historyCaptor.capture());
        assertEquals("更新需求待办", historyCaptor.getValue().getActionSummary());
    }

    private IntakeTodoEntity responseEntity(Long id, String status, String result) {
        IntakeTodoEntity entity = new IntakeTodoEntity();
        entity.setId(id);
        entity.setIntakeId(id < 200 ? 11L : id < 300 ? 12L : id < 400 ? 13L : 14L);
        entity.setTitle("确认资方接口口径");
        entity.setContent("需要和资方确认还款状态映射。");
        entity.setTodoStatus(status);
        entity.setAssigneeUserName("shihao");
        entity.setPlannedAt(LocalDateTime.of(2026, 6, 5, 10, 0));
        entity.setCompletedAt("已完成".equals(status) ? LocalDateTime.of(2026, 6, 5, 18, 0) : null);
        entity.setProcessResult(result);
        entity.setCreatedAt(LocalDateTime.of(2026, 6, 4, 10, 0));
        entity.setUpdatedAt(LocalDateTime.of(2026, 6, 4, 10, 0));
        return entity;
    }
}
