package cn.aslight.workhub.controller.wecom;

import cn.aslight.workhub.common.api.ApiResponse;
import cn.aslight.workhub.model.intake.IntakeDetailResponse;
import cn.aslight.workhub.model.intake.WecomCallbackRequest;
import cn.aslight.workhub.service.intake.IntakeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 企业微信回调接口控制器。
 */
@RestController
@RequestMapping("/api/wecom/callback")
public class WecomCallbackController {

    private final IntakeService intakeService;

    public WecomCallbackController(IntakeService intakeService) {
        this.intakeService = intakeService;
    }

    /**
     * 接收企业微信回调并写入待整理箱。
     *
     * @param request 回调请求
     * @return 写入后的需求详情
     */
    @PostMapping("/messages")
    public ApiResponse<IntakeDetailResponse> receiveMessage(@Valid @RequestBody WecomCallbackRequest request) {
        return ApiResponse.success(intakeService.receiveWecomCallback(request));
    }
}
