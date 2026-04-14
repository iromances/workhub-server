package cn.aslight.workhub.service.payment;

import cn.aslight.workhub.model.payment.PaymentPurposeOptionResponse;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 支付字典服务。
 */
@Service
public class PaymentCatalogService {

    public List<PaymentPurposeOptionResponse> listPurposes() {
        return PaymentCatalogs.listPurposes();
    }
}
