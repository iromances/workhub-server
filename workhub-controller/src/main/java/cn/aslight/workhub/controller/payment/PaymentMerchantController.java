package cn.aslight.workhub.controller.payment;

import cn.aslight.workhub.common.api.ApiResponse;
import cn.aslight.workhub.common.api.PageResponse;
import cn.aslight.workhub.model.payment.PaymentMerchantDetailResponse;
import cn.aslight.workhub.model.payment.PaymentMerchantCredentialResponse;
import cn.aslight.workhub.model.payment.PaymentMerchantCredentialSaveRequest;
import cn.aslight.workhub.model.payment.PaymentMerchantParamResponse;
import cn.aslight.workhub.model.payment.PaymentMerchantParamSaveRequest;
import cn.aslight.workhub.model.payment.PaymentMerchantSaveRequest;
import cn.aslight.workhub.model.payment.PaymentMerchantSummaryResponse;
import cn.aslight.workhub.model.payment.PaymentSecretSaveRequest;
import cn.aslight.workhub.model.payment.PaymentSecretSummaryResponse;
import cn.aslight.workhub.service.payment.PaymentMerchantCredentialService;
import cn.aslight.workhub.service.payment.PaymentMerchantParamService;
import cn.aslight.workhub.service.payment.PaymentMerchantService;
import cn.aslight.workhub.service.payment.PaymentSecretService;
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
 * 支付商户接口控制器。
 */
@RestController
@RequestMapping("/api/payment/merchants")
public class PaymentMerchantController {

    private final PaymentMerchantService paymentMerchantService;
    private final PaymentMerchantParamService paymentMerchantParamService;
    private final PaymentMerchantCredentialService paymentMerchantCredentialService;
    private final PaymentSecretService paymentSecretService;

    public PaymentMerchantController(PaymentMerchantService paymentMerchantService,
                                     PaymentMerchantParamService paymentMerchantParamService,
                                     PaymentMerchantCredentialService paymentMerchantCredentialService,
                                     PaymentSecretService paymentSecretService) {
        this.paymentMerchantService = paymentMerchantService;
        this.paymentMerchantParamService = paymentMerchantParamService;
        this.paymentMerchantCredentialService = paymentMerchantCredentialService;
        this.paymentSecretService = paymentSecretService;
    }

    @GetMapping
    public ApiResponse<PageResponse<PaymentMerchantSummaryResponse>> list(@RequestParam(required = false) String status,
                                                                          @RequestParam(required = false) Long channelId,
                                                                          @RequestParam(required = false) Long projectId,
                                                                          @RequestParam(required = false) String purposeCode,
                                                                          @RequestParam(required = false) String keyword,
                                                                          @RequestParam(defaultValue = "1") int page,
                                                                          @RequestParam(defaultValue = "10") int pageSize) {
        List<PaymentMerchantSummaryResponse> items = paymentMerchantService.list(status, channelId, projectId, purposeCode, keyword);
        return ApiResponse.success(page(items, page, pageSize));
    }

    @GetMapping("/{id}")
    public ApiResponse<PaymentMerchantDetailResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(paymentMerchantService.detail(id));
    }

    @PostMapping
    public ApiResponse<PaymentMerchantDetailResponse> create(@Valid @RequestBody PaymentMerchantSaveRequest request,
                                                             Principal principal) {
        return ApiResponse.success(paymentMerchantService.create(request, operator(principal)));
    }

    @PutMapping("/{id}")
    public ApiResponse<PaymentMerchantDetailResponse> update(@PathVariable Long id,
                                                             @Valid @RequestBody PaymentMerchantSaveRequest request,
                                                             Principal principal) {
        return ApiResponse.success(paymentMerchantService.update(id, request, operator(principal)));
    }

    @GetMapping("/{merchantId}/params")
    public ApiResponse<List<PaymentMerchantParamResponse>> listParams(@PathVariable Long merchantId) {
        return ApiResponse.success(paymentMerchantParamService.list(merchantId));
    }

    @PostMapping("/{merchantId}/params")
    public ApiResponse<List<PaymentMerchantParamResponse>> saveParam(@PathVariable Long merchantId,
                                                                     @Valid @RequestBody PaymentMerchantParamSaveRequest request,
                                                                     Principal principal) {
        return ApiResponse.success(paymentMerchantParamService.save(merchantId, request, operator(principal)));
    }

    @PutMapping("/{merchantId}/params/{paramId}")
    public ApiResponse<List<PaymentMerchantParamResponse>> updateParam(@PathVariable Long merchantId,
                                                                       @PathVariable Long paramId,
                                                                       @Valid @RequestBody PaymentMerchantParamSaveRequest request,
                                                                       Principal principal) {
        return ApiResponse.success(paymentMerchantParamService.update(merchantId, paramId, request, operator(principal)));
    }

    @GetMapping("/{merchantId}/credentials")
    public ApiResponse<List<PaymentMerchantCredentialResponse>> listCredentials(@PathVariable Long merchantId) {
        return ApiResponse.success(paymentMerchantCredentialService.list(merchantId));
    }

    @PostMapping("/{merchantId}/credentials")
    public ApiResponse<List<PaymentMerchantCredentialResponse>> saveCredential(@PathVariable Long merchantId,
                                                                               @Valid @RequestBody PaymentMerchantCredentialSaveRequest request,
                                                                               Principal principal) {
        return ApiResponse.success(paymentMerchantCredentialService.save(merchantId, request, operator(principal)));
    }

    @PutMapping("/{merchantId}/credentials/{credentialId}")
    public ApiResponse<List<PaymentMerchantCredentialResponse>> updateCredential(@PathVariable Long merchantId,
                                                                                 @PathVariable Long credentialId,
                                                                                 @Valid @RequestBody PaymentMerchantCredentialSaveRequest request,
                                                                                 Principal principal) {
        return ApiResponse.success(paymentMerchantCredentialService.update(merchantId, credentialId, request, operator(principal)));
    }

    @GetMapping("/{merchantId}/secrets")
    public ApiResponse<List<PaymentSecretSummaryResponse>> listSecrets(@PathVariable Long merchantId) {
        return ApiResponse.success(paymentSecretService.list(merchantId));
    }

    @PostMapping("/{merchantId}/secrets")
    public ApiResponse<List<PaymentSecretSummaryResponse>> createSecret(@PathVariable Long merchantId,
                                                                        @Valid @RequestBody PaymentSecretSaveRequest request,
                                                                        Principal principal) {
        return ApiResponse.success(paymentSecretService.create(merchantId, request, operator(principal)));
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
