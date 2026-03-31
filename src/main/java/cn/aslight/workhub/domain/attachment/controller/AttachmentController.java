package cn.aslight.workhub.domain.attachment.controller;

import cn.aslight.workhub.domain.attachment.service.AttachmentService;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/attachments")
public class AttachmentController {

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        AttachmentService.AttachmentResource attachment = attachmentService.loadAsResource(id);
        String contentType = attachment.entity().getContentType() == null
                ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                : attachment.entity().getContentType();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(attachment.entity().getFileName(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(attachment.resource());
    }
}
