package cn.aslight.workhub.service.payment;

import cn.aslight.workhub.dao.payment.PaymentMerchantMapper;
import cn.aslight.workhub.dao.payment.PaymentSecretMapper;
import cn.aslight.workhub.model.payment.PaymentMerchantEntity;
import cn.aslight.workhub.model.payment.PaymentSecretEntity;
import cn.aslight.workhub.model.payment.PaymentSecretFileUploadRequest;
import cn.aslight.workhub.model.payment.PaymentSecretSaveRequest;
import cn.aslight.workhub.model.payment.PaymentSecretSummaryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/**
 * 支付秘钥服务。
 */
@Service
public class PaymentSecretService {

    private final PaymentMerchantMapper paymentMerchantMapper;
    private final PaymentSecretMapper paymentSecretMapper;
    private final PaymentCryptoService paymentCryptoService;
    private final PaymentAuditService paymentAuditService;

    public PaymentSecretService(PaymentMerchantMapper paymentMerchantMapper,
                                PaymentSecretMapper paymentSecretMapper,
                                PaymentCryptoService paymentCryptoService,
                                PaymentAuditService paymentAuditService) {
        this.paymentMerchantMapper = paymentMerchantMapper;
        this.paymentSecretMapper = paymentSecretMapper;
        this.paymentCryptoService = paymentCryptoService;
        this.paymentAuditService = paymentAuditService;
    }

    public List<PaymentSecretSummaryResponse> list(Long merchantId) {
        requireMerchant(merchantId);
        return paymentSecretMapper.findByMerchantId(merchantId);
    }

    @Transactional
    public List<PaymentSecretSummaryResponse> create(Long merchantId,
                                                     PaymentSecretSaveRequest request,
                                                     String operatorUserName) {
        requireMerchant(merchantId);
        String secretName = PaymentCatalogs.requireText(request.getSecretName(), "secretName");
        String rawValue = PaymentCatalogs.requireText(request.getSecretValue(), "secretValue");
        boolean activateNow = request.getActivateNow() == null || request.getActivateNow();
        int version = paymentSecretMapper.findMaxVersion(merchantId, secretName) + 1;
        if (activateNow) {
            paymentSecretMapper.deactivateActiveVersions(merchantId, secretName);
        }
        PaymentSecretEntity entity = new PaymentSecretEntity();
        entity.setMerchantId(merchantId);
        entity.setSecretName(secretName);
        entity.setSecretType(PaymentCatalogs.normalizeSecretType(request.getSecretType()));
        entity.setEncryptedValue(paymentCryptoService.encrypt(rawValue));
        entity.setMaskedValue(paymentCryptoService.mask(rawValue));
        entity.setFingerprint(paymentCryptoService.fingerprint(rawValue));
        entity.setAlgorithm(paymentCryptoService.algorithm());
        entity.setVersionNo(version);
        entity.setStatus(activateNow ? "ACTIVE" : "INACTIVE");
        entity.setValidFrom(request.getValidFrom());
        entity.setValidTo(request.getValidTo());
        entity.setRemark(PaymentCatalogs.trimToNull(request.getRemark()));
        paymentSecretMapper.insert(entity);
        paymentAuditService.record(
                "MERCHANT_SECRET",
                entity.getId(),
                "ROTATE",
                "新增秘钥版本",
                "merchantId=" + merchantId + ",secretName=" + secretName + ",version=" + version,
                operatorUserName
        );
        return list(merchantId);
    }

    @Transactional
    public List<PaymentSecretSummaryResponse> createFromFile(Long merchantId,
                                                             PaymentSecretFileUploadRequest request,
                                                             MultipartFile file,
                                                             String operatorUserName) {
        PaymentSecretSaveRequest saveRequest = new PaymentSecretSaveRequest();
        saveRequest.setSecretName(request.getSecretName());
        saveRequest.setSecretType(request.getSecretType());
        saveRequest.setSecretValue(readSecretFileValue(request.getFileValueType(), file));
        saveRequest.setActivateNow(request.getActivateNow());
        saveRequest.setValidFrom(request.getValidFrom());
        saveRequest.setValidTo(request.getValidTo());
        saveRequest.setRemark(request.getRemark());
        return create(merchantId, saveRequest, operatorUserName);
    }

    private PaymentMerchantEntity requireMerchant(Long merchantId) {
        PaymentMerchantEntity merchantEntity = paymentMerchantMapper.findEntityById(merchantId);
        if (merchantEntity == null) {
            throw new IllegalArgumentException("支付商户不存在");
        }
        return merchantEntity;
    }

    private String readSecretFileValue(String fileValueType, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("秘钥文件不能为空");
        }
        String normalizedType = PaymentCatalogs.normalizeSecretFileValueType(fileValueType);
        try {
            byte[] bytes = file.getBytes();
            if ("TEXT".equals(normalizedType)) {
                return PaymentCatalogs.requireText(new String(bytes, StandardCharsets.UTF_8), "secretValue");
            }
            return Base64.getEncoder().encodeToString(bytes);
        } catch (IOException ex) {
            throw new IllegalArgumentException("秘钥文件读取失败", ex);
        }
    }
}
