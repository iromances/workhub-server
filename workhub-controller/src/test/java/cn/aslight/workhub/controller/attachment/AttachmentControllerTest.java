package cn.aslight.workhub.controller.attachment;

import cn.aslight.workhub.model.intake.IntakeDetailResponse;
import cn.aslight.workhub.service.attachment.AttachmentService;
import cn.aslight.workhub.service.intake.IntakeService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AttachmentControllerTest {

    @Test
    void delete_shouldUseAttachmentResourcePath() throws Exception {
        IntakeService intakeService = mock(IntakeService.class);
        when(intakeService.deleteAttachment(46L, 63L, "admin")).thenReturn(simpleDetail(46L));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AttachmentController(mock(AttachmentService.class), intakeService)).build();

        mockMvc.perform(delete("/api/attachments/63")
                        .param("intakeId", "46")
                        .principal(new UsernamePasswordAuthenticationToken("admin", "N/A")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(46));

        verify(intakeService).deleteAttachment(46L, 63L, "admin");
    }

    @Test
    void replace_shouldUseAttachmentResourcePath() throws Exception {
        IntakeService intakeService = mock(IntakeService.class);
        when(intakeService.replaceAttachment(eq(46L), eq(63L), any(), eq("admin"))).thenReturn(simpleDetail(46L));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AttachmentController(mock(AttachmentService.class), intakeService)).build();

        MockMultipartFile file = new MockMultipartFile("file", "new.docx", "application/octet-stream", "new".getBytes());
        mockMvc.perform(multipart("/api/attachments/63/replace")
                        .file(file)
                        .param("intakeId", "46")
                        .principal(new UsernamePasswordAuthenticationToken("admin", "N/A")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(46));

        verify(intakeService).replaceAttachment(eq(46L), eq(63L), any(), eq("admin"));
    }

    private IntakeDetailResponse simpleDetail(Long id) {
        return new IntakeDetailResponse(
                id,
                "需求截图附件录入",
                "需求录入",
                null,
                "admin",
                "石浩",
                LocalDateTime.of(2026, 4, 2, 12, 0),
                "已收录",
                "原始内容",
                null,
                List.of(),
                List.of(),
                "待整理",
                "SUCCEEDED",
                null,
                LocalDateTime.of(2026, 4, 2, 12, 1),
                null,
                List.of(),
                null,
                LocalDateTime.of(2026, 4, 2, 12, 0),
                LocalDateTime.of(2026, 4, 2, 12, 1)
        );
    }
}
