package cn.aslight.workhub.controller.payment;

import cn.aslight.workhub.common.api.ApiResponse;
import cn.aslight.workhub.common.api.PageResponse;
import cn.aslight.workhub.model.payment.PaymentChannelDetailResponse;
import cn.aslight.workhub.model.payment.PaymentChannelSaveRequest;
import cn.aslight.workhub.model.payment.PaymentChannelSummaryResponse;
import cn.aslight.workhub.service.payment.PaymentChannelService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

/**
 * 支付渠道接口控制器。
 */
@RestController
@RequestMapping("/api/payment/channels")
public class PaymentChannelController {

    private final PaymentChannelService paymentChannelService;

    public PaymentChannelController(PaymentChannelService paymentChannelService) {
        this.paymentChannelService = paymentChannelService;
    }

    @GetMapping
    public ApiResponse<PageResponse<PaymentChannelSummaryResponse>> list(@RequestParam(required = false) Long channelId,
                                                                         @RequestParam(required = false) String status,
                                                                         @RequestParam(defaultValue = "1") int page,
                                                                         @RequestParam(defaultValue = "10") int pageSize) {
        List<PaymentChannelSummaryResponse> items = paymentChannelService.list(channelId, status);
        return ApiResponse.success(page(items, page, pageSize));
    }

    @GetMapping("/{id}")
    public ApiResponse<PaymentChannelDetailResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(paymentChannelService.detail(id));
    }

    @PostMapping
    public ApiResponse<PaymentChannelDetailResponse> create(@Valid @RequestBody PaymentChannelSaveRequest request,
                                                            Principal principal) {
        return ApiResponse.success(paymentChannelService.create(request, operator(principal)));
    }

    @PutMapping("/{id}")
    public ApiResponse<PaymentChannelDetailResponse> update(@PathVariable Long id,
                                                            @Valid @RequestBody PaymentChannelSaveRequest request,
                                                            Principal principal) {
        return ApiResponse.success(paymentChannelService.update(id, request, operator(principal)));
    }

    private String operator(Principal principal) {
        return principal == null ? "system" : principal.getName();
    }

    private <T> PageResponse<T> page(List<T> items, int page, int pageSize) {
        int normalizedPage = Math.max(page, 1);
        int normalizedPageSize = Math.max(pageSize, 1);
        int fromIndex = Math.min((normalizedPage - 1) * normalizedPageSize, items.size());
        int toIndex = Math.min(fromIndex + normalizedPageSize, items.size());
        return new PageResponse<>(items.size(), items.subList(fromIndex, toIndex));
    }
}
