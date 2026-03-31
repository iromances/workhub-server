package cn.aslight.workhub.domain.intake.controller;

import cn.aslight.workhub.common.api.ApiResponse;
import cn.aslight.workhub.common.api.PageResponse;
import cn.aslight.workhub.domain.intake.dto.IntakeConvertRequest;
import cn.aslight.workhub.domain.intake.dto.IntakeConvertResponse;
import cn.aslight.workhub.domain.intake.dto.IntakeCreateRequest;
import cn.aslight.workhub.domain.intake.dto.IntakeDetailResponse;
import cn.aslight.workhub.domain.intake.dto.IntakeSummaryResponse;
import cn.aslight.workhub.domain.intake.dto.IntakeUploadRequest;
import cn.aslight.workhub.domain.intake.service.IntakeService;
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

@RestController
@RequestMapping({"/api/intake-records", "/api/intake"})
public class IntakeController {

    private final IntakeService intakeService;

    public IntakeController(IntakeService intakeService) {
        this.intakeService = intakeService;
    }

    @GetMapping
    public ApiResponse<PageResponse<IntakeSummaryResponse>> list(@RequestParam(required = false) String status,
                                                                 @RequestParam(required = false) String sourceType,
                                                                 @RequestParam(required = false) String keyword) {
        List<IntakeSummaryResponse> items = intakeService.list(status, sourceType, keyword);
        return ApiResponse.success(new PageResponse<>(items.size(), items));
    }

    @GetMapping("/{id}")
    public ApiResponse<IntakeDetailResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(intakeService.detail(id));
    }

    @PostMapping("/manual")
    public ApiResponse<IntakeDetailResponse> createManual(@Valid @RequestBody IntakeCreateRequest request) {
        return ApiResponse.success(intakeService.createManual(request));
    }

    @PostMapping("/paste")
    public ApiResponse<IntakeDetailResponse> createPasted(@Valid @RequestBody IntakeCreateRequest request) {
        return ApiResponse.success(intakeService.createPasted(request));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<IntakeDetailResponse> createUploaded(@Valid @ModelAttribute IntakeUploadRequest request,
                                                            @RequestParam(name = "screenshots", required = false) List<MultipartFile> screenshots,
                                                            @RequestParam(name = "attachments", required = false) List<MultipartFile> attachments) {
        return ApiResponse.success(intakeService.createUploaded(request, screenshots, attachments));
    }

    @PostMapping("/{id}/ai-draft")
    public ApiResponse<IntakeDetailResponse> generateAiDraft(@PathVariable Long id,
                                                             @RequestParam(required = false) String provider) {
        return ApiResponse.success(intakeService.generateAiDraft(id, provider));
    }

    @PostMapping("/{id}/convert")
    public ApiResponse<IntakeConvertResponse> convert(@PathVariable Long id,
                                                      @Valid @RequestBody IntakeConvertRequest request,
                                                      Authentication authentication) {
        return ApiResponse.success(intakeService.convertToWorkItem(id, request, authentication.getName()));
    }
}
