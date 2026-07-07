package cn.aslight.workhub.controller.payment;

import cn.aslight.workhub.common.api.ApiResponse;
import cn.aslight.workhub.common.api.PageResponse;
import cn.aslight.workhub.model.payment.PaymentMerchantDetailResponse;
import cn.aslight.workhub.model.payment.PaymentMerchantCredentialResponse;
import cn.aslight.workhub.model.payment.PaymentMerchantCredentialSaveRequest;
import cn.aslight.workhub.model.payment.PaymentMerchantParamFileUploadRequest;
import cn.aslight.workhub.model.payment.PaymentMerchantParamResponse;
import cn.aslight.workhub.model.payment.PaymentMerchantParamSaveRequest;
import cn.aslight.workhub.model.payment.PaymentMerchantSaveRequest;
import cn.aslight.workhub.model.payment.PaymentMerchantSummaryResponse;
import cn.aslight.workhub.model.payment.PaymentSecretDownloadResponse;
import cn.aslight.workhub.model.payment.PaymentSecretFileUploadRequest;
import cn.aslight.workhub.model.payment.PaymentSecretSaveRequest;
import cn.aslight.workhub.model.payment.PaymentSecretSummaryResponse;
import cn.aslight.workhub.service.payment.PaymentMerchantCredentialService;
import cn.aslight.workhub.service.payment.PaymentMerchantParamService;
import cn.aslight.workhub.service.payment.PaymentMerchantService;
import cn.aslight.workhub.service.payment.PaymentSecretService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
    @PreAuthorize("hasAuthority('payment:config:view') or hasAuthority('payment:config:manage')")
    public ApiResponse<PageResponse<PaymentMerchantSummaryResponse>> list(@RequestParam(required = false) String status,
                                                                          @RequestParam(required = false) Long channelId,
                                                                          @RequestParam(required = false) Long projectId,
                                                                          @RequestParam(required = false) String businessLine,
                                                                          @RequestParam(required = false) String purposeCode,
                                                                          @RequestParam(required = false) String keyword,
                                                                          @RequestParam(defaultValue = "1") int page,
                                                                          @RequestParam(defaultValue = "10") int pageSize) {
        List<PaymentMerchantSummaryResponse> items = paymentMerchantService.list(status, channelId, projectId, businessLine, purposeCode, keyword);
        return ApiResponse.success(page(items, page, pageSize));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('payment:config:view') or hasAuthority('payment:config:manage')")
    public ApiResponse<PaymentMerchantDetailResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(paymentMerchantService.detail(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('payment:merchant:create') or hasAuthority('payment:config:manage')")
    public ApiResponse<PaymentMerchantDetailResponse> create(@Valid @RequestBody PaymentMerchantSaveRequest request,
                                                             Principal principal) {
        return ApiResponse.success(paymentMerchantService.create(request, operator(principal)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('payment:merchant:update') or hasAuthority('payment:config:manage')")
    public ApiResponse<PaymentMerchantDetailResponse> update(@PathVariable Long id,
                                                             @Valid @RequestBody PaymentMerchantSaveRequest request,
                                                             Principal principal) {
        return ApiResponse.success(paymentMerchantService.update(id, request, operator(principal)));
    }

    @GetMapping("/{merchantId}/params")
    @PreAuthorize("hasAuthority('payment:config:view') or hasAuthority('payment:config:manage')")
    public ApiResponse<List<PaymentMerchantParamResponse>> listParams(@PathVariable Long merchantId) {
        return ApiResponse.success(paymentMerchantParamService.list(merchantId));
    }

    @PostMapping("/{merchantId}/params")
    @PreAuthorize("hasAuthority('payment:param:manage') or hasAuthority('payment:config:manage')")
    public ApiResponse<List<PaymentMerchantParamResponse>> saveParam(@PathVariable Long merchantId,
                                                                     @Valid @RequestBody PaymentMerchantParamSaveRequest request,
                                                                     Principal principal) {
        return ApiResponse.success(paymentMerchantParamService.save(merchantId, request, operator(principal)));
    }

    @PutMapping("/{merchantId}/params/{paramId}")
    @PreAuthorize("hasAuthority('payment:param:manage') or hasAuthority('payment:config:manage')")
    public ApiResponse<List<PaymentMerchantParamResponse>> updateParam(@PathVariable Long merchantId,
                                                                       @PathVariable Long paramId,
                                                                       @Valid @RequestBody PaymentMerchantParamSaveRequest request,
                                                                       Principal principal) {
        return ApiResponse.success(paymentMerchantParamService.update(merchantId, paramId, request, operator(principal)));
    }

    @PostMapping(value = "/{merchantId}/params/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('payment:param:manage') or hasAuthority('payment:config:manage')")
    public ApiResponse<List<PaymentMerchantParamResponse>> saveParamFromFile(@PathVariable Long merchantId,
                                                                             @Valid @ModelAttribute PaymentMerchantParamFileUploadRequest request,
                                                                             @RequestParam(name = "file") MultipartFile file,
                                                                             Principal principal) {
        return ApiResponse.success(paymentMerchantParamService.saveFromFile(merchantId, request, file, operator(principal)));
    }

    @PutMapping(value = "/{merchantId}/params/{paramId}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('payment:param:manage') or hasAuthority('payment:config:manage')")
    public ApiResponse<List<PaymentMerchantParamResponse>> updateParamFromFile(@PathVariable Long merchantId,
                                                                               @PathVariable Long paramId,
                                                                               @Valid @ModelAttribute PaymentMerchantParamFileUploadRequest request,
                                                                               @RequestParam(name = "file", required = false) MultipartFile file,
                                                                               Principal principal) {
        return ApiResponse.success(paymentMerchantParamService.updateFromFile(merchantId, paramId, request, file, operator(principal)));
    }

    @GetMapping("/{merchantId}/params/{paramId}/file")
    @PreAuthorize("hasAuthority('payment:param:manage') or hasAuthority('payment:config:manage')")
    public ResponseEntity<byte[]> downloadParamFile(@PathVariable Long merchantId,
                                                    @PathVariable Long paramId) {
        PaymentSecretDownloadResponse response = paymentMerchantParamService.downloadFile(merchantId, paramId);
        return ResponseEntity.ok()
                .contentType(resolveMediaType(response.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(response.fileName()))
                .body(response.content());
    }

    @DeleteMapping("/{merchantId}/params/{paramId}")
    @PreAuthorize("hasAuthority('payment:param:manage') or hasAuthority('payment:config:manage')")
    public ApiResponse<List<PaymentMerchantParamResponse>> deleteParam(@PathVariable Long merchantId,
                                                                       @PathVariable Long paramId,
                                                                       Principal principal) {
        return ApiResponse.success(paymentMerchantParamService.delete(merchantId, paramId, operator(principal)));
    }

    @GetMapping("/{merchantId}/credentials")
    @PreAuthorize("hasAuthority('payment:config:view') or hasAuthority('payment:config:manage')")
    public ApiResponse<List<PaymentMerchantCredentialResponse>> listCredentials(@PathVariable Long merchantId) {
        return ApiResponse.success(paymentMerchantCredentialService.list(merchantId));
    }

    @PostMapping("/{merchantId}/credentials")
    @PreAuthorize("hasAuthority('payment:credential:manage') or hasAuthority('payment:config:manage')")
    public ApiResponse<List<PaymentMerchantCredentialResponse>> saveCredential(@PathVariable Long merchantId,
                                                                               @Valid @RequestBody PaymentMerchantCredentialSaveRequest request,
                                                                               Principal principal) {
        return ApiResponse.success(paymentMerchantCredentialService.save(merchantId, request, operator(principal)));
    }

    @PutMapping("/{merchantId}/credentials/{credentialId}")
    @PreAuthorize("hasAuthority('payment:credential:manage') or hasAuthority('payment:config:manage')")
    public ApiResponse<List<PaymentMerchantCredentialResponse>> updateCredential(@PathVariable Long merchantId,
                                                                                 @PathVariable Long credentialId,
                                                                                 @Valid @RequestBody PaymentMerchantCredentialSaveRequest request,
                                                                                 Principal principal) {
        return ApiResponse.success(paymentMerchantCredentialService.update(merchantId, credentialId, request, operator(principal)));
    }

    @GetMapping("/{merchantId}/secrets")
    @PreAuthorize("hasAuthority('payment:config:view') or hasAuthority('payment:secret:manage')")
    public ApiResponse<List<PaymentSecretSummaryResponse>> listSecrets(@PathVariable Long merchantId) {
        return ApiResponse.success(paymentSecretService.list(merchantId));
    }

    @PostMapping("/{merchantId}/secrets")
    @PreAuthorize("hasAuthority('payment:secret:manage') or hasAuthority('payment:config:manage')")
    public ApiResponse<List<PaymentSecretSummaryResponse>> createSecret(@PathVariable Long merchantId,
                                                                        @Valid @RequestBody PaymentSecretSaveRequest request,
                                                                        Principal principal) {
        return ApiResponse.success(paymentSecretService.create(merchantId, request, operator(principal)));
    }

    @PutMapping("/{merchantId}/secrets/{secretId}")
    @PreAuthorize("hasAuthority('payment:secret:manage') or hasAuthority('payment:config:manage')")
    public ApiResponse<List<PaymentSecretSummaryResponse>> updateSecret(@PathVariable Long merchantId,
                                                                        @PathVariable Long secretId,
                                                                        @Valid @RequestBody PaymentSecretSaveRequest request,
                                                                        Principal principal) {
        return ApiResponse.success(paymentSecretService.update(merchantId, secretId, request, operator(principal)));
    }

    @PostMapping(value = "/{merchantId}/secrets/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('payment:secret:manage') or hasAuthority('payment:config:manage')")
    public ApiResponse<List<PaymentSecretSummaryResponse>> createSecretFromFile(@PathVariable Long merchantId,
                                                                                @Valid @ModelAttribute PaymentSecretFileUploadRequest request,
                                                                                @RequestParam(name = "file") MultipartFile file,
                                                                                Principal principal) {
        return ApiResponse.success(paymentSecretService.createFromFile(merchantId, request, file, operator(principal)));
    }

    @PutMapping(value = "/{merchantId}/secrets/{secretId}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('payment:secret:manage') or hasAuthority('payment:config:manage')")
    public ApiResponse<List<PaymentSecretSummaryResponse>> updateSecretFromFile(@PathVariable Long merchantId,
                                                                                @PathVariable Long secretId,
                                                                                @Valid @ModelAttribute PaymentSecretFileUploadRequest request,
                                                                                @RequestParam(name = "file", required = false) MultipartFile file,
                                                                                Principal principal) {
        return ApiResponse.success(paymentSecretService.updateFromFile(merchantId, secretId, request, file, operator(principal)));
    }

	    @GetMapping("/{merchantId}/secrets/{secretId}/file")
	    @PreAuthorize("hasAuthority('payment:secret:manage') or hasAuthority('payment:config:manage')")
	    public ResponseEntity<byte[]> downloadSecretFile(@PathVariable Long merchantId,
	                                                     @PathVariable Long secretId) {
	        PaymentSecretDownloadResponse response = paymentSecretService.downloadFile(merchantId, secretId);
	        return ResponseEntity.ok()
	                .contentType(resolveMediaType(response.contentType()))
	                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(response.fileName()))
	                .body(response.content());
	    }

	    private String operator(Principal principal) {
	        return principal == null ? "system" : principal.getName();
	    }

	    private MediaType resolveMediaType(String contentType) {
	        try {
	            return MediaType.parseMediaType(contentType);
	        } catch (Exception ex) {
	            return MediaType.APPLICATION_OCTET_STREAM;
	        }
	    }

	    private String contentDisposition(String fileName) {
	        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
	        return "attachment; filename*=UTF-8''" + encoded;
	    }

    private <T> PageResponse<T> page(List<T> items, int page, int pageSize) {
        int normalizedPage = Math.max(page, 1);
        int normalizedPageSize = Math.max(pageSize, 1);
        int fromIndex = Math.min((normalizedPage - 1) * normalizedPageSize, items.size());
        int toIndex = Math.min(fromIndex + normalizedPageSize, items.size());
        return new PageResponse<>(items.size(), items.subList(fromIndex, toIndex));
    }
}
