package com.lease.xxt.pay.business.refund.impl;

import cn.hutool.core.util.StrUtil;
import com.lease.xxt.pay.business.refund.RefundOrderUpdateBiz;
import com.lease.xxt.pay.business.refund.RefundResultConsumeBiz;
import com.lease.xxt.pay.business.refund.RefundResultNotifyBiz;
import com.lease.xxt.pay.business.refund.dto.RefundConsumeUpdateResult;
import com.lease.xxt.pay.common.enums.PaymentStatusEnum;
import com.lease.xxt.pay.model.bo.result.RefundNotifyResult;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 统一退款结果处理器（双重幂等）。
 *
 * <p>同时服务「退款申请同步返回」「定时任务查单」和「渠道异步回调」多路来源。
 * 双重幂等：Redisson 分布式锁（防并发进入）+ 数据库状态判断（防锁过期后重复处理）。
 *
 * <p>锁内仅执行「DB 状态检查 + 变更」，通知逻辑移到锁外执行，减少锁持有时间。
 *
 * @author Codex
 * @date 2026/4/22
 */
@Service
@Slf4j
public class RefundResultConsumeBizImpl implements RefundResultConsumeBiz {

    private static final String LOCK_KEY_PREFIX = "refund:result:lock:";
    private static final long LOCK_EXPIRE_SECONDS = 30L;

    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private RefundOrderUpdateBiz refundOrderUpdateBiz;
    @Autowired
    private RefundResultNotifyBiz refundResultNotifyBiz;

    @Override
    public void handle(RefundNotifyResult refundResult) {
        if (!validateInput(refundResult)) {
            return;
        }
        String platformSerialNo = refundResult.getPlatformSerialNo();
        PaymentStatusEnum status = refundResult.getStatus();
        log.info("[{}] 退款结果处理器开始，来源={}, 状态={}",
                platformSerialNo, refundResult.getSource(), status);
        if (!isFinalStatus(status)) {
            log.info("[{}] 非终态退款结果({})，按策略跳过", platformSerialNo, status);
            return;
        }

        RLock lock = redissonClient.getLock(LOCK_KEY_PREFIX + platformSerialNo);
        RefundConsumeUpdateResult updateResult = null;
        boolean locked = false;
        try {
            locked = lock.tryLock(0, LOCK_EXPIRE_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                log.info("[{}] 正在被其他线程处理，跳过本轮退款消费", platformSerialNo);
                return;
            }
            // 时序：无事务加锁 -> 锁内调用事务更新 -> 事务提交 -> 释放锁 -> 锁外触发异步通知。
            updateResult = refundOrderUpdateBiz.updateFinal(platformSerialNo, refundResult);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[{}] 退款结果锁获取被中断", platformSerialNo, e);
            return;
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
        notifyBusinessIfNecessary(platformSerialNo, updateResult);
    }

    /**
     * 按终态消费结果触发退款通知。
     *
     * @param requestSerialNo 入参退款流水号
     * @param updateResult    终态消费结果
     */
    private void notifyBusinessIfNecessary(String requestSerialNo,
                                           RefundConsumeUpdateResult updateResult) {
        if (updateResult == null) {
            return;
        }
        RefundConsumeUpdateResult.UpdateStatus consumeStatus = updateResult.getStatus();
        if (consumeStatus == null) {
            log.warn("[{}] 退款终态消费结果状态为空，跳过通知", requestSerialNo);
            return;
        }

        if (RefundConsumeUpdateResult.UpdateStatus.ORDER_NOT_FOUND.equals(consumeStatus)) {
            log.info("[{}] 退款终态消费未更新，result={}", requestSerialNo, consumeStatus);
            return;
        }
        if (!RefundConsumeUpdateResult.UpdateStatus.UPDATED.equals(consumeStatus)
                && !RefundConsumeUpdateResult.UpdateStatus.ALREADY_FINAL.equals(consumeStatus)) {
            log.warn("[{}] 未识别的退款终态消费结果，result={}", requestSerialNo, consumeStatus);
            return;
        }
        if (RefundConsumeUpdateResult.UpdateStatus.ALREADY_FINAL.equals(consumeStatus)) {
            log.info("[{}] 退款终态结果已由其他链路落库，补触发业务通知, result={}",
                    requestSerialNo, consumeStatus);
        }

        String refundSerialNo = updateResult.getRefundSerialNo();
        if (StrUtil.isBlank(refundSerialNo)) {
            log.warn("[{}] 退款终态消费成功但退款主订单流水为空，跳过通知", requestSerialNo);
            return;
        }
        refundResultNotifyBiz.notifyAsync(refundSerialNo);
    }

    /**
     * 校验输入参数。
     *
     * @param refundResult 退款结果
     * @return true-合法，false-跳过
     */
    private boolean validateInput(RefundNotifyResult refundResult) {
        if (refundResult == null) {
            log.warn("退款结果为空，跳过处理");
            return false;
        }
        if (StrUtil.isBlank(refundResult.getPlatformSerialNo())) {
            log.warn("退款结果缺少平台退款流水号，跳过处理");
            return false;
        }
        if (refundResult.getStatus() == null) {
            log.warn("[{}] 退款结果缺少状态，跳过处理", refundResult.getPlatformSerialNo());
            return false;
        }
        if (refundResult.getSource() == null) {
            log.warn("[{}] 退款结果缺少来源标识，跳过处理", refundResult.getPlatformSerialNo());
            return false;
        }
        return true;
    }

    /**
     * 是否为终态。
     *
     * @param status 退款状态
     * @return true-终态
     */
    private boolean isFinalStatus(PaymentStatusEnum status) {
        return PaymentStatusEnum.SUCCESS.equals(status) || PaymentStatusEnum.FAILED.equals(status);
    }

}
