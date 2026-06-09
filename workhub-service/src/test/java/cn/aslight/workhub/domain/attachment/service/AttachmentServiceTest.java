package cn.aslight.workhub.service.attachment;

import cn.aslight.workhub.config.StorageProperties;
import cn.aslight.workhub.dao.attachment.AttachmentMapper;
import cn.aslight.workhub.model.attachment.AttachmentEntity;
import cn.aslight.workhub.model.attachment.AttachmentResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AttachmentServiceTest {

    @Test
    void listIntakeAttachmentsShouldMarkImagesAndPdfAsPreviewable() {
        AttachmentMapper attachmentMapper = mock(AttachmentMapper.class);
        AttachmentService service = new AttachmentService(attachmentMapper, new StorageProperties());
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
