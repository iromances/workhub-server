package cn.aslight.workhub.controller.attachment;

import cn.aslight.workhub.common.api.ApiResponse;
import cn.aslight.workhub.model.intake.IntakeDetailResponse;
import cn.aslight.workhub.service.attachment.AttachmentService;
import cn.aslight.workhub.service.intake.IntakeService;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;

/**
 * 附件下载接口控制器。
 */
@RestController
@RequestMapping("/api/attachments")
public class AttachmentController {

    private final AttachmentService attachmentService;
    private final IntakeService intakeService;

    public AttachmentController(AttachmentService attachmentService, IntakeService intakeService) {
        this.attachmentService = attachmentService;
        this.intakeService = intakeService;
    }

    /**
     * 下载指定附件。
     *
     * @param id 附件 ID
     * @return 附件下载响应
     */
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

    /**
     * 删除需求附件。
     *
     * @param id 附件 ID
     * @param intakeId 需求 ID
     * @param authentication 当前认证信息
     * @return 更新后的需求详情
     */
    @DeleteMapping("/{id}")
    public ApiResponse<IntakeDetailResponse> delete(@PathVariable Long id,
                                                    @RequestParam Long intakeId,
                                                    Authentication authentication) {
        return ApiResponse.success(intakeService.deleteAttachment(intakeId, id, authentication.getName()));
    }

    /**
     * 替换需求附件。
     *
     * @param id 附件 ID
     * @param intakeId 需求 ID
     * @param file 新文件
     * @param authentication 当前认证信息
     * @return 更新后的需求详情
     */
    @PostMapping("/{id}/replace")
    public ApiResponse<IntakeDetailResponse> replace(@PathVariable Long id,
                                                     @RequestParam Long intakeId,
                                                     @RequestParam(name = "file") MultipartFile file,
                                                     Authentication authentication) {
        return ApiResponse.success(intakeService.replaceAttachment(intakeId, id, file, authentication.getName()));
    }
}
