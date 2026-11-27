package cn.aslight.workhub.service.attachment;

import cn.aslight.workhub.dao.attachment.AttachmentMapper;
import cn.aslight.workhub.model.attachment.AttachmentEntity;
import cn.aslight.workhub.model.attachment.AttachmentResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class AttachmentServiceTest {

    @Test
    void listIntakeAttachmentsShouldMarkImagesAndPdfAsPreviewable() {
        AttachmentMapper attachmentMapper = mock(AttachmentMapper.class);
        AttachmentService service = new AttachmentService(attachmentMapper);
        when(attachmentMapper.findByBiz(eq(7L), anyList())).thenReturn(List.of(
                attachment(1L, "approval.png", "image/png"),
                attachment(2L, "requirement.pdf", "application/octet-stream"),
                attachment(3L, "requirement.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
        ));

        List<AttachmentResponse> responses = service.listIntakeAttachments(7L);

        assertTrue(responses.get(0).previewable());
        assertTrue(responses.get(1).previewable());
        assertFalse(responses.get(2).previewable());
    }

    @Test
    void saveIntakeFilesShouldPersistContentSizeAndSha256InDatabase() {
        AttachmentMapper attachmentMapper = mock(AttachmentMapper.class);
        AttachmentService service = new AttachmentService(attachmentMapper);
        byte[] content = "database attachment".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "attachments",
                "requirement.txt",
                "text/plain",
                content
        );

        service.saveIntakeFiles(7L, List.of(), List.of(file));

        ArgumentCaptor<AttachmentEntity> captor = ArgumentCaptor.forClass(AttachmentEntity.class);
        verify(attachmentMapper).insert(captor.capture());
        AttachmentEntity entity = captor.getValue();
        assertEquals(AttachmentService.INTAKE_ATTACHMENT, entity.getBizType());
        assertEquals(7L, entity.getBizId());
        assertNull(entity.getStoragePath());
        assertArrayEquals(content, entity.getFileContent());
        assertEquals(content.length, entity.getFileSize());
        assertEquals(64, entity.getFileSha256().length());
    }

    @Test
    void loadAsResourceShouldReadDatabaseContent() throws Exception {
        AttachmentMapper attachmentMapper = mock(AttachmentMapper.class);
        AttachmentService service = new AttachmentService(attachmentMapper);
        byte[] content = "stored in database".getBytes();
        AttachmentEntity entity = attachment(9L, "requirement.txt", "text/plain");
        entity.setFileContent(content);
        entity.setFileSize((long) content.length);
        when(attachmentMapper.findByIdWithContent(9L)).thenReturn(entity);

        AttachmentService.AttachmentResource resource = service.loadAsResource(9L);

        assertArrayEquals(content, resource.resource().getContentAsByteArray());
    }

    @Test
    void loadAsResourceShouldFallbackToLegacyLocalFile(@TempDir Path tempDir) throws Exception {
        AttachmentMapper attachmentMapper = mock(AttachmentMapper.class);
        AttachmentService service = new AttachmentService(attachmentMapper);
        byte[] content = "legacy attachment".getBytes();
        Path file = tempDir.resolve("legacy.txt");
        Files.write(file, content);
        AttachmentEntity entity = attachment(10L, "legacy.txt", "text/plain");
        entity.setStoragePath(file.toString());
        when(attachmentMapper.findByIdWithContent(10L)).thenReturn(entity);

        AttachmentService.AttachmentResource resource = service.loadAsResource(10L);

        assertArrayEquals(content, resource.resource().getContentAsByteArray());
    }

    @Test
    void loadAsResourceShouldRejectContentWithWrongSize() {
        AttachmentMapper attachmentMapper = mock(AttachmentMapper.class);
        AttachmentService service = new AttachmentService(attachmentMapper);
        AttachmentEntity entity = attachment(11L, "broken.txt", "text/plain");
        entity.setFileContent("broken".getBytes());
        entity.setFileSize(999L);
        when(attachmentMapper.findByIdWithContent(11L)).thenReturn(entity);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.loadAsResource(11L)
        );
        assertEquals("附件内容完整性校验失败", exception.getMessage());
    }

    @Test
    void saveUserAvatarShouldStoreContentAsUserAvatarRow() {
        AttachmentMapper attachmentMapper = mock(AttachmentMapper.class);
        AttachmentService service = new AttachmentService(attachmentMapper);
        byte[] content = "avatar image".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", content);
        doAnswer(call -> {
            ((AttachmentEntity) call.getArgument(0)).setId(77L);
            return 1;
        }).when(attachmentMapper).insert(any(AttachmentEntity.class));

        Long avatarId = service.saveUserAvatar(5L, file);

        assertEquals(77L, avatarId);
        ArgumentCaptor<AttachmentEntity> captor = ArgumentCaptor.forClass(AttachmentEntity.class);
        verify(attachmentMapper).insert(captor.capture());
        AttachmentEntity entity = captor.getValue();
        assertEquals(AttachmentService.USER_AVATAR, entity.getBizType());
        assertEquals(5L, entity.getBizId());
        assertEquals("avatar.png", entity.getFileName());
        assertNull(entity.getStoragePath());
        assertEquals("image/png", entity.getContentType());
        assertArrayEquals(content, entity.getFileContent());
        assertEquals(content.length, entity.getFileSize());
        assertEquals(64, entity.getFileSha256().length());
    }

    @Test
    void loadUserAvatarShouldReadStoredAvatar() throws Exception {
        AttachmentMapper attachmentMapper = mock(AttachmentMapper.class);
        AttachmentService service = new AttachmentService(attachmentMapper);
        byte[] content = "stored avatar".getBytes();
        AttachmentEntity avatar = avatar(77L);
        avatar.setFileContent(content);
        avatar.setFileSize((long) content.length);
        when(attachmentMapper.findById(77L)).thenReturn(avatar);
        when(attachmentMapper.findByIdWithContent(77L)).thenReturn(avatar);

        AttachmentService.AttachmentResource resource = service.loadUserAvatar(77L);

        assertArrayEquals(content, resource.resource().getContentAsByteArray());
        assertEquals("image/png", resource.entity().getContentType());
    }

    @Test
    void loadUserAvatarShouldRejectMissingOrNonAvatarAttachment() {
        AttachmentMapper attachmentMapper = mock(AttachmentMapper.class);
        AttachmentService service = new AttachmentService(attachmentMapper);
        when(attachmentMapper.findById(77L)).thenReturn(null);
        when(attachmentMapper.findById(78L)).thenReturn(attachment(78L, "requirement.txt", "text/plain"));

        assertThrows(IllegalArgumentException.class, () -> service.loadUserAvatar(77L));
        assertThrows(IllegalArgumentException.class, () -> service.loadUserAvatar(78L));
        verify(attachmentMapper, never()).findByIdWithContent(any());
    }

    @Test
    void deleteUserAvatarShouldOnlyDeleteAvatarRows() {
        AttachmentMapper attachmentMapper = mock(AttachmentMapper.class);
        AttachmentService service = new AttachmentService(attachmentMapper);
        when(attachmentMapper.findById(77L)).thenReturn(avatar(77L));
        when(attachmentMapper.findById(78L)).thenReturn(attachment(78L, "requirement.txt", "text/plain"));
        when(attachmentMapper.findById(79L)).thenReturn(null);

        service.deleteUserAvatar(77L);
        service.deleteUserAvatar(78L);
        service.deleteUserAvatar(79L);

        verify(attachmentMapper).deleteById(77L);
        verify(attachmentMapper, never()).deleteById(78L);
        verify(attachmentMapper, never()).deleteById(79L);
    }

    @Test
    void saveTodoScreenshotsShouldStoreContentAsTodoScreenshotRow() {
        AttachmentMapper attachmentMapper = mock(AttachmentMapper.class);
        AttachmentService service = new AttachmentService(attachmentMapper);
        byte[] content = "todo screenshot".getBytes();
        MockMultipartFile file = new MockMultipartFile("files", "error.png", "image/png", content);

        service.saveTodoScreenshots(501L, List.of(file));

        ArgumentCaptor<AttachmentEntity> captor = ArgumentCaptor.forClass(AttachmentEntity.class);
        verify(attachmentMapper).insert(captor.capture());
        AttachmentEntity entity = captor.getValue();
        assertEquals(AttachmentService.INTAKE_TODO_SCREENSHOT, entity.getBizType());
        assertEquals(501L, entity.getBizId());
        assertEquals("error.png", entity.getFileName());
        assertNull(entity.getStoragePath());
        assertArrayEquals(content, entity.getFileContent());
        assertEquals((long) content.length, entity.getFileSize());
    }

    @Test
    void mapTodoScreenshotsShouldGroupByTodoInUploadOrder() {
        AttachmentMapper attachmentMapper = mock(AttachmentMapper.class);
        AttachmentService service = new AttachmentService(attachmentMapper);
        when(attachmentMapper.findByBizIds(AttachmentService.INTAKE_TODO_SCREENSHOT, List.of(501L, 502L))).thenReturn(List.of(
                todoScreenshot(7001L, 501L, "first.png"),
                todoScreenshot(7002L, 502L, "second.png"),
                todoScreenshot(7003L, 501L, "third.png")
        ));

        Map<Long, List<AttachmentResponse>> screenshots = service.mapTodoScreenshots(List.of(501L, 502L));

        assertEquals(List.of(7001L, 7003L), screenshots.get(501L).stream().map(AttachmentResponse::id).toList());
        assertEquals(List.of(7002L), screenshots.get(502L).stream().map(AttachmentResponse::id).toList());
        AttachmentResponse first = screenshots.get(501L).get(0);
        assertEquals("截图", first.category());
        assertEquals("/api/attachments/7001/download", first.downloadUrl());
        assertTrue(first.previewable());
    }

    @Test
    void mapTodoScreenshotsShouldSkipQueryWhenNoTodo() {
        AttachmentMapper attachmentMapper = mock(AttachmentMapper.class);
        AttachmentService service = new AttachmentService(attachmentMapper);

        Map<Long, List<AttachmentResponse>> screenshots = service.mapTodoScreenshots(List.of());

        assertTrue(screenshots.isEmpty());
        verify(attachmentMapper, never()).findByBizIds(any(), any());
    }

    @Test
    void deleteTodoScreenshotsShouldStayWithinTodo() {
        AttachmentMapper attachmentMapper = mock(AttachmentMapper.class);
        AttachmentService service = new AttachmentService(attachmentMapper);

        service.deleteTodoScreenshots(501L, List.of(7001L));
        service.deleteAllTodoScreenshots(501L);

        verify(attachmentMapper).deleteByBizAndIds(AttachmentService.INTAKE_TODO_SCREENSHOT, 501L, List.of(7001L));
        verify(attachmentMapper).deleteByBiz(AttachmentService.INTAKE_TODO_SCREENSHOT, 501L);
        verify(attachmentMapper, never()).deleteById(any());
    }

    private AttachmentEntity todoScreenshot(Long id, Long todoId, String fileName) {
        AttachmentEntity entity = attachment(id, fileName, "image/png");
        entity.setBizType(AttachmentService.INTAKE_TODO_SCREENSHOT);
        entity.setBizId(todoId);
        return entity;
    }

    private AttachmentEntity avatar(Long id) {
        AttachmentEntity entity = attachment(id, "avatar.png", "image/png");
        entity.setBizType(AttachmentService.USER_AVATAR);
        return entity;
    }

    private AttachmentEntity attachment(Long id, String fileName, String contentType) {
        AttachmentEntity entity = new AttachmentEntity();
        entity.setId(id);
        entity.setBizType(AttachmentService.INTAKE_ATTACHMENT);
        entity.setBizId(7L);
        entity.setFileName(fileName);
        entity.setStoragePath("/tmp/" + fileName);
        entity.setContentType(contentType);
        return entity;
    }
}
