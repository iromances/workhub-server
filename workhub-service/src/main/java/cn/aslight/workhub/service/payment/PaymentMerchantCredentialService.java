package cn.aslight.workhub.service.payment;

import cn.aslight.workhub.dao.payment.PaymentMerchantCredentialMapper;
import cn.aslight.workhub.dao.payment.PaymentMerchantMapper;
import cn.aslight.workhub.model.payment.PaymentMerchantCredentialEntity;
import cn.aslight.workhub.model.payment.PaymentMerchantCredentialResponse;
import cn.aslight.workhub.model.payment.PaymentMerchantCredentialSaveRequest;
import cn.aslight.workhub.model.payment.PaymentMerchantEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 商户敏感凭据服务。
 */
@Service
public class PaymentMerchantCredentialService {

    private final PaymentMerchantMapper paymentMerchantMapper;
    private final PaymentMerchantCredentialMapper paymentMerchantCredentialMapper;
    private final PaymentCryptoService paymentCryptoService;
    private final PaymentAuditService paymentAuditService;

    public PaymentMerchantCredentialService(PaymentMerchantMapper paymentMerchantMapper,
                                            PaymentMerchantCredentialMapper paymentMerchantCredentialMapper,
                                            PaymentCryptoService paymentCryptoService,
                                            PaymentAuditService paymentAuditService) {
        this.paymentMerchantMapper = paymentMerchantMapper;
        this.paymentMerchantCredentialMapper = paymentMerchantCredentialMapper;
        this.paymentCryptoService = paymentCryptoService;
        this.paymentAuditService = paymentAuditService;
    }

    public List<PaymentMerchantCredentialResponse> list(Long merchantId) {
        requireMerchant(merchantId);
        return paymentMerchantCredentialMapper.findByMerchantId(merchantId);
    }

    @Transactional
    public List<PaymentMerchantCredentialResponse> save(Long merchantId,
                                                        PaymentMerchantCredentialSaveRequest request,
                                                        String operatorUserName) {
        requireMerchant(merchantId);
        String credentialKey = PaymentCatalogs.requireText(request.getCredentialKey(), "credentialKey");
        PaymentMerchantCredentialEntity existing = paymentMerchantCredentialMapper.findEntityByMerchantIdAndKey(merchantId, credentialKey);
        PaymentMerchantCredentialEntity entity = toEntity(merchantId, credentialKey, request);
        if (existing == null) {
            paymentMerchantCredentialMapper.insert(entity);
            paymentAuditService.record(
                    "MERCHANT_CREDENTIAL",
                    entity.getId(),
                    "CREATE",
                    "新增商户敏感凭据",
                    "merchantId=" + merchantId + ",credentialKey=" + credentialKey,
                    operatorUserName
            );
        } else {
            entity.setId(existing.getId());
            paymentMerchantCredentialMapper.update(entity);
            paymentAuditService.record(
                    "MERCHANT_CREDENTIAL",
                    existing.getId(),
                    "UPDATE",
                    "更新商户敏感凭据",
                    "merchantId=" + merchantId + ",credentialKey=" + credentialKey,
                    operatorUserName
            );
        }
        return list(merchantId);
    }

    @Transactional
    public List<PaymentMerchantCredentialResponse> update(Long merchantId,
                                                          Long credentialId,
                                                          PaymentMerchantCredentialSaveRequest request,
                                                          String operatorUserName) {
        requireMerchant(merchantId);
        PaymentMerchantCredentialEntity existing = paymentMerchantCredentialMapper.findEntityById(credentialId);
        if (existing == null || !merchantId.equals(existing.getMerchantId())) {
            throw new IllegalArgumentException("商户敏感凭据不存在");
        }
        PaymentMerchantCredentialEntity entity = toEntity(merchantId, existing.getCredentialKey(), request);
        entity.setId(credentialId);
        paymentMerchantCredentialMapper.update(entity);
        paymentAuditService.record(
                "MERCHANT_CREDENTIAL",
                credentialId,
                "UPDATE",
                "更新商户敏感凭据",
                "merchantId=" + merchantId + ",credentialKey=" + existing.getCredentialKey(),
                operatorUserName
        );
        return list(merchantId);
    }

    private PaymentMerchantCredentialEntity toEntity(Long merchantId,
                                                     String credentialKey,
                                                     PaymentMerchantCredentialSaveRequest request) {
        String rawValue = PaymentCatalogs.requireText(request.getCredentialValue(), "credentialValue");
        PaymentMerchantCredentialEntity entity = new PaymentMerchantCredentialEntity();
        entity.setMerchantId(merchantId);
        entity.setCredentialKey(credentialKey);
        entity.setCredentialName(PaymentCatalogs.requireText(request.getCredentialName(), "credentialName"));
        entity.setCredentialType(PaymentCatalogs.normalizeCredentialType(request.getCredentialType()));
        entity.setEncryptedValue(Boolean.TRUE.equals(request.getPlainStorage()) ? rawValue : paymentCryptoService.encrypt(rawValue));
        entity.setMaskedValue(paymentCryptoService.mask(rawValue));
        entity.setFingerprint(paymentCryptoService.fingerprint(rawValue));
        entity.setStatus(PaymentCatalogs.normalizeStatus(request.getStatus(), "status"));
        entity.setRemark(PaymentCatalogs.trimToNull(request.getRemark()));
        return entity;
    }

    private PaymentMerchantEntity requireMerchant(Long merchantId) {
        PaymentMerchantEntity merchantEntity = paymentMerchantMapper.findEntityById(merchantId);
        if (merchantEntity == null) {
            throw new IllegalArgumentException("支付商户不存在");
        }
        return merchantEntity;
    }
}
