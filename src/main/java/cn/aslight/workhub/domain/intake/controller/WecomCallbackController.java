package cn.aslight.workhub.domain.intake.controller;

import cn.aslight.workhub.common.api.ApiResponse;
import cn.aslight.workhub.domain.intake.dto.IntakeDetailResponse;
import cn.aslight.workhub.domain.intake.dto.WecomCallbackRequest;
import cn.aslight.workhub.domain.intake.service.IntakeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wecom/callback")
public class WecomCallbackController {

    private final IntakeService intakeService;

    public WecomCallbackController(IntakeService intakeService) {
        this.intakeService = intakeService;
    }

    @PostMapping("/messages")
    public ApiResponse<IntakeDetailResponse> receiveMessage(@Valid @RequestBody WecomCallbackRequest request) {
        return ApiResponse.success(intakeService.receiveWecomCallback(request));
    }
}
