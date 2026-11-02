package com.lease.xxt.pay.business.refund.impl;

import com.lease.xxt.pay.api.consumer.dto.channel.notify.ChannelResultNotifyRequest;
import com.lease.xxt.pay.business.refund.RefundResultCallBackBiz;
import com.lease.xxt.pay.business.refund.RefundResultConsumeBiz;
import com.lease.xxt.pay.common.enums.PaymentStatusEnum;
import com.lease.xxt.pay.mapper.ChannelCallbackLogMapper;
import com.lease.xxt.pay.model.bo.result.RefundNotifyResult;
import com.lease.xxt.pay.model.entity.ChannelCallbackLogEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * 渠道退款回调通知服务实现。
 *
 * <p>对齐支付回调处理方式：先落渠道回调审计日志，再转换为统一退款结果模型消费。
 *
 * @author Codex
 * @since 2026-04-13
 */
@Slf4j
@Service
public class RefundResultCallBackBizImpl implements RefundResultCallBackBiz {

    /**
     * 日期时间格式化。
     */
    private static final DateTimeFormatter DT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 渠道回调日志 Mapper。
     */
    @Autowired
    private ChannelCallbackLogMapper channelCallbackLogMapper;
    /**
     * 退款结果统一处理器。
     */
    @Autowired
    private RefundResultConsumeBiz refundResultConsumeBiz;

    @Override
    public void processAsync(ChannelResultNotifyRequest request) {
        String refundSerialNo = request.getOrderId();
        log.info("[{}] 退款回调处理开始", refundSerialNo);

        ChannelCallbackLogEntity callbackLog = buildCallbackLog(request);
        channelCallbackLogMapper.insert(callbackLog);
        log.info("[{}] 退款回调记录落库成功, id={}", refundSerialNo, callbackLog.getId());

        try {
            RefundNotifyResult refundResult = convert(request);
            refundResult.setSource(RefundNotifyResult.NotifySource.CHANNEL_CALLBACK);
            refundResultConsumeBiz.handle(refundResult);
            log.info("[{}] 退款回调处理完成", refundSerialNo);
        } catch (Exception e) {
            log.error("[{}] 退款回调处理异常", refundSerialNo, e);
            throw e;
        }
    }

    /**
     * 构建回调记录实体。
     *
     * @param request 渠道回调请求
     * @return 回调记录
     */
    private ChannelCallbackLogEntity buildCallbackLog(ChannelResultNotifyRequest request) {
        ChannelCallbackLogEntity entity = new ChannelCallbackLogEntity();
        entity.setOrderId(request.getOrderId());
        entity.setChannelSn(request.getChannelSn());
        entity.setSubChannelSn(request.getSubChannelSn());
        entity.setTransactionSubMode(request.getTransactionSubMode());
        entity.setChannel(request.getChannel());
        entity.setTradeAmount(request.getSuccAmt());
        entity.setFee(request.getChannelFee());
        entity.setStatus(request.getStatus());
        entity.setChannelCode(request.getRespCode());
        entity.setChannelMsg(request.getRespMsg());
        entity.setReceiveTime(LocalDateTime.now());
        entity.setFinishTime(parseFinishTime(request.getCompleteTime()));
        return entity;
    }

    /**
     * 将渠道回调报文转换为统一退款结果。
     *
     * @param request 渠道回调请求
     * @return 统一退款结果
     */
    private RefundNotifyResult convert(ChannelResultNotifyRequest request) {
        RefundNotifyResult result = new RefundNotifyResult();
        result.setPlatformSerialNo(request.getOrderId());
        result.setChannelSerialNo(request.getChannelSn());
        result.setChannelRespCode(request.getRespCode());
        result.setChannelRespMsg(request.getRespMsg());
        //默认是处理中
        result.setStatus(resolveRefundStatus(request.getStatus()));
        result.setCompleteTime(parseFinishTime(request.getCompleteTime()));
        return result;
    }

    /**
     * 解析退款状态。
     *
     * @param status 渠道状态码
     * @return 退款状态
     */
    private PaymentStatusEnum resolveRefundStatus(Integer status) {
        PaymentStatusEnum refundStatus = PaymentStatusEnum.getByValue(status);
        return refundStatus != null ? refundStatus : PaymentStatusEnum.PROCESSING;
    }

    /**
     * 解析完成时间。
     *
     * @param finishTime 完成时间字符串
     * @return 完成时间
     */
    private LocalDateTime parseFinishTime(String finishTime) {
        if (Objects.isNull(finishTime) || finishTime.isEmpty()) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(finishTime, DT_FORMATTER);
        } catch (Exception e) {
            log.warn("解析退款 finishTime 失败: {}", finishTime);
            return LocalDateTime.now();
        }
    }
}
