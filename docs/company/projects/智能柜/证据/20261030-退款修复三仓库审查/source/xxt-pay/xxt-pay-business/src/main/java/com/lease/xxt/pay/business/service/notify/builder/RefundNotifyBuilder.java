package com.lease.xxt.pay.business.service.notify.builder;

import cn.hutool.core.util.StrUtil;
import com.lease.xxt.pay.business.service.notify.dto.NotifyRequestPayload;
import com.lease.xxt.pay.business.service.notify.dto.RefundNotifyResultRequestDto;
import com.lease.xxt.pay.common.enums.NotifyTypeEnum;
import com.lease.xxt.pay.common.enums.PaymentStatusEnum;
import com.lease.xxt.pay.model.entity.OrderChannelEntity;
import com.lease.xxt.pay.model.entity.OrderNotifyEntity;
import com.lease.xxt.pay.model.entity.RefundOrderEntity;
import com.lease.xxt.pay.service.OrderChannelService;
import com.lease.xxt.pay.service.RefundOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

/**
 * 退款通知报文构建器。
 * （目前复用的是支付通知报文构建器 ol-saps）
 * <p>构建退款结果通知报文，notify_type = 3。
 *
 * @author Codex
 * @since 2026-04-11
 */
@Component
@Slf4j
public class RefundNotifyBuilder implements NotifyRequestBuilder {

    private static final DateTimeFormatter DT_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Autowired
    private RefundOrderService refundOrderService;
    @Autowired
    private OrderChannelService orderChannelService;

    @Override
    public boolean supports(int notifyType) {
        return notifyType == NotifyTypeEnum.REFUND.getValue();
    }

    @Override
    public NotifyRequestPayload build(OrderNotifyEntity orderNotify) {
        String tradeNo = orderNotify != null ? orderNotify.getTradeNo() : null;
        if (StrUtil.isBlank(tradeNo)) {
            log.warn("RefundNotifyBuilder: tradeNo 为空");
            return null;
        }

        try {
            RefundOrderEntity refundOrder = refundOrderService.getBySerialNo(tradeNo);
            if (refundOrder == null) {
                log.warn("RefundNotifyBuilder: 退款订单不存在, tradeNo={}", tradeNo);
                return null;
            }
            OrderChannelEntity refundChannel = orderChannelService.getByOrderBusinessSn(refundOrder.getSerialNo());

            RefundNotifyResultRequestDto payload = new RefundNotifyResultRequestDto();
            payload.setStatus(refundOrder.getStatus() != null ? refundOrder.getStatus().getValue() : null);
            payload.setResCode(refundChannel != null ? refundChannel.getResponseCode() : null);
            payload.setOrderNo(refundOrder.getMerchantSn());
            payload.setPaySerialNo(refundOrder.getSerialNo());
            payload.setChannelSerialNo(resolveChannelSerialNo(refundChannel));
            payload.setFintime(refundOrder.getFinishTime() != null
                    ? refundOrder.getFinishTime().format(DT_FORMATTER) : null);

            if (PaymentStatusEnum.FAILED.equals(refundOrder.getStatus())) {
                payload.setFailMsg(refundChannel != null ? refundChannel.getResponseMsg() : null);
            }
            return payload;
        } catch (Exception e) {
            log.error("RefundNotifyBuilder: 构建报文异常, tradeNo={}", tradeNo, e);
            return null;
        }
    }

    /**
     * 解析渠道退款流水号。
     *
     * @param refundChannel 退款渠道订单
     * @return 渠道退款流水号
     */
    private String resolveChannelSerialNo(OrderChannelEntity refundChannel) {
        return refundChannel != null ? refundChannel.getChannelSn() : null;
    }
}
