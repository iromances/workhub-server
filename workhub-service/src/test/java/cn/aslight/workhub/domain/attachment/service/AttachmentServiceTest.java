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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
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
