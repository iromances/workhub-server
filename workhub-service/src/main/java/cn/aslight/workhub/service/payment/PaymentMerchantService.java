package cn.aslight.workhub.service.payment;

import cn.aslight.workhub.dao.payment.PaymentChannelMapper;
import cn.aslight.workhub.dao.payment.PaymentMerchantMapper;
import cn.aslight.workhub.dao.payment.PaymentMerchantParamMapper;
import cn.aslight.workhub.dao.payment.PaymentMerchantCredentialMapper;
import cn.aslight.workhub.dao.payment.PaymentSecretMapper;
import cn.aslight.workhub.model.payment.PaymentChannelEntity;
import cn.aslight.workhub.model.payment.PaymentMerchantDetailResponse;
import cn.aslight.workhub.model.payment.PaymentMerchantDetailView;
import cn.aslight.workhub.model.payment.PaymentMerchantEntity;
import cn.aslight.workhub.model.payment.PaymentMerchantSaveRequest;
import cn.aslight.workhub.model.payment.PaymentMerchantSummaryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 支付商户服务。
 */
@Service
public class PaymentMerchantService {

    private final PaymentMerchantMapper paymentMerchantMapper;
    private final PaymentChannelMapper paymentChannelMapper;
    private final PaymentMerchantParamMapper paymentMerchantParamMapper;
    private final PaymentMerchantCredentialMapper paymentMerchantCredentialMapper;
    private final PaymentSecretMapper paymentSecretMapper;
    private final PaymentAuditService paymentAuditService;

    public PaymentMerchantService(PaymentMerchantMapper paymentMerchantMapper,
	                                  PaymentChannelMapper paymentChannelMapper,
	                                  PaymentMerchantParamMapper paymentMerchantParamMapper,
                                      PaymentMerchantCredentialMapper paymentMerchantCredentialMapper,
	                                  PaymentSecretMapper paymentSecretMapper,
	                                  PaymentAuditService paymentAuditService) {
        this.paymentMerchantMapper = paymentMerchantMapper;
        this.paymentChannelMapper = paymentChannelMapper;
        this.paymentMerchantParamMapper = paymentMerchantParamMapper;
        this.paymentMerchantCredentialMapper = paymentMerchantCredentialMapper;
        this.paymentSecretMapper = paymentSecretMapper;
        this.paymentAuditService = paymentAuditService;
    }

    public List<PaymentMerchantSummaryResponse> list(String status,
                                                     Long channelId,
                                                     Long projectId,
                                                     String purposeCode,
                                                     String keyword) {
        return paymentMerchantMapper.findAll(
                PaymentCatalogs.trimToNull(status) == null ? null : PaymentCatalogs.normalizeStatus(status, "status"),
                channelId,
                projectId,
                PaymentCatalogs.trimToNull(purposeCode) == null ? null : PaymentCatalogs.normalizePurpose(purposeCode),
                PaymentCatalogs.trimToNull(keyword)
        ).stream().map(this::withPurposeCodes).toList();
    }

    public PaymentMerchantDetailResponse detail(Long id) {
        PaymentMerchantDetailView detail = paymentMerchantMapper.findDetailById(id);
        if (detail == null) {
            throw new IllegalArgumentException("支付商户不存在");
        }
        return new PaymentMerchantDetailResponse(
                detail.id(),
                detail.channelId(),
                detail.channelCode(),
                detail.channelName(),
                detail.merchantCode(),
                detail.merchantName(),
                detail.environment(),
                detail.appId(),
                detail.settlementSubject(),
                detail.status(),
                detail.remark(),
                paymentMerchantMapper.findPurposeCodes(id),
                paymentMerchantParamMapper.findByMerchantId(id),
                paymentSecretMapper.findByMerchantId(id),
                paymentMerchantCredentialMapper.findByMerchantId(id),
                detail.createdAt(),
                detail.updatedAt()
        );
    }

    public PaymentMerchantEntity requireExisting(Long id) {
        PaymentMerchantEntity entity = paymentMerchantMapper.findEntityById(id);
        if (entity == null) {
            throw new IllegalArgumentException("支付商户不存在");
        }
        return entity;
    }

    @Transactional
    public PaymentMerchantDetailResponse create(PaymentMerchantSaveRequest request, String operatorUserName) {
        requireActiveOrInactiveChannel(request.getChannelId());
        ensureUniqueKey(request.getChannelId(), request.getMerchantCode(), request.getEnvironment(), null);
        PaymentMerchantEntity entity = toEntity(request);
        paymentMerchantMapper.insert(entity);
        savePurposes(entity.getId(), normalizePurposeCodes(request.getPurposeCodes()));
        paymentAuditService.record(
                "MERCHANT",
                entity.getId(),
                "CREATE",
                "新增支付商户",
                "merchantCode=" + entity.getMerchantCode() + ",environment=" + entity.getEnvironment(),
                operatorUserName
        );
        return detail(entity.getId());
    }

    @Transactional
    public PaymentMerchantDetailResponse update(Long id, PaymentMerchantSaveRequest request, String operatorUserName) {
        requireExisting(id);
        requireActiveOrInactiveChannel(request.getChannelId());
        ensureUniqueKey(request.getChannelId(), request.getMerchantCode(), request.getEnvironment(), id);
        PaymentMerchantEntity entity = toEntity(request);
        entity.setId(id);
        paymentMerchantMapper.update(entity);
        savePurposes(id, normalizePurposeCodes(request.getPurposeCodes()));
        paymentAuditService.record(
                "MERCHANT",
                id,
                "UPDATE",
                "更新支付商户",
                "merchantCode=" + entity.getMerchantCode() + ",environment=" + entity.getEnvironment(),
                operatorUserName
        );
        return detail(id);
    }

    private void ensureUniqueKey(Long channelId, String merchantCode, String environment, Long currentId) {
        PaymentMerchantEntity existing = paymentMerchantMapper.findEntityByUniqueKey(
                channelId,
                PaymentCatalogs.requireText(merchantCode, "merchantCode"),
                PaymentCatalogs.normalizeEnvironment(environment)
        );
        if (existing != null && !existing.getId().equals(currentId)) {
            throw new IllegalArgumentException("同一渠道下商户号与环境组合已存在");
        }
    }

    private void requireActiveOrInactiveChannel(Long channelId) {
        PaymentChannelEntity channelEntity = paymentChannelMapper.findEntityById(channelId);
        if (channelEntity == null) {
            throw new IllegalArgumentException("支付渠道不存在");
        }
    }

    private PaymentMerchantEntity toEntity(PaymentMerchantSaveRequest request) {
        PaymentMerchantEntity entity = new PaymentMerchantEntity();
        entity.setChannelId(request.getChannelId());
        entity.setMerchantCode(PaymentCatalogs.requireText(request.getMerchantCode(), "merchantCode"));
        entity.setMerchantName(PaymentCatalogs.requireText(request.getMerchantName(), "merchantName"));
        entity.setEnvironment(PaymentCatalogs.normalizeEnvironment(request.getEnvironment()));
        entity.setStatus(PaymentCatalogs.normalizeStatus(request.getStatus(), "status"));
        entity.setAppId(PaymentCatalogs.trimToNull(request.getAppId()));
        entity.setSettlementSubject(PaymentCatalogs.trimToNull(request.getSettlementSubject()));
        entity.setRemark(PaymentCatalogs.trimToNull(request.getRemark()));
        return entity;
    }

    public List<String> listPurposeCodes(Long merchantId) {
        requireExisting(merchantId);
        return paymentMerchantMapper.findPurposeCodes(merchantId);
    }

    private PaymentMerchantSummaryResponse withPurposeCodes(PaymentMerchantSummaryResponse response) {
        return new PaymentMerchantSummaryResponse(
                response.id(),
                response.channelId(),
                response.channelCode(),
                response.channelName(),
                response.merchantCode(),
                response.merchantName(),
                response.environment(),
                response.appId(),
                paymentMerchantMapper.findPurposeCodes(response.id()),
                response.status()
        );
    }

    private List<String> normalizePurposeCodes(List<String> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String item : source) {
            String purposeCode = PaymentCatalogs.normalizePurpose(item);
            if (!normalized.contains(purposeCode)) {
                normalized.add(purposeCode);
            }
        }
        return normalized;
    }

    private void savePurposes(Long merchantId, List<String> purposeCodes) {
        paymentMerchantMapper.deletePurposes(merchantId);
        for (String purposeCode : purposeCodes) {
            paymentMerchantMapper.insertPurpose(merchantId, purposeCode);
        }
    }
}
