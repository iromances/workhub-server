package cn.aslight.workhub.service.payment;

import cn.aslight.workhub.dao.payment.PaymentMerchantMapper;
import cn.aslight.workhub.dao.payment.PaymentMerchantParamMapper;
import cn.aslight.workhub.model.payment.PaymentMerchantEntity;
import cn.aslight.workhub.model.payment.PaymentMerchantParamEntity;
import cn.aslight.workhub.model.payment.PaymentMerchantParamResponse;
import cn.aslight.workhub.model.payment.PaymentMerchantParamSaveRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 支付商户参数服务。
 */
@Service
public class PaymentMerchantParamService {

    private final PaymentMerchantMapper paymentMerchantMapper;
    private final PaymentMerchantParamMapper paymentMerchantParamMapper;
    private final PaymentCryptoService paymentCryptoService;
    private final PaymentAuditService paymentAuditService;

    public PaymentMerchantParamService(PaymentMerchantMapper paymentMerchantMapper,
                                       PaymentMerchantParamMapper paymentMerchantParamMapper,
                                       PaymentCryptoService paymentCryptoService,
                                       PaymentAuditService paymentAuditService) {
        this.paymentMerchantMapper = paymentMerchantMapper;
        this.paymentMerchantParamMapper = paymentMerchantParamMapper;
        this.paymentCryptoService = paymentCryptoService;
        this.paymentAuditService = paymentAuditService;
    }

    public List<PaymentMerchantParamResponse> list(Long merchantId) {
        requireMerchant(merchantId);
        return paymentMerchantParamMapper.findByMerchantId(merchantId);
    }

    @Transactional
    public List<PaymentMerchantParamResponse> save(Long merchantId,
                                                   PaymentMerchantParamSaveRequest request,
                                                   String operatorUserName) {
        requireMerchant(merchantId);
        String paramKey = PaymentCatalogs.requireText(request.getParamKey(), "paramKey");
        PaymentMerchantParamEntity existing = paymentMerchantParamMapper.findEntityByMerchantIdAndKey(merchantId, paramKey);
        PaymentMerchantParamEntity entity = toEntity(merchantId, paramKey, request);
        if (existing == null) {
            paymentMerchantParamMapper.insert(entity);
            paymentAuditService.record(
                    "MERCHANT_PARAM",
                    entity.getId(),
                    "CREATE",
                    "新增商户参数",
                    "merchantId=" + merchantId + ",paramKey=" + paramKey,
                    operatorUserName
            );
        } else {
            entity.setId(existing.getId());
            paymentMerchantParamMapper.update(entity);
            paymentAuditService.record(
                    "MERCHANT_PARAM",
                    existing.getId(),
                    "UPDATE",
                    "更新商户参数",
                    "merchantId=" + merchantId + ",paramKey=" + paramKey,
                    operatorUserName
            );
        }
        return list(merchantId);
    }

    @Transactional
    public List<PaymentMerchantParamResponse> update(Long merchantId,
                                                     Long paramId,
                                                     PaymentMerchantParamSaveRequest request,
                                                     String operatorUserName) {
        requireMerchant(merchantId);
        PaymentMerchantParamEntity existing = paymentMerchantParamMapper.findEntityById(paramId);
        if (existing == null || !merchantId.equals(existing.getMerchantId())) {
            throw new IllegalArgumentException("商户参数不存在");
        }
        PaymentMerchantParamEntity entity = toEntity(merchantId, existing.getParamKey(), request);
        entity.setId(paramId);
        paymentMerchantParamMapper.update(entity);
        paymentAuditService.record(
                "MERCHANT_PARAM",
                paramId,
                "UPDATE",
                "更新商户参数",
                "merchantId=" + merchantId + ",paramKey=" + existing.getParamKey(),
                operatorUserName
        );
        return list(merchantId);
    }

    private PaymentMerchantParamEntity toEntity(Long merchantId,
                                                String paramKey,
                                                PaymentMerchantParamSaveRequest request) {
        boolean sensitive = Boolean.TRUE.equals(request.getSensitive());
        String rawValue = PaymentCatalogs.requireText(request.getParamValue(), "paramValue");
        PaymentMerchantParamEntity entity = new PaymentMerchantParamEntity();
        entity.setMerchantId(merchantId);
        entity.setParamKey(paramKey);
        entity.setValueType(PaymentCatalogs.normalizeValueType(request.getValueType()));
        entity.setSensitiveFlag(sensitive);
        entity.setPlainValue(sensitive ? null : rawValue);
        entity.setEncryptedValue(sensitive ? paymentCryptoService.encrypt(rawValue) : null);
        entity.setMaskedValue(sensitive ? paymentCryptoService.mask(rawValue) : rawValue);
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
