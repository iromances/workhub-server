package com.lease.saps.business.service.api.impl;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.lease.common.core.Result;
import com.lease.common.core.ResultCode;
import com.lease.redission.RedissonLock;
import com.lease.saps.api.PaymentApiService;
import com.lease.saps.business.service.api.RefundApplyBiz;
import com.lease.saps.business.service.order.CustomerOrderNotifyBiz;
import com.lease.saps.business.service.refund.RefundCallbackBiz;
import com.lease.saps.business.service.refund.RefundOrderSaveBiz;
import com.lease.saps.common.exception.ExcepitionUtil;
import com.lease.saps.common.result.XxtPayResultHelper;
import com.lease.saps.common.util.ServletUtils;
import com.lease.saps.common.util.ThreadPoolTaskConfig;
import com.lease.saps.model.api.payment.*;
import com.lease.saps.model.entity.CustomerOrderEntity;
import com.lease.saps.model.enums.*;
import com.lease.saps.model.profile.busiconfig.XxtPayGroup;
import com.lease.saps.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 支付结算申请实现 (优化版)
 * 核心优化点：
 * 1. 移除冗余的PaymentContext，直接使用业务数据对象
 * 2. 使用Redisson分布式锁替代Redis原生锁，支持自动续期和可重入
 * 3. 分布式锁在事务外层，避免锁超时
 * 4. 减少重复查询，优化批量操作
 * <p>
 * 流程：参数验证 -> 分布式锁控制 -> 业务验证 -> 扩展验证 -> 分账组装 -> 订单交易组装 -> 支付请求
 *
 * @author CaiRui
 * @date 2025/11/28 18:49
 */
@Slf4j
@Service
public class RefundApplyBizImpl implements RefundApplyBiz {

    private static final DateTimeFormatter FINISH_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Autowired
    private PaymentApiService paymentApiService;

    @Autowired
    private RedissonLock redissonLock;

    @Autowired
    private CustomerOrderService customerOrderService;

    @Autowired
    private WechatMessageService wechatMessageService;

    @Autowired
    private CustomerOrderNotifyBiz customerOrderNotifyBiz;

    @Autowired
    private RefundCallbackBiz refundCallbackBiz;

    @Autowired
    private RefundOrderSaveBiz refundOrderSaveBiz;

    @Autowired
    private ThreadPoolTaskConfig threadPoolTaskConfig;
    @Autowired
    private BusinessConfigService businessConfigService;

    @Override
    @SuppressWarnings("all")
    public Result<RefundApplyVo> refundApply(RefundApplyReq refundApplyReq) {

        String originOrderNo = refundApplyReq.getOriginOrderNo();
        if (StrUtil.isBlankOrUndefined(originOrderNo)) {
            ExcepitionUtil.throwSapsCommonError("必传参数为空");
        }
        String lockKey = RedisPrefixKeyEnum.REFUND_APPLY.getKey(originOrderNo);
        Long lockTimeout = 30L;
        boolean locked = false;
        try {
            locked = redissonLock.tryLock(lockKey, lockTimeout);
            if (!locked) {
                log.warn("获取分布式锁失败，可能存在并发请求，paySn={}", originOrderNo);
                ExcepitionUtil.throwSapsCommonError("当前流水正在申请退款,请勿重复退款");
            }
            log.info("获取退款锁成功，paySn={}, lockKey={}, timeout={}",
                    originOrderNo, lockKey, lockTimeout);
            refundApplyReq.setOrgCode(ServletUtils.getCooperationCode());
           return processApply(refundApplyReq);
        } finally {
            // Redisson自动释放锁（支持可重入和自动续期）
            if (locked && redissonLock.isHoldByCurrentThread(lockKey)) {
                redissonLock.unlock(lockKey);
                log.debug("释放分布式锁，paySn={}", originOrderNo);
            }
        }

    }



    private Result<RefundApplyVo> processApply(RefundApplyReq refundApplyReq) {

        log.info("executeCustomerRefund start, paySn={}", refundApplyReq.getPaySn());
        //支付订单
        CustomerOrderEntity originOrder =customerOrderService.getByOrderNo(refundApplyReq.getOriginOrderNo());

        Result<CustomerOrderEntity> persistResult = refundOrderSaveBiz.saveRefundOrderAndTrans(refundApplyReq, originOrder);
        if (!persistResult.isSuccess()) {
            Result<RefundApplyVo> failResult = new Result<>();
            failResult.setCode(persistResult.getCode());
            failResult.setMessage(persistResult.getMessage());
            return failResult;
        }
        CustomerOrderEntity refundOrder = persistResult.getData();
        //发起退款申请申请
        try {
            //refundApplyReq.setOrderNo(refundOrder.getOrderNo());
            //refundApplyReq.setSourceOrderNo(originOrder.getOrderNo());

            XxtPayGroup xxtPayGroup = businessConfigService.getConfig(XxtPayGroup.class, refundOrder.getProjectNo());
            RefundApplyApiReq applyApiReq = new RefundApplyApiReq();
            applyApiReq.setOrderNo(refundOrder.getOrderNo());
            applyApiReq.setSourceOrderNo(originOrder.getOrderNo());
            applyApiReq.setRefundAmt(refundApplyReq.getRefundAmt());
            applyApiReq.setReason(refundApplyReq.getReason());
            applyApiReq.setAppId(xxtPayGroup.getAppId());
            applyApiReq.setRefundStrategy(2); //转账再退款
            applyApiReq.setNotifyUrl(xxtPayGroup.getRefundCallBackUrl());
           // Result<RefundApplyResp> refundApplyResult = paymentApiService.refundApply(refundApplyReq);
            Result<RefundApplyResp> refundApplyResult = paymentApiService.refundApply(applyApiReq);
            log.info("退款申请发起结果：{}", JSON.toJSONString(refundApplyResult));
            // 支付系统响应后，复用退款结果通知消费逻辑处理状态流转
            handlePaymentResult(refundApplyResult, refundOrder);
        } catch (Exception e) {
            log.error("{}退款申请发起异常", refundApplyReq.getPaySn(), e);
            wechatMessageService.sendMsgDEV(refundApplyReq.getPaySn() + "发送支付系统退款异常请处理");
        }
        RefundApplyVo refundApplyVo = new RefundApplyVo();
        refundApplyVo.setOrderNo(refundOrder.getOrderNo());
        refundApplyVo.setPaySn(refundApplyReq.getPaySn());
        Result<RefundApplyVo> result = new Result<>(ResultCode.SUCCESS1);
        result.setData(refundApplyVo);
        return result;
    }

    @SuppressWarnings("all")
    private void handlePaymentResult(Result<RefundApplyResp> result, CustomerOrderEntity refundOrder) {
        // 1. result 为 null
        if (result == null) {
            log.info("支付系统无响应（result为null），退款单保持处理中，orderNo={}", refundOrder.getOrderNo());
            return;
        }
        //查询最新状态
        CustomerOrderEntity byOrderNo = customerOrderService.getByOrderNo(refundOrder.getOrderNo());
        if(PaymentStatusEnum.SUCCESS.equals(byOrderNo.getStatus())||PaymentStatusEnum.FAILED.equals(byOrderNo.getStatus())){
            log.info("退款单保已处理，orderNo={},{}", refundOrder.getOrderNo(),byOrderNo.getStatus());
            return;
        }

        if (XxtPayResultHelper.isSuccess(result)) {
            RefundApplyResp refundApplyResp = result.getData();
            if (isTerminalRefundResult(refundApplyResp)) {
                fillRefundApplyResp(refundApplyResp, refundOrder);
                log.info("支付系统响应包含明确退款结果，模拟退款结果通知消费，orderNo={}, code={}, status={}",
                        refundApplyResp.getOrderNo(), result.getCode(), refundApplyResp.getStatus());
                refundCallbackBiz.consumeRefundResult(refundApplyResp);
                return;
            }
            log.info("支付系统请求成功但退款结果未明确，退款单保持处理中，orderNo={}, code={}, status={}",
                    refundOrder.getOrderNo(), result.getCode(), refundApplyResp == null ? null : refundApplyResp.getStatus());
            return;
        }

        if (XxtPayResultHelper.isProcessing(result)) {
            log.info("支付系统响应结果未知，退款单保持处理中，orderNo={}, code={}, msg={}",
                    refundOrder.getOrderNo(), result.getCode(), result.getMessage());
            return;
        }

        if (XxtPayResultHelper.isFailure(result)){
            log.info("支付系统响应失败，退款单保持处理中，orderNo={}, code={}, msg={}",
                    refundOrder.getOrderNo(), result.getCode(), result.getMessage());
            RefundApplyResp failRefundApplyResp = new RefundApplyResp();
            failRefundApplyResp.setOrderNo(refundOrder.getOrderNo());
            failRefundApplyResp.setStatus(PaymentStatusEnum.FAILED.getValue());
            failRefundApplyResp.setFailMsg(StrUtil.isNotBlank(result.getMessage()) ? result.getMessage() : "支付系统退款申请失败");
            failRefundApplyResp.setFintime(LocalDateTime.now().format(FINISH_TIME_FORMATTER));
            log.info("支付系统返回明确失败，模拟退款结果通知消费，orderNo={}, code={}, msg={}",
                    refundOrder.getOrderNo(), result.getCode(), result.getMessage());
            refundCallbackBiz.consumeRefundResult(failRefundApplyResp);
        }

    }

    private boolean isTerminalRefundResult(RefundApplyResp refundApplyResp) {
        if (refundApplyResp == null || refundApplyResp.getStatus() == null) {
            return false;
        }
        return PaymentStatusEnum.SUCCESS.getValue().equals(refundApplyResp.getStatus())
                || PaymentStatusEnum.FAILED.getValue().equals(refundApplyResp.getStatus());
    }

    private void fillRefundApplyResp(RefundApplyResp refundApplyResp, CustomerOrderEntity refundOrder) {
        if (StrUtil.isBlank(refundApplyResp.getOrderNo())) {
            refundApplyResp.setOrderNo(refundOrder.getOrderNo());
        }
        if (PaymentStatusEnum.FAILED.getValue().equals(refundApplyResp.getStatus())
                && StrUtil.isBlank(refundApplyResp.getFailMsg())) {
            refundApplyResp.setFailMsg("支付系统退款申请失败");
        }
        if (StrUtil.isBlank(refundApplyResp.getFintime())) {
            refundApplyResp.setFintime(LocalDateTime.now().format(FINISH_TIME_FORMATTER));
        }
    }

    private void notifyPartner(CustomerOrderEntity refundOrder) {
        try {
            log.error("refund notifyPartner start:{}",refundOrder.getOrderNo());
            customerOrderNotifyBiz.executeNotify(refundOrder);
            log.error("refund notifyPartner end:{}",refundOrder.getOrderNo());
        }catch (Exception e){
            log.error("refund notifyPartner error:{},{}",refundOrder.getOrderNo(),e);
        }

    }

    @Override
    public void refundsync(RefundApplyReq refundApplyReq) {
        //查询退款单
        CustomerOrderEntity refundOrder = customerOrderService.getByOrderNo(refundApplyReq.getOrderNo());
        PaymentStatusEnum status = refundOrder.getStatus();
        if(PaymentStatusEnum.SUCCESS.equals(status)||PaymentStatusEnum.FAILED.equals(status)){
            ThreadPoolExecutor orderNotifyExecutor = threadPoolTaskConfig.getOrderNotifyExecutor();
            orderNotifyExecutor.execute(() -> {
                try {
                    Thread.sleep(1000);
                    notifyPartner(refundOrder);
                }  catch (Exception e) {
                    log.error("notifyPartner error", e);
                }
            });
        }
    }
}
