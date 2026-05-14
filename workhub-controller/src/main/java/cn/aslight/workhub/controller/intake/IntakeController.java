package cn.aslight.workhub.controller.intake;

import cn.aslight.workhub.common.api.ApiResponse;
import cn.aslight.workhub.common.api.PageResponse;
import cn.aslight.workhub.model.intake.DevelopmentAnalysisChatRequest;
import cn.aslight.workhub.model.intake.DevelopmentAnalysisConfirmResponse;
import cn.aslight.workhub.model.intake.DevelopmentAnalysisDraftUpdateRequest;
import cn.aslight.workhub.model.intake.DevelopmentAnalysisOwnerUpdateRequest;
import cn.aslight.workhub.model.intake.DevelopmentAnalysisResponse;
import cn.aslight.workhub.model.intake.IntakeDevelopmentBranchRequest;
import cn.aslight.workhub.model.intake.IntakeDetailResponse;
import cn.aslight.workhub.model.intake.IntakeStageActionRequest;
import cn.aslight.workhub.model.intake.IntakeSummaryResponse;
import cn.aslight.workhub.model.intake.IntakeUploadRequest;
import cn.aslight.workhub.model.intake.IntakeZentaoLinkRequest;
import cn.aslight.workhub.service.intake.DevelopmentAnalysisService;
import cn.aslight.workhub.service.intake.IntakeService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
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
    private final DevelopmentAnalysisService developmentAnalysisService;

    public IntakeController(IntakeService intakeService,
                            DevelopmentAnalysisService developmentAnalysisService) {
        this.intakeService = intakeService;
        this.developmentAnalysisService = developmentAnalysisService;
    }

    /**
     * 查询列表数据。
     *
     * @return 列表结果
     */
    @GetMapping
    public ApiResponse<PageResponse<IntakeSummaryResponse>> list(@RequestParam(required = false) String status,
                                                                 @RequestParam(required = false) String requirementName,
                                                                 @RequestParam(required = false) String approvalCode,
                                                                 @RequestParam(required = false) String proposerName,
                                                                 @RequestParam(required = false) String demandStatus,
                                                                 @RequestParam(required = false) String releasedStartDate,
                                                                 @RequestParam(required = false) String releasedEndDate,
                                                                 @RequestParam(defaultValue = "1") int page,
                                                                 @RequestParam(defaultValue = "10") int pageSize) {
        List<IntakeSummaryResponse> items = intakeService.list(status, requirementName, approvalCode, proposerName, demandStatus, releasedStartDate, releasedEndDate);
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
     * 补充上传需求截图和需求附件。
     *
     * @param id 待整理记录 ID
     * @param screenshots 需求截图文件
     * @param attachments 需求附件文件
     * @param authentication 当前认证信息
     * @return 更新后的需求详情
     */
    @PostMapping(value = "/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<IntakeDetailResponse> appendAttachments(@PathVariable Long id,
                                                               @RequestParam(name = "screenshots", required = false) List<MultipartFile> screenshots,
                                                               @RequestParam(name = "attachments", required = false) List<MultipartFile> attachments,
                                                               Authentication authentication) {
        return ApiResponse.success(intakeService.appendAttachments(id, screenshots, attachments, authentication.getName()));
    }

    /**
     * 删除需求截图或需求附件。
     *
     * @param id 待整理记录 ID
     * @param attachmentId 附件 ID
     * @param authentication 当前认证信息
     * @return 更新后的需求详情
     */
    @DeleteMapping("/{id}/attachments/{attachmentId}")
    public ApiResponse<IntakeDetailResponse> deleteAttachment(@PathVariable Long id,
                                                              @PathVariable Long attachmentId,
                                                              Authentication authentication) {
        return ApiResponse.success(intakeService.deleteAttachment(id, attachmentId, authentication.getName()));
    }

    /**
     * 替换需求截图或需求附件。
     *
     * @param id 待整理记录 ID
     * @param attachmentId 附件 ID
     * @param file 新文件
     * @param authentication 当前认证信息
     * @return 更新后的需求详情
     */
    @PostMapping("/{id}/attachments/{attachmentId}/replace")
    public ApiResponse<IntakeDetailResponse> replaceAttachment(@PathVariable Long id,
                                                               @PathVariable Long attachmentId,
                                                               @RequestParam(name = "file") MultipartFile file,
                                                               Authentication authentication) {
        return ApiResponse.success(intakeService.replaceAttachment(id, attachmentId, file, authentication.getName()));
    }

    /**
     * 重新触发失败需求的截图和附件结构化识别。
     *
     * @param id 待整理记录 ID
     * @param authentication 当前认证信息
     * @return 更新后的需求详情
     */
    @PostMapping("/{id}/enrichment/retry")
    public ApiResponse<IntakeDetailResponse> retryEnrichment(@PathVariable Long id,
                                                             Authentication authentication) {
        return ApiResponse.success(intakeService.retryEnrichment(id, authentication.getName()));
    }

    /**
     * 执行需求阶段动作。
     *
     * @param id 待整理记录 ID
     * @param request 阶段动作请求
     * @param authentication 当前认证信息
     * @return 更新后的需求详情
     */
    @PostMapping(value = "/{id}/stage-actions", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<IntakeDetailResponse> advanceStage(@PathVariable Long id,
                                                          @RequestBody IntakeStageActionRequest request,
                                                          Authentication authentication) {
        return ApiResponse.success(intakeService.advanceStage(id, request, authentication.getName()));
    }

    /**
     * 通过 multipart 执行需求阶段动作，并可选上传阶段产出数据文件。
     *
     * @param id 待整理记录 ID
     * @param request 阶段动作请求
     * @param dataFiles 数据文件
     * @param authentication 当前认证信息
     * @return 更新后的需求详情
     */
    @PostMapping(value = "/{id}/stage-actions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<IntakeDetailResponse> advanceStageWithFiles(@PathVariable Long id,
                                                                   @ModelAttribute IntakeStageActionRequest request,
                                                                   @RequestParam(name = "dataFiles", required = false) List<MultipartFile> dataFiles,
                                                                   Authentication authentication) {
        return ApiResponse.success(intakeService.advanceStage(id, request, dataFiles, authentication.getName()));
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
     * 更新研发分支名。
     *
     * @param id 待整理记录 ID
     * @param request 研发分支请求
     * @param authentication 当前认证信息
     * @return 更新后的需求详情
     */
    @PostMapping("/{id}/development-branch")
    public ApiResponse<IntakeDetailResponse> updateDevelopmentBranch(@PathVariable Long id,
                                                                     @RequestBody IntakeDevelopmentBranchRequest request,
                                                                     Authentication authentication) {
        return ApiResponse.success(intakeService.updateDevelopmentBranch(id, request, authentication.getName()));
    }

    /**
     * 为数据提取/运维类需求生成 SQL 草稿。
     *
     * <p>接口只生成草稿并保存，不连接数据库、不执行 SQL。</p>
     *
     * @param id 待整理记录 ID
     * @param authentication 当前认证信息
     * @return 更新后的需求详情
     */
    @PostMapping("/{id}/sql-draft")
    public ApiResponse<IntakeDetailResponse> generateSqlDraft(@PathVariable Long id,
                                                              Authentication authentication) {
        return ApiResponse.success(intakeService.generateSqlDraft(id, authentication.getName()));
    }

    @GetMapping("/{id}/development-analysis")
    public ApiResponse<DevelopmentAnalysisResponse> developmentAnalysis(@PathVariable Long id) {
        return ApiResponse.success(developmentAnalysisService.detail(id));
    }

    @PostMapping("/{id}/development-analysis")
    public ApiResponse<DevelopmentAnalysisResponse> analyzeDevelopment(@PathVariable Long id,
                                                                       @RequestParam(required = false) String projectGroup,
                                                                       Authentication authentication) {
        return ApiResponse.success(developmentAnalysisService.analyze(id, projectGroup, authentication.getName()));
    }

    @PostMapping("/{id}/development-analysis/chat")
    public ApiResponse<DevelopmentAnalysisResponse> chatDevelopmentAnalysis(@PathVariable Long id,
                                                                           @Valid @RequestBody DevelopmentAnalysisChatRequest request,
                                                                           Authentication authentication) {
        return ApiResponse.success(developmentAnalysisService.chat(id, request, authentication.getName()));
    }

    @PostMapping("/{id}/development-analysis/owners")
    public ApiResponse<DevelopmentAnalysisResponse> updateDevelopmentAnalysisOwners(@PathVariable Long id,
                                                                                   @Valid @RequestBody DevelopmentAnalysisOwnerUpdateRequest request,
                                                                                   Authentication authentication) {
        return ApiResponse.success(developmentAnalysisService.updateOwners(id, request, authentication.getName()));
    }

    @PostMapping("/{id}/development-analysis/draft")
    public ApiResponse<DevelopmentAnalysisResponse> updateDevelopmentAnalysisDraft(@PathVariable Long id,
                                                                                  @Valid @RequestBody DevelopmentAnalysisDraftUpdateRequest request,
                                                                                  Authentication authentication) {
        return ApiResponse.success(developmentAnalysisService.updateDraft(id, request, authentication.getName()));
    }

    @PostMapping("/{id}/development-analysis/confirm")
    public ApiResponse<DevelopmentAnalysisConfirmResponse> confirmDevelopmentAnalysis(@PathVariable Long id,
                                                                                     Authentication authentication) {
        return ApiResponse.success(developmentAnalysisService.confirm(id, authentication.getName()));
    }

    @PostMapping("/{id}/zentao-sync")
    public ApiResponse<DevelopmentAnalysisConfirmResponse> syncZentao(@PathVariable Long id,
                                                                      Authentication authentication) {
        return ApiResponse.success(developmentAnalysisService.syncZentao(id, authentication.getName()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, Authentication authentication) {
        intakeService.delete(id, authentication == null ? null : authentication.getName());
        return ApiResponse.success();
    }

}
