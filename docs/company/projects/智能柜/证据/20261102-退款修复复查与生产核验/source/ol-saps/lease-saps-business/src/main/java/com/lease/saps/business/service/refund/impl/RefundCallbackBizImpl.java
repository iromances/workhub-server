package com.lease.saps.business.service.refund.impl;

import cn.hutool.core.util.StrUtil;
import com.lease.redission.RedissonLock;
import com.lease.saps.business.service.order.CustomerOrderNotifyBiz;
import com.lease.saps.business.service.refund.RefundCallbackBiz;
import com.lease.saps.common.exception.ExcepitionUtil;
import com.lease.saps.common.util.OptimisticLockUtil;
import com.lease.saps.common.util.ThreadPoolTaskConfig;
import com.lease.saps.model.api.payment.RefundApplyResp;
import com.lease.saps.model.dto.replacebattery.DeviceFlowSyncReq;
import com.lease.saps.model.entity.CustomerOrderEntity;
import com.lease.saps.model.entity.CustomerTransactionEntity;
import com.lease.saps.model.enums.DeviceFlowProcessProgressEnum;
import com.lease.saps.model.enums.PaymentStatusEnum;
import com.lease.saps.model.enums.RedisPrefixKeyEnum;
import com.lease.saps.service.CustomerOrderService;
import com.lease.saps.service.CustomerTransactionService;
import com.lease.saps.service.MessageLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 退款订单回调
 * @author CaiRui
 * @date 2026/4/12 14:40
 */
@Slf4j
@Service
public class RefundCallbackBizImpl implements RefundCallbackBiz {

    private static final DateTimeFormatter FINISH_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Autowired
    private RedissonLock redissonLock;

    @Autowired
    private MessageLogService messageLogService;

    @Autowired
    private CustomerOrderService customerOrderService;

    @Autowired
    private CustomerTransactionService customerTransactionService;

    @Autowired
    private CustomerOrderNotifyBiz customerOrderNotifyBiz;

    @Autowired
    private ThreadPoolTaskConfig threadPoolTaskConfig;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void consumeRefundResult(RefundApplyResp refundApplyResp) {
        if (refundApplyResp == null || StrUtil.isBlank(refundApplyResp.getOrderNo())) {
            ExcepitionUtil.throwSapsCommonError("退款结果订单号不能为空");
        }
        String orderNo = refundApplyResp.getOrderNo();
        String lockKey = RedisPrefixKeyEnum.REFUND_RESULT.getKey(orderNo);
        boolean locked = false;
        try {
            locked = redissonLock.tryLock(lockKey, 30L);
            if (!locked) {
                log.warn("退款结果通知获取分布式锁失败，orderNo={}", orderNo);
                //支付系统会有补偿通知 防止下面订单没有处理完成
                ExcepitionUtil.throwSapsCommonError("退款订单正在处理中，请稍后重试");
            }

            CustomerOrderEntity refundOrder = customerOrderService.getByOrderNo(orderNo);
            messageLogService.saveRefundResultLog(refundApplyResp, refundOrder);
            if (refundOrder == null) {
                ExcepitionUtil.throwSapsCommonError("退款订单不存在:" + orderNo);
            }
            //支持幂等
            PaymentStatusEnum currentStatus = refundOrder.getStatus();
            if (PaymentStatusEnum.SUCCESS.equals(currentStatus) || PaymentStatusEnum.FAILED.equals(currentStatus)) {
                log.info("退款结果重复通知，订单已处理完成，orderNo={}, currentStatus={}, callbackStatus={}",
                        orderNo, currentStatus, refundApplyResp.getStatus());
                return;
            }
            Integer callbackStatus = refundApplyResp.getStatus();
            if (!PaymentStatusEnum.SUCCESS.getValue().equals(callbackStatus)
                    && !PaymentStatusEnum.FAILED.getValue().equals(callbackStatus)) {
                log.info("退款结果通知为处理中，保持订单处理中，orderNo={}, status={}", orderNo, callbackStatus);
                return;
            }
            updateRefundOrder(refundOrder, refundApplyResp);
            updateRefundTransactions(refundOrder);

            if (PaymentStatusEnum.SUCCESS.equals(refundOrder.getStatus())) {
                customerOrderNotifyBiz.notifOrderNode(orderNo, DeviceFlowProcessProgressEnum.REFUND);
            }
            //第一时间通知合作方 (异步通知)
            triggerPartnerNotify(refundOrder);
        } finally {
            if (locked && redissonLock.isHoldByCurrentThread(lockKey)) {
                redissonLock.unlock(lockKey);
            }
        }
    }

    private void updateRefundOrder(CustomerOrderEntity refundOrder, RefundApplyResp refundApplyResp) {
        PaymentStatusEnum callbackStatus = PaymentStatusEnum.getByValue(refundApplyResp.getStatus());
        refundOrder.setStatus(callbackStatus);
        refundOrder.setFailMsg(PaymentStatusEnum.FAILED.equals(callbackStatus) ? refundApplyResp.getFailMsg() : null);
        refundOrder.setFinishTime(parseFinishTime(refundApplyResp.getFintime()));
        if (StrUtil.isNotBlank(refundApplyResp.getChannelSerialNo())) {
            refundOrder.setCserialNo(refundApplyResp.getChannelSerialNo());
        }
        if (StrUtil.isNotBlank(refundApplyResp.getPaySerialNo())) {
            refundOrder.setPaySerialNo(refundApplyResp.getPaySerialNo());
        }
        customerOrderService.update(refundOrder);
        log.info("退款结果通知处理完成，orderNo={}, status={}", refundOrder.getOrderNo(), callbackStatus);
    }

    private void updateRefundTransactions(CustomerOrderEntity refundOrder) {
        List<CustomerTransactionEntity> transactions = customerTransactionService.listByOrderNo(
                refundOrder.getTenantCode(), refundOrder.getOrderNo());
        if (CollectionUtils.isEmpty(transactions)) {
            ExcepitionUtil.throwSapsCommonError("退款交易不存在:" + refundOrder.getOrderNo());
        }
        for (CustomerTransactionEntity transaction : transactions) {
            transaction.setStatus(refundOrder.getStatus());
            transaction.setFailMsg(refundOrder.getFailMsg());
        }
        customerTransactionService.updateBatch(transactions);
    }

    private LocalDateTime parseFinishTime(String finTime) {
        if (StrUtil.isBlank(finTime)) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(finTime, FINISH_TIME_FORMATTER);
        } catch (Exception e) {
            log.warn("退款结果完成时间解析失败，finTime={}", finTime, e);
            return LocalDateTime.now();
        }
    }

    private void triggerPartnerNotify(CustomerOrderEntity refundOrder) {
        //事务提交通知合作方
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            // 在事务中，注册事务提交后的回调
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    ThreadPoolExecutor orderNotifyExecutor = threadPoolTaskConfig.getOrderNotifyExecutor();
                    orderNotifyExecutor.execute(() -> {
                        try {
                            Thread.sleep(3000);
                            customerOrderNotifyBiz.executeNotify(refundOrder);
                        }  catch (Exception e) {
                            log.error("notifyPartner error", e);
                        }
                    });
                }
            });
        }
    }

    private void submitNotifyTask(Runnable notifyTask) {
        ThreadPoolExecutor orderNotifyExecutor = threadPoolTaskConfig.getOrderNotifyExecutor();
        orderNotifyExecutor.execute(notifyTask);
    }
}
