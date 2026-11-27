package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.dao.intake.IntakeHistoryMapper;
import cn.aslight.workhub.dao.intake.IntakeMapper;
import cn.aslight.workhub.dao.intake.IntakeTodoMapper;
import cn.aslight.workhub.model.attachment.AttachmentResponse;
import cn.aslight.workhub.model.intake.IntakeHistoryEntity;
import cn.aslight.workhub.model.intake.IntakeRecordEntity;
import cn.aslight.workhub.model.intake.IntakeTodoCreateRequest;
import cn.aslight.workhub.model.intake.IntakeTodoEntity;
import cn.aslight.workhub.model.intake.IntakeTodoStatusRequest;
import cn.aslight.workhub.model.intake.IntakeTodoResponse;
import cn.aslight.workhub.model.intake.IntakeTodoUpdateRequest;
import cn.aslight.workhub.service.attachment.AttachmentService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IntakeTodoServiceTest {

    @Test
    void create_shouldAddPendingTodoAndRecordHistory() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeTodoMapper todoMapper = mock(IntakeTodoMapper.class);
        IntakeHistoryMapper historyMapper = mock(IntakeHistoryMapper.class);
        IntakeTodoService service = new IntakeTodoService(intakeMapper, todoMapper, historyMapper, mock(AttachmentService.class));

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
        assertEquals(List.of(), response.screenshots());
        ArgumentCaptor<IntakeTodoEntity> todoCaptor = forClass(IntakeTodoEntity.class);
        verify(todoMapper).insert(todoCaptor.capture());
        assertEquals(11L, todoCaptor.getValue().getIntakeId());
        assertEquals("待处理", todoCaptor.getValue().getTodoStatus());
        ArgumentCaptor<IntakeHistoryEntity> historyCaptor = forClass(IntakeHistoryEntity.class);
        verify(historyMapper).insert(historyCaptor.capture());
        assertEquals("新增需求待办", historyCaptor.getValue().getActionSummary());
    }

    @Test
    void list_shouldAttachScreenshotsToEachTodo() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeTodoMapper todoMapper = mock(IntakeTodoMapper.class);
        AttachmentService attachmentService = mock(AttachmentService.class);
        IntakeTodoService service = new IntakeTodoService(intakeMapper, todoMapper, mock(IntakeHistoryMapper.class), attachmentService);
        IntakeRecordEntity intake = new IntakeRecordEntity();
        intake.setId(21L);
        when(intakeMapper.findById(21L)).thenReturn(intake);
        when(todoMapper.findByIntakeId(21L)).thenReturn(List.of(
                responseEntity(601L, "待处理", null),
                responseEntity(602L, "待处理", null)
        ));
        when(attachmentService.mapTodoScreenshots(List.of(601L, 602L))).thenReturn(Map.of(
                601L, List.of(screenshot(7001L, "first.png"), screenshot(7002L, "second.png"))
        ));

        List<IntakeTodoResponse> response = service.list(21L);

        assertEquals(
                List.of(7001L, 7002L),
                response.get(0).screenshots().stream().map(AttachmentResponse::id).toList()
        );
        assertEquals(List.of(), response.get(1).screenshots());
    }

    @Test
    void updateStatus_shouldRequireResultWhenCompletingTodo() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeTodoMapper todoMapper = mock(IntakeTodoMapper.class);
        IntakeTodoService service = new IntakeTodoService(intakeMapper, todoMapper, mock(IntakeHistoryMapper.class), mock(AttachmentService.class));

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
        IntakeTodoService service = new IntakeTodoService(intakeMapper, todoMapper, historyMapper, mock(AttachmentService.class));

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
        IntakeTodoService service = new IntakeTodoService(intakeMapper, todoMapper, historyMapper, mock(AttachmentService.class));

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

    @Test
    void saveScreenshots_shouldRejectNonImageWithoutWriting() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeTodoMapper todoMapper = mock(IntakeTodoMapper.class);
        IntakeHistoryMapper historyMapper = mock(IntakeHistoryMapper.class);
        AttachmentService attachmentService = mock(AttachmentService.class);
        IntakeTodoService service = new IntakeTodoService(intakeMapper, todoMapper, historyMapper, attachmentService);
        IntakeRecordEntity intake = new IntakeRecordEntity();
        intake.setId(15L);
        when(intakeMapper.findById(15L)).thenReturn(intake);
        when(todoMapper.findById(501L)).thenReturn(ownedEntity(501L));
        List<MultipartFile> files = List.of(
                new MockMultipartFile("files", "error.png", "image/png", "png".getBytes()),
                new MockMultipartFile("files", "notes.pdf", "application/pdf", "pdf".getBytes())
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.saveScreenshots(15L, 501L, files, List.of(), "admin")
        );

        assertEquals("截图仅支持图片文件：notes.pdf", exception.getMessage());
        verify(attachmentService, never()).deleteTodoScreenshots(any(), any());
        verify(attachmentService, never()).saveTodoScreenshots(any(), any());
        verify(historyMapper, never()).insert(any());
    }

    @Test
    void saveScreenshots_shouldRejectScreenshotNotBelongingToTodo() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeTodoMapper todoMapper = mock(IntakeTodoMapper.class);
        IntakeHistoryMapper historyMapper = mock(IntakeHistoryMapper.class);
        AttachmentService attachmentService = mock(AttachmentService.class);
        IntakeTodoService service = new IntakeTodoService(intakeMapper, todoMapper, historyMapper, attachmentService);
        IntakeRecordEntity intake = new IntakeRecordEntity();
        intake.setId(15L);
        when(intakeMapper.findById(15L)).thenReturn(intake);
        when(todoMapper.findById(501L)).thenReturn(ownedEntity(501L));
        when(attachmentService.mapTodoScreenshots(List.of(501L))).thenReturn(Map.of(
                501L, List.of(screenshot(7001L, "old.png"))
        ));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.saveScreenshots(15L, 501L, List.of(), List.of(7999L), "admin")
        );

        assertEquals("截图不存在或不属于当前待办", exception.getMessage());
        verify(attachmentService, never()).deleteTodoScreenshots(any(), any());
        verify(attachmentService, never()).saveTodoScreenshots(any(), any());
        verify(historyMapper, never()).insert(any());
    }

    @Test
    void saveScreenshots_shouldDeleteRemovedAppendNewAndRecordHistory() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeTodoMapper todoMapper = mock(IntakeTodoMapper.class);
        IntakeHistoryMapper historyMapper = mock(IntakeHistoryMapper.class);
        AttachmentService attachmentService = mock(AttachmentService.class);
        IntakeTodoService service = new IntakeTodoService(intakeMapper, todoMapper, historyMapper, attachmentService);
        IntakeRecordEntity intake = new IntakeRecordEntity();
        intake.setId(15L);
        when(intakeMapper.findById(15L)).thenReturn(intake);
        when(todoMapper.findById(501L)).thenReturn(ownedEntity(501L));
        when(attachmentService.mapTodoScreenshots(List.of(501L))).thenReturn(Map.of(
                501L, List.of(screenshot(7001L, "old.png"))
        ));
        MockMultipartFile newFile = new MockMultipartFile("files", "new.png", "image/png", "new".getBytes());

        service.saveScreenshots(15L, 501L, List.of(newFile), List.of(7001L), "admin");

        verify(attachmentService).deleteTodoScreenshots(501L, List.of(7001L));
        verify(attachmentService).saveTodoScreenshots(501L, List.of(newFile));
        ArgumentCaptor<IntakeHistoryEntity> historyCaptor = forClass(IntakeHistoryEntity.class);
        verify(historyMapper).insert(historyCaptor.capture());
        assertEquals("更新需求待办截图", historyCaptor.getValue().getActionSummary());
        assertEquals(
                "待办标题：确认资方接口口径\n删除截图：old.png\n新增截图：new.png",
                historyCaptor.getValue().getDetailText()
        );
    }

    @Test
    void saveScreenshots_shouldSkipWritesAndHistoryWhenNothingChanges() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeTodoMapper todoMapper = mock(IntakeTodoMapper.class);
        IntakeHistoryMapper historyMapper = mock(IntakeHistoryMapper.class);
        AttachmentService attachmentService = mock(AttachmentService.class);
        IntakeTodoService service = new IntakeTodoService(intakeMapper, todoMapper, historyMapper, attachmentService);
        IntakeRecordEntity intake = new IntakeRecordEntity();
        intake.setId(15L);
        when(intakeMapper.findById(15L)).thenReturn(intake);
        when(todoMapper.findById(501L)).thenReturn(ownedEntity(501L));

        var response = service.saveScreenshots(15L, 501L, List.of(), List.of(), "admin");

        assertEquals(501L, response.id());
        assertEquals(List.of(), response.screenshots());
        verify(attachmentService, never()).deleteTodoScreenshots(any(), any());
        verify(attachmentService, never()).saveTodoScreenshots(any(), any());
        verify(historyMapper, never()).insert(any());
    }

    @Test
    void delete_shouldRemoveTodoAndRecordHistory() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeTodoMapper todoMapper = mock(IntakeTodoMapper.class);
        IntakeHistoryMapper historyMapper = mock(IntakeHistoryMapper.class);
        AttachmentService attachmentService = mock(AttachmentService.class);
        IntakeTodoService service = new IntakeTodoService(intakeMapper, todoMapper, historyMapper, attachmentService);
        IntakeRecordEntity intake = new IntakeRecordEntity();
        intake.setId(15L);
        when(intakeMapper.findById(15L)).thenReturn(intake);
        IntakeTodoEntity existing = responseEntity(501L, "待处理", null);
        existing.setIntakeId(15L);
        when(todoMapper.findById(501L)).thenReturn(existing);

        service.delete(15L, 501L, "admin");

        verify(todoMapper).deleteById(501L);
        verify(attachmentService).deleteAllTodoScreenshots(501L);
        ArgumentCaptor<IntakeHistoryEntity> historyCaptor = forClass(IntakeHistoryEntity.class);
        verify(historyMapper).insert(historyCaptor.capture());
        assertEquals("删除需求待办", historyCaptor.getValue().getActionSummary());
    }

    private IntakeTodoEntity ownedEntity(Long id) {
        IntakeTodoEntity entity = responseEntity(id, "待处理", null);
        entity.setIntakeId(15L);
        return entity;
    }

    private AttachmentResponse screenshot(Long id, String fileName) {
        return new AttachmentResponse(
                id,
                "截图",
                fileName,
                "image/png",
                "/api/attachments/" + id + "/download",
                true,
                LocalDateTime.of(2026, 6, 4, 10, 0)
        );
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
