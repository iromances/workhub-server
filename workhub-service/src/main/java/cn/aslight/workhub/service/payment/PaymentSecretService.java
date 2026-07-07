package cn.aslight.workhub.service.payment;

import cn.aslight.workhub.dao.payment.PaymentMerchantMapper;
import cn.aslight.workhub.dao.payment.PaymentSecretMapper;
import cn.aslight.workhub.model.payment.PaymentMerchantEntity;
import cn.aslight.workhub.model.payment.PaymentSecretDownloadResponse;
import cn.aslight.workhub.model.payment.PaymentSecretEntity;
import cn.aslight.workhub.model.payment.PaymentSecretFileUploadRequest;
import cn.aslight.workhub.model.payment.PaymentSecretSaveRequest;
import cn.aslight.workhub.model.payment.PaymentSecretSummaryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
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
        ensureSecretNameAvailable(merchantId, secretName, null);
        PaymentSecretEntity entity = baseEntity(merchantId, secretName, request.getSecretType(), request.getStatus(),
                request.getValidFrom(), request.getValidTo(), request.getRemark());
        applyTextSecretValue(entity, PaymentCatalogs.requireText(request.getSecretValue(), "secretValue"));
        paymentSecretMapper.insert(entity);
        paymentAuditService.record("MERCHANT_SECRET", entity.getId(), "CREATE", "新增商户秘钥",
                "merchantId=" + merchantId + ",secretName=" + secretName, operatorUserName);
        return list(merchantId);
    }

    @Transactional
    public List<PaymentSecretSummaryResponse> createFromFile(Long merchantId,
                                                             PaymentSecretFileUploadRequest request,
                                                             MultipartFile file,
                                                             String operatorUserName) {
        requireMerchant(merchantId);
        String secretName = PaymentCatalogs.requireText(request.getSecretName(), "secretName");
        ensureSecretNameAvailable(merchantId, secretName, null);
        PaymentSecretEntity entity = baseEntity(merchantId, secretName, request.getSecretType(), request.getStatus(),
                request.getValidFrom(), request.getValidTo(), request.getRemark());
        applyFileSecretValue(entity, PaymentCatalogs.normalizeSecretFileValueType(request.getFileValueType()), file);
        paymentSecretMapper.insert(entity);
        paymentAuditService.record("MERCHANT_SECRET", entity.getId(), "CREATE", "新增商户秘钥文件",
                "merchantId=" + merchantId + ",secretName=" + secretName, operatorUserName);
        return list(merchantId);
    }

    @Transactional
    public List<PaymentSecretSummaryResponse> update(Long merchantId,
                                                     Long secretId,
                                                     PaymentSecretSaveRequest request,
                                                     String operatorUserName) {
        PaymentSecretEntity existing = requireSecret(merchantId, secretId);
        String secretName = PaymentCatalogs.requireText(request.getSecretName(), "secretName");
        ensureSecretNameAvailable(merchantId, secretName, secretId);
        PaymentSecretEntity entity = baseEntity(merchantId, secretName, request.getSecretType(), request.getStatus(),
                request.getValidFrom(), request.getValidTo(), request.getRemark());
        entity.setId(secretId);
        entity.setVersionNo(existing.getVersionNo());
        String rawValue = PaymentCatalogs.trimToNull(request.getSecretValue());
        if (rawValue == null) {
            keepExistingSecretValue(entity, existing);
        } else {
            applyTextSecretValue(entity, rawValue);
        }
        paymentSecretMapper.update(entity);
        paymentAuditService.record("MERCHANT_SECRET", secretId, "UPDATE", "更新商户秘钥",
                "merchantId=" + merchantId + ",secretName=" + secretName, operatorUserName);
        return list(merchantId);
    }

    @Transactional
    public List<PaymentSecretSummaryResponse> updateFromFile(Long merchantId,
                                                             Long secretId,
                                                             PaymentSecretFileUploadRequest request,
                                                             MultipartFile file,
                                                             String operatorUserName) {
        PaymentSecretEntity existing = requireSecret(merchantId, secretId);
        String secretName = PaymentCatalogs.requireText(request.getSecretName(), "secretName");
        ensureSecretNameAvailable(merchantId, secretName, secretId);
        PaymentSecretEntity entity = baseEntity(merchantId, secretName, request.getSecretType(), request.getStatus(),
                request.getValidFrom(), request.getValidTo(), request.getRemark());
        entity.setId(secretId);
        entity.setVersionNo(existing.getVersionNo());
        if (file == null || file.isEmpty()) {
            keepExistingSecretValue(entity, existing);
        } else {
            applyFileSecretValue(entity, PaymentCatalogs.normalizeSecretFileValueType(request.getFileValueType()), file);
        }
        paymentSecretMapper.update(entity);
        paymentAuditService.record("MERCHANT_SECRET", secretId, "UPDATE", "更新商户秘钥文件",
                "merchantId=" + merchantId + ",secretName=" + secretName, operatorUserName);
        return list(merchantId);
    }

    public PaymentSecretDownloadResponse downloadFile(Long merchantId, Long secretId) {
        requireMerchant(merchantId);
        PaymentSecretEntity entity = requireSecret(merchantId, secretId);
        if (!"FILE".equals(entity.getSourceType())) {
            throw new IllegalArgumentException("该秘钥不是文件上传");
        }
        String fileName = PaymentCatalogs.trimToNull(entity.getFileName());
        String rawValue = paymentCryptoService.decrypt(entity.getEncryptedValue());
        byte[] content = decodeFileContent(rawValue, entity.getFileValueType());
        String contentType = PaymentCatalogs.trimToNull(entity.getFileContentType());
        return new PaymentSecretDownloadResponse(
                fileName == null ? entity.getSecretName() : fileName,
                contentType == null ? "application/octet-stream" : contentType,
                content
        );
    }

    private PaymentMerchantEntity requireMerchant(Long merchantId) {
        PaymentMerchantEntity merchantEntity = paymentMerchantMapper.findEntityById(merchantId);
        if (merchantEntity == null) {
            throw new IllegalArgumentException("支付商户不存在");
        }
        return merchantEntity;
    }

    private PaymentSecretEntity requireSecret(Long merchantId, Long secretId) {
        PaymentSecretEntity entity = paymentSecretMapper.findEntityById(secretId);
        if (entity == null || !merchantId.equals(entity.getMerchantId())) {
            throw new IllegalArgumentException("商户秘钥不存在");
        }
        return entity;
    }

    private PaymentSecretEntity baseEntity(Long merchantId,
                                           String secretName,
                                           String secretType,
                                           String status,
                                           LocalDateTime validFrom,
                                           LocalDateTime validTo,
                                           String remark) {
        PaymentSecretEntity entity = new PaymentSecretEntity();
        entity.setMerchantId(merchantId);
        entity.setSecretName(secretName);
        entity.setSecretType(PaymentCatalogs.normalizeSecretType(secretType));
        entity.setVersionNo(1);
        entity.setStatus(PaymentCatalogs.normalizeStatus(status == null ? "ACTIVE" : status, "status"));
        entity.setValidFrom(validFrom);
        entity.setValidTo(validTo);
        entity.setRemark(PaymentCatalogs.trimToNull(remark));
        return entity;
    }

    private void applyTextSecretValue(PaymentSecretEntity entity, String rawValue) {
        entity.setEncryptedValue(paymentCryptoService.encrypt(rawValue));
        entity.setMaskedValue(paymentCryptoService.mask(rawValue));
        entity.setFingerprint(paymentCryptoService.fingerprint(rawValue));
        entity.setAlgorithm(paymentCryptoService.algorithm());
        entity.setSourceType("TEXT");
        entity.setFileName(null);
        entity.setFileContentType(null);
        entity.setFileValueType(null);
    }

    private void applyFileSecretValue(PaymentSecretEntity entity, String fileValueType, MultipartFile file) {
        String rawValue = readSecretFileValue(fileValueType, file);
        entity.setEncryptedValue(paymentCryptoService.encrypt(rawValue));
        entity.setMaskedValue(paymentCryptoService.mask(rawValue));
        entity.setFingerprint(paymentCryptoService.fingerprint(rawValue));
        entity.setAlgorithm(paymentCryptoService.algorithm());
        entity.setSourceType("FILE");
        entity.setFileName(PaymentCatalogs.trimToNull(file.getOriginalFilename()));
        entity.setFileContentType(PaymentCatalogs.trimToNull(file.getContentType()));
        entity.setFileValueType(fileValueType);
    }

    private void keepExistingSecretValue(PaymentSecretEntity target, PaymentSecretEntity existing) {
        target.setEncryptedValue(existing.getEncryptedValue());
        target.setMaskedValue(existing.getMaskedValue());
        target.setFingerprint(existing.getFingerprint());
        target.setAlgorithm(existing.getAlgorithm());
        target.setSourceType(existing.getSourceType());
        target.setFileName(existing.getFileName());
        target.setFileContentType(existing.getFileContentType());
        target.setFileValueType(existing.getFileValueType());
    }

    private void ensureSecretNameAvailable(Long merchantId, String secretName, Long currentId) {
        PaymentSecretEntity existing = paymentSecretMapper.findLatestEntityByMerchantIdAndName(merchantId, secretName);
        if (existing != null && !existing.getId().equals(currentId)) {
            throw new IllegalArgumentException("同一商户下秘钥名称已存在");
        }
    }

    private byte[] decodeFileContent(String rawValue, String fileValueType) {
        if (!"BINARY".equals(fileValueType)) {
            return rawValue.getBytes(StandardCharsets.UTF_8);
        }
        try {
            return Base64.getDecoder().decode(rawValue);
        } catch (IllegalArgumentException ex) {
            return rawValue.getBytes(StandardCharsets.UTF_8);
        }
    }

    private String readSecretFileValue(String fileValueType, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("秘钥文件不能为空");
        }
        try {
            byte[] bytes = file.getBytes();
            if ("TEXT".equals(fileValueType)) {
                return PaymentCatalogs.requireText(new String(bytes, StandardCharsets.UTF_8), "secretValue");
            }
            return Base64.getEncoder().encodeToString(bytes);
        } catch (IOException ex) {
            throw new IllegalArgumentException("秘钥文件读取失败", ex);
        }
    }
}
