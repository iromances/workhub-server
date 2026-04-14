package cn.aslight.workhub.service.payment;

import cn.aslight.workhub.dao.payment.PaymentOperationLogMapper;
import org.springframework.stereotype.Service;

/**
 * 支付配置审计服务。
 */
@Service
public class PaymentAuditService {

    private final PaymentOperationLogMapper paymentOperationLogMapper;

    public PaymentAuditService(PaymentOperationLogMapper paymentOperationLogMapper) {
        this.paymentOperationLogMapper = paymentOperationLogMapper;
    }

    public void record(String bizType,
                       Long bizId,
                       String actionType,
                       String actionSummary,
                       String detailText,
                       String operatorUserName) {
        paymentOperationLogMapper.insert(
                bizType,
                bizId,
                actionType,
                actionSummary,
                detailText,
                PaymentCatalogs.trimToNull(operatorUserName) == null ? "system" : operatorUserName.trim()
        );
    }
}
