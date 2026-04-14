package cn.aslight.workhub.controller.payment;

import cn.aslight.workhub.common.api.ApiResponse;
import cn.aslight.workhub.common.api.PageResponse;
import cn.aslight.workhub.model.payment.PaymentProjectBindingResponse;
import cn.aslight.workhub.model.payment.PaymentProjectBindingSaveRequest;
import cn.aslight.workhub.model.payment.PaymentPurposeOptionResponse;
import cn.aslight.workhub.service.payment.PaymentCatalogService;
import cn.aslight.workhub.service.payment.PaymentProjectBindingService;
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
 * 项目支付绑定接口控制器。
 */
@RestController
@RequestMapping("/api/payment")
public class PaymentBindingController {

    private final PaymentProjectBindingService paymentProjectBindingService;
    private final PaymentCatalogService paymentCatalogService;

    public PaymentBindingController(PaymentProjectBindingService paymentProjectBindingService,
                                    PaymentCatalogService paymentCatalogService) {
        this.paymentProjectBindingService = paymentProjectBindingService;
        this.paymentCatalogService = paymentCatalogService;
    }

    @GetMapping("/bindings")
    public ApiResponse<PageResponse<PaymentProjectBindingResponse>> list(@RequestParam(required = false) Long projectId,
                                                                         @RequestParam(required = false) Long merchantId,
                                                                         @RequestParam(required = false) String purposeCode,
                                                                         @RequestParam(required = false) String status) {
        List<PaymentProjectBindingResponse> items = paymentProjectBindingService.list(projectId, merchantId, purposeCode, status);
        return ApiResponse.success(new PageResponse<>(items.size(), items));
    }

    @PostMapping("/bindings")
    public ApiResponse<PaymentProjectBindingResponse> create(@Valid @RequestBody PaymentProjectBindingSaveRequest request,
                                                             Principal principal) {
        return ApiResponse.success(paymentProjectBindingService.create(request, operator(principal)));
    }

    @PutMapping("/bindings/{id}")
    public ApiResponse<PaymentProjectBindingResponse> update(@PathVariable Long id,
                                                             @Valid @RequestBody PaymentProjectBindingSaveRequest request,
                                                             Principal principal) {
        return ApiResponse.success(paymentProjectBindingService.update(id, request, operator(principal)));
    }

    @GetMapping("/projects/{projectId}/bindings/resolve")
    public ApiResponse<PaymentProjectBindingResponse> resolve(@PathVariable Long projectId,
                                                              @RequestParam String purposeCode) {
        return ApiResponse.success(paymentProjectBindingService.resolve(projectId, purposeCode));
    }

    @GetMapping("/purposes")
    public ApiResponse<List<PaymentPurposeOptionResponse>> listPurposes() {
        return ApiResponse.success(paymentCatalogService.listPurposes());
    }

    private String operator(Principal principal) {
        return principal == null ? "system" : principal.getName();
    }
}
