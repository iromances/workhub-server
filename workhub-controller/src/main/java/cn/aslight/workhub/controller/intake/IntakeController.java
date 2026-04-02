package cn.aslight.workhub.controller.intake;

import cn.aslight.workhub.common.api.ApiResponse;
import cn.aslight.workhub.common.api.PageResponse;
import cn.aslight.workhub.model.intake.IntakeConvertRequest;
import cn.aslight.workhub.model.intake.IntakeConvertResponse;
import cn.aslight.workhub.model.intake.IntakeDetailResponse;
import cn.aslight.workhub.model.intake.IntakeStageActionRequest;
import cn.aslight.workhub.model.intake.IntakeSummaryResponse;
import cn.aslight.workhub.model.intake.IntakeUploadRequest;
import cn.aslight.workhub.model.intake.IntakeZentaoLinkRequest;
import cn.aslight.workhub.service.intake.IntakeService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

/**
 * 待整理箱接口控制器。
 */
@RestController
@RequestMapping({"/api/intake-records", "/api/intake"})
public class IntakeController {

    private final IntakeService intakeService;

    public IntakeController(IntakeService intakeService) {
        this.intakeService = intakeService;
    }

    /**
     * 查询列表数据。
     *
     * @return 列表结果
     */
    @GetMapping
    public ApiResponse<PageResponse<IntakeSummaryResponse>> list(@RequestParam(required = false) String status,
                                                                 @RequestParam(required = false) String sourceType,
                                                                 @RequestParam(required = false) String keyword,
                                                                 @RequestParam(required = false) String demandStatus,
                                                                 @RequestParam(required = false) String enrichmentStatus,
                                                                 @RequestParam(defaultValue = "1") int page,
                                                                 @RequestParam(defaultValue = "10") int pageSize) {
        List<IntakeSummaryResponse> items = intakeService.list(status, sourceType, keyword, demandStatus, enrichmentStatus);
        int normalizedPage = Math.max(page, 1);
        int normalizedPageSize = Math.max(pageSize, 1);
        int fromIndex = Math.min((normalizedPage - 1) * normalizedPageSize, items.size());
        int toIndex = Math.min(fromIndex + normalizedPageSize, items.size());
        return ApiResponse.success(new PageResponse<>(items.size(), items.subList(fromIndex, toIndex)));
    }

    /**
     * 查询详情数据。
     *
     * @param id 业务 ID
     * @param recordView 是否记录查看历史
     * @param authentication 当前认证信息
     * @return 详情结果
     */
    @GetMapping("/{id}")
    public ApiResponse<IntakeDetailResponse> detail(@PathVariable Long id,
                                                    @RequestParam(defaultValue = "true") boolean recordView,
                                                    Authentication authentication) {
        return ApiResponse.success(intakeService.detail(id, authentication == null ? null : authentication.getName(), recordView));
    }

    /**
     * 通过需求截图和需求附件录入待整理需求。
     *
     * @param request 上传表单
     * @param screenshots 需求截图文件
     * @param attachments 需求附件文件
     * @return 新建后的需求详情
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<IntakeDetailResponse> createUploaded(@Valid @ModelAttribute IntakeUploadRequest request,
                                                            @RequestParam(name = "screenshots", required = false) List<MultipartFile> screenshots,
                                                            @RequestParam(name = "attachments", required = false) List<MultipartFile> attachments) {
        return ApiResponse.success(intakeService.createUploaded(request, screenshots, attachments));
    }

    /**
     * 执行需求阶段动作。
     *
     * @param id 待整理记录 ID
     * @param request 阶段动作请求
     * @param authentication 当前认证信息
     * @return 更新后的需求详情
     */
    @PostMapping("/{id}/stage-actions")
    public ApiResponse<IntakeDetailResponse> advanceStage(@PathVariable Long id,
                                                          @RequestBody IntakeStageActionRequest request,
                                                          Authentication authentication) {
        return ApiResponse.success(intakeService.advanceStage(id, request, authentication.getName()));
    }

    /**
     * 关联或更新禅道地址。
     *
     * @param id 待整理记录 ID
     * @param request 禅道地址请求
     * @param authentication 当前认证信息
     * @return 更新后的需求详情
     */
    @PostMapping("/{id}/zentao-link")
    public ApiResponse<IntakeDetailResponse> updateZentaoLink(@PathVariable Long id,
                                                              @RequestBody IntakeZentaoLinkRequest request,
                                                              Authentication authentication) {
        return ApiResponse.success(intakeService.updateZentaoLink(id, request, authentication.getName()));
    }

    /**
     * 将待整理需求转成正式工作项。
     *
     * @param id 待整理记录 ID
     * @param request 转换请求
     * @param authentication 当前认证信息
     * @return 转换结果
     */
    @PostMapping("/{id}/convert")
    public ApiResponse<IntakeConvertResponse> convert(@PathVariable Long id,
                                                      @Valid @RequestBody IntakeConvertRequest request,
                                                      Authentication authentication) {
        return ApiResponse.success(intakeService.convertToWorkItem(id, request, authentication.getName()));
    }
}
