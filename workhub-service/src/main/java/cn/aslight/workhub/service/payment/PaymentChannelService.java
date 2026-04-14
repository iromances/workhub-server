package cn.aslight.workhub.service.payment;

import cn.aslight.workhub.dao.payment.PaymentChannelMapper;
import cn.aslight.workhub.model.payment.PaymentChannelDetailResponse;
import cn.aslight.workhub.model.payment.PaymentChannelEntity;
import cn.aslight.workhub.model.payment.PaymentChannelSaveRequest;
import cn.aslight.workhub.model.payment.PaymentChannelSummaryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * 支付渠道服务。
 */
@Service
public class PaymentChannelService {

    private final PaymentChannelMapper paymentChannelMapper;
    private final PaymentAuditService paymentAuditService;

    public PaymentChannelService(PaymentChannelMapper paymentChannelMapper,
                                 PaymentAuditService paymentAuditService) {
        this.paymentChannelMapper = paymentChannelMapper;
        this.paymentAuditService = paymentAuditService;
    }

    public List<PaymentChannelSummaryResponse> list(String status, String keyword) {
        return paymentChannelMapper.findAll(
                PaymentCatalogs.trimToNull(status) == null ? null : PaymentCatalogs.normalizeStatus(status, "status"),
                PaymentCatalogs.trimToNull(keyword)
        );
    }

    public PaymentChannelDetailResponse detail(Long id) {
        PaymentChannelDetailResponse detail = paymentChannelMapper.findDetailById(id);
        if (detail == null) {
            throw new IllegalArgumentException("支付渠道不存在");
        }
        return detail;
    }

    public PaymentChannelEntity requireExisting(Long id) {
        PaymentChannelEntity entity = paymentChannelMapper.findEntityById(id);
        if (entity == null) {
            throw new IllegalArgumentException("支付渠道不存在");
        }
        return entity;
    }

    @Transactional
    public PaymentChannelDetailResponse create(PaymentChannelSaveRequest request, String operatorUserName) {
        ensureCodeAvailable(request.getCode(), null);
        PaymentChannelEntity entity = toEntity(request);
        paymentChannelMapper.insert(entity);
        paymentAuditService.record(
                "CHANNEL",
                entity.getId(),
                "CREATE",
                "新增支付渠道",
                "channelCode=" + entity.getChannelCode(),
                operatorUserName
        );
        return detail(entity.getId());
    }

    @Transactional
    public PaymentChannelDetailResponse update(Long id, PaymentChannelSaveRequest request, String operatorUserName) {
        requireExisting(id);
        ensureCodeAvailable(request.getCode(), id);
        PaymentChannelEntity entity = toEntity(request);
        entity.setId(id);
        paymentChannelMapper.update(entity);
        paymentAuditService.record(
                "CHANNEL",
                id,
                "UPDATE",
                "更新支付渠道",
                "channelCode=" + entity.getChannelCode(),
                operatorUserName
        );
        return detail(id);
    }

    private void ensureCodeAvailable(String code, Long currentId) {
        String normalizedCode = PaymentCatalogs.requireText(code, "code").toUpperCase(Locale.ROOT);
        PaymentChannelEntity existing = paymentChannelMapper.findEntityByCode(normalizedCode);
        if (existing != null && !existing.getId().equals(currentId)) {
            throw new IllegalArgumentException("渠道编码已存在");
        }
    }

    private PaymentChannelEntity toEntity(PaymentChannelSaveRequest request) {
        PaymentChannelEntity entity = new PaymentChannelEntity();
        entity.setChannelCode(PaymentCatalogs.requireText(request.getCode(), "code").toUpperCase(Locale.ROOT));
        entity.setChannelName(PaymentCatalogs.requireText(request.getName(), "name"));
        entity.setVendorName(PaymentCatalogs.requireText(request.getVendorName(), "vendorName"));
        entity.setStatus(PaymentCatalogs.normalizeStatus(request.getStatus(), "status"));
        entity.setDescription(PaymentCatalogs.trimToNull(request.getDescription()));
        return entity;
    }
}
