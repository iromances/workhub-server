package com.lease.xxt.pay.business.refund.impl;

import cn.hutool.core.util.StrUtil;
import com.lease.xxt.pay.business.refund.RefundOrderUpdateBiz;
import com.lease.xxt.pay.business.refund.dto.RefundConsumeUpdateResult;
import com.lease.xxt.pay.common.enums.PaymentStatusEnum;
import com.lease.xxt.pay.common.exception.BizException;
import com.lease.xxt.pay.common.exception.BizExceptionEnum;
import com.lease.xxt.pay.mapper.OrderChannelMapper;
import com.lease.xxt.pay.mapper.RefundOrderMapper;
import com.lease.xxt.pay.model.bo.result.RefundNotifyResult;
import com.lease.xxt.pay.model.entity.OrderChannelEntity;
import com.lease.xxt.pay.model.entity.RefundOrderEntity;
import com.lease.xxt.pay.model.gateway.result.GatewayRefundResult;
import com.lease.xxt.pay.service.OrderChannelService;
import com.lease.xxt.pay.service.RefundOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Date;

/**
 * 退款订单更新业务实现。
 *
 * <p>对齐支付结果处理链路：非终态仅更新处理中响应信息，
 * 终态通过条件更新保证先到先得，再由锁外统一触发通知。
 *
 * @author Codex
 * @since 2026-04-11
 */
@Service
@Slf4j
public class RefundOrderUpdateBizImpl implements RefundOrderUpdateBiz {

    @Autowired
    private RefundOrderService refundOrderService;
    @Autowired
    private OrderChannelService orderChannelService;
    @Autowired
    private RefundOrderMapper refundOrderMapper;
    @Autowired
    private OrderChannelMapper orderChannelMapper;

    @Override
    public void updateProcessing(RefundOrderEntity refundOrder,
                                 OrderChannelEntity refundChannel,
                                 GatewayRefundResult channelResp) {
        if (refundOrder == null || refundChannel == null) {
            return;
        }
        if (!needProcessingUpdate(channelResp)) {
            log.info("[{}] 同步退款结果未携带新增渠道信息，跳过重复PROCESSING更新", refundOrder.getSerialNo());
            return;
        }

        RefundOrderEntity latestRefundOrder = refundOrderService.getBySerialNo(refundOrder.getSerialNo());
        if (latestRefundOrder == null) {
            log.warn("[{}] 退款主订单不存在，跳过处理中更新", refundOrder.getSerialNo());
            return;
        }
        if (PaymentStatusEnum.isComplete(latestRefundOrder.getStatus())) {
            log.info("[{}] 退款主订单已终态({})，跳过处理中更新",
                    refundOrder.getSerialNo(), latestRefundOrder.getStatus());
            return;
        }

        OrderChannelEntity latestRefundChannel = orderChannelService.getBySerialNo(refundChannel.getSerialNo());
        if (latestRefundChannel == null) {
            log.warn("[{}] 退款渠道订单不存在，跳过处理中更新", refundChannel.getSerialNo());
            return;
        }
        if (PaymentStatusEnum.isComplete(latestRefundChannel.getStatus())) {
            log.info("[{}] 退款渠道订单已终态({})，跳过处理中更新",
                    refundChannel.getSerialNo(), latestRefundChannel.getStatus());
            return;
        }

        String responseCode = resolveProcessingRespCode(channelResp);
        String responseMsg = resolveProcessingRespMsg(channelResp);

        RefundOrderEntity refundUpdate = new RefundOrderEntity();
        refundUpdate.setId(latestRefundOrder.getId());
        refundUpdate.setStatus(PaymentStatusEnum.PROCESSING);
        refundUpdate.setModifyTime(new Date());

        OrderChannelEntity channelUpdate = new OrderChannelEntity();
        channelUpdate.setId(latestRefundChannel.getId());
        channelUpdate.setStatus(PaymentStatusEnum.PROCESSING);
        channelUpdate.setResponseCode(responseCode);
        channelUpdate.setResponseMsg(responseMsg);
        channelUpdate.setModifyTime(new Date());
        String channelRefundSn = resolveChannelRefundSn(channelResp);
        if (StrUtil.isNotBlank(channelRefundSn)) {
            channelUpdate.setChannelSn(channelRefundSn);
        }

        boolean refundUpdated = refundOrderService.updateById(refundUpdate);
        boolean channelUpdated = orderChannelService.updateById(channelUpdate);
        if (!refundUpdated || !channelUpdated) {
            throw new BizException(BizExceptionEnum.DB_UPDATE_ERROR);
        }
        log.info("[{}] 退款主订单与渠道订单更新为PROCESSING成功", refundOrder.getSerialNo());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RefundConsumeUpdateResult updateFinal(String platformSerialNo,
                                                 RefundNotifyResult refundResult) {
        RefundOrderContext refundOrderContext = loadRefundOrderContext(platformSerialNo);
        if (refundOrderContext == null) {
            log.warn("[{}] 退款订单不存在，终态更新跳过", platformSerialNo);
            return RefundConsumeUpdateResult.orderNotFound();
        }

        RefundOrderEntity refundOrder = refundOrderContext.getRefundOrder();
        OrderChannelEntity refundChannel = refundOrderContext.getRefundChannel();
        if (PaymentStatusEnum.isComplete(refundChannel.getStatus())) {
            log.info("[{}] 退款渠道订单已终态({})，跳过终态更新",
                    platformSerialNo, refundChannel.getStatus());
            return RefundConsumeUpdateResult.alreadyFinal(refundOrder.getSerialNo());
        }
        if (PaymentStatusEnum.isComplete(refundOrder.getStatus())) {
            log.info("[{}] 退款主订单已终态({})，跳过终态更新",
                    platformSerialNo, refundOrder.getStatus());
            return RefundConsumeUpdateResult.alreadyFinal(refundOrder.getSerialNo());
        }

        PaymentStatusEnum targetStatus = refundResult.getStatus();
        if (!PaymentStatusEnum.SUCCESS.equals(targetStatus) && !PaymentStatusEnum.FAILED.equals(targetStatus)) {
            throw new BizException(BizExceptionEnum.CHECK_ERROR, "仅支持退款终态更新: " + targetStatus);
        }
        LocalDateTime finishTime = resolveCompleteTime(refundResult);
        String channelRefundSn = refundResult.getChannelSerialNo();
        int channelUpdated = orderChannelMapper.updateFinalStatusIfPending(
                refundChannel.getId(),
                targetStatus,
                finishTime,
                channelRefundSn,
                null,
                refundResult.getChannelRespCode(),
                refundResult.getChannelRespMsg(),
                refundOrder.getRefundAmount());
        if (channelUpdated == 0) {
            return resolveRefundChannelUpdateSkipped(refundOrder.getSerialNo(), refundChannel.getSerialNo());
        }

        int refundUpdated = refundOrderMapper.updateFinalStatusIfPending(
                refundOrder.getId(),
                targetStatus,
                finishTime);
        if (refundUpdated == 0) {
            return resolveRefundUpdateSkipped(refundOrder.getSerialNo());
        }

        log.info("[{}] 退款主订单+渠道订单终态更新完成，status={}",
                refundOrder.getSerialNo(), targetStatus);
        return RefundConsumeUpdateResult.updated(refundOrder.getSerialNo());
    }

    /**
     * 解析渠道退款流水号。
     *
     * @param channelResp 渠道同步响应
     * @return 渠道退款流水号
     */
    private String resolveChannelRefundSn(GatewayRefundResult channelResp) {
        if (channelResp == null) {
            return null;
        }
        return channelResp.getChannelSerialNo();
    }

    /**
     * 判断是否需要补充处理中状态信息。
     *
     * <p>退款建单时主订单和渠道订单已经默认写为 PROCESSING，
     * 同步返回非终态时，只有在携带了新增渠道信息（如渠道退款流水、响应码、响应信息）
     * 或网关返回为空需要补充错误说明时，才有必要再次落库。
     *
     * @param channelResp 渠道同步响应
     * @return true-需要更新，false-可跳过
     */
    private boolean needProcessingUpdate(GatewayRefundResult channelResp) {
        if (channelResp == null) {
            return true;
        }
        return StrUtil.isNotBlank(channelResp.getChannelSerialNo())
                || StrUtil.isNotBlank(channelResp.getRespCode())
                || StrUtil.isNotBlank(channelResp.getRespMsg());
    }

    /**
     * 解析处理中响应码。
     *
     * @param channelResp 渠道同步响应
     * @return 响应码
     */
    private String resolveProcessingRespCode(GatewayRefundResult channelResp) {
        return channelResp == null ? "GATEWAY_ERROR" : channelResp.getRespCode();
    }

    /**
     * 解析处理中响应信息。
     *
     * @param channelResp 渠道同步响应
     * @return 响应信息
     */
    private String resolveProcessingRespMsg(GatewayRefundResult channelResp) {
        return channelResp == null ? "网关响应为空" : channelResp.getRespMsg();
    }

    /**
     * 解析终态完成时间。
     *
     * @param refundResult 统一退款结果
     * @return 完成时间
     */
    private LocalDateTime resolveCompleteTime(RefundNotifyResult refundResult) {
        return refundResult.getCompleteTime() != null ? refundResult.getCompleteTime() : LocalDateTime.now();
    }

    /**
     * 加载退款订单上下文。
     *
     * <p>入参固定为退款渠道订单流水号，即 {@code order_channel.serial_no}。
     *
     * @param platformSerialNo 退款渠道订单流水号
     * @return 退款订单上下文；订单不存在时返回 null
     */
    private RefundOrderContext loadRefundOrderContext(String platformSerialNo) {
        OrderChannelEntity refundChannel = orderChannelService.getBySerialNo(platformSerialNo);
        if (refundChannel == null) {
            return null;
        }
        RefundOrderEntity refundOrder = refundOrderService.getBySerialNo(refundChannel.getOrderBusinessSn());
        if (refundOrder == null) {
            log.warn("[{}] 退款主订单不存在", platformSerialNo);
            return null;
        }
        return new RefundOrderContext(refundOrder, refundChannel);
    }

    /**
     * 处理退款主订单终态更新被跳过场景。
     *
     * @param refundSerialNo 退款主订单流水号
     * @return 结构化更新结果
     */
    private RefundConsumeUpdateResult resolveRefundUpdateSkipped(String refundSerialNo) {
        RefundOrderEntity latestRefundOrder = refundOrderService.getBySerialNo(refundSerialNo);
        if (latestRefundOrder == null) {
            throw new BizException(BizExceptionEnum.CHECK_ERROR, "退款主订单不存在: " + refundSerialNo);
        }
        if (PaymentStatusEnum.isComplete(latestRefundOrder.getStatus())) {
            log.info("[{}] 退款主订单已终态({})，按先到先得跳过",
                    refundSerialNo, latestRefundOrder.getStatus());
            return RefundConsumeUpdateResult.alreadyFinal(latestRefundOrder.getSerialNo());
        }
        throw new BizException(BizExceptionEnum.DB_UPDATE_ERROR);
    }

    /**
     * 处理退款渠道订单终态更新被跳过场景。
     *
     * @param refundSerialNo        退款流水号
     * @param refundChannelSerialNo 退款渠道流水号
     * @return 结构化更新结果
     */
    private RefundConsumeUpdateResult resolveRefundChannelUpdateSkipped(String refundSerialNo,
                                                                        String refundChannelSerialNo) {
        OrderChannelEntity latestRefundChannel = orderChannelService.getBySerialNo(refundChannelSerialNo);
        if (latestRefundChannel == null) {
            log.warn("[{}] 退款渠道订单不存在，终态更新跳过", refundSerialNo);
            return RefundConsumeUpdateResult.orderNotFound();
        }
        if (PaymentStatusEnum.isComplete(latestRefundChannel.getStatus())) {
            log.info("[{}] 退款渠道订单已终态({})，按先到先得跳过",
                    refundSerialNo, latestRefundChannel.getStatus());
            return RefundConsumeUpdateResult.alreadyFinal(refundSerialNo);
        }
        throw new BizException(BizExceptionEnum.DB_UPDATE_ERROR);
    }

    /**
     * 退款终态更新上下文。
     */
    private static final class RefundOrderContext {

        /**
         * 退款主订单。
         */
        private final RefundOrderEntity refundOrder;

        /**
         * 退款渠道订单。
         */
        private final OrderChannelEntity refundChannel;

        /**
         * 构造退款订单上下文。
         *
         * @param refundOrder   退款主订单
         * @param refundChannel 退款渠道订单
         */
        private RefundOrderContext(RefundOrderEntity refundOrder,
                                   OrderChannelEntity refundChannel) {
            this.refundOrder = refundOrder;
            this.refundChannel = refundChannel;
        }

        /**
         * 获取退款主订单。
         *
         * @return 退款主订单
         */
        private RefundOrderEntity getRefundOrder() {
            return refundOrder;
        }

        /**
         * 获取退款渠道订单。
         *
         * @return 退款渠道订单
         */
        private OrderChannelEntity getRefundChannel() {
            return refundChannel;
        }
    }
}
