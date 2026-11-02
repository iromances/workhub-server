package com.lease.saps.business.service.order.impl;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.lease.common.core.Result;
import com.lease.saps.api.GatewayApiService;
import com.lease.saps.api.dto.gateway.PayResultNotifyRequest;
import com.lease.saps.api.dto.gateway.PayResultNotifyResp;
import com.lease.saps.business.service.api.PaymentTransaction;
import com.lease.saps.business.service.order.CustomerOrderNotifyBiz;
import com.lease.saps.common.util.ThreadPoolTaskConfig;
import com.lease.saps.model.api.payment.PaymentResultNotifyRequest;
import com.lease.saps.model.api.payment.PaymentResultSettlementDetail;
import com.lease.saps.model.api.payment.PaymentResultShareDetail;
import com.lease.saps.model.api.payment.PaymentResultSubjectDetail;
import com.lease.saps.model.api.payment.transaction.TransactionResultQueryRequest;
import com.lease.saps.model.dto.replacebattery.DeviceFlowSyncReq;
import com.lease.saps.model.entity.CustomerBillEntity;
import com.lease.saps.model.entity.CustomerBillPayDetailEntity;
import com.lease.saps.model.entity.CustomerBillPaySubjectDetailEntity;
import com.lease.saps.model.entity.CustomerOrderAsinfoEntity;
import com.lease.saps.model.entity.CustomerOrderEntity;
import com.lease.saps.model.entity.CustomerOrderExtendEntity;
import com.lease.saps.model.enums.DeviceFlowProcessProgressEnum;
import com.lease.saps.model.enums.notify.NotifyStatusEnum;
import com.lease.saps.service.CustomerBillPayDetailService;
import com.lease.saps.service.CustomerBillPaySubjectDetailService;
import com.lease.saps.service.CustomerBillService;
import com.lease.saps.service.CustomerOrderAsinfoService;
import com.lease.saps.service.CustomerOrderExtendService;
import com.lease.saps.service.replacebattery.ReplaceBatteryDeviceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 客户订单交易结果通知
 *
 * @author CaiRui
 * @date 2025/12/5 13:19
 */
@Slf4j
@Service
public class CustomerOrderNotifyBizImpl implements CustomerOrderNotifyBiz {


    @Autowired
    private CustomerOrderAsinfoService customerOrderAsinfoService;
    @Autowired
    private CustomerBillPayDetailService customerBillPayDetailService;
    @Autowired
    private CustomerBillService customerBillService;
    @Autowired
    private CustomerBillPaySubjectDetailService customerBillPaySubjectDetailService;
    @Autowired
    private CustomerOrderExtendService customerOrderExtendService;
    @Autowired
    private ThreadPoolTaskConfig threadPoolTaskConfig;
    @Autowired
    private GatewayApiService gatewayApiService;
    @Autowired
    private ReplaceBatteryDeviceService replaceBatteryDeviceService;
    @Autowired
    private PaymentTransaction paymentTransaction;

    @Override
    public void executeNotify(CustomerOrderEntity customerOrder) {
        // 检查订单是否为空或没有通知URL
        if (customerOrder == null) {
            log.warn("订单为空，跳过交易结果通知");
            return;
        }
        // 异步发送通知
       // ThreadPoolExecutor executor = threadPoolTaskConfig.getOrderNotifyExecutor();
       // executor.execute(() -> {
            try {
                sendNotification(customerOrder);
            } catch (Exception e) {
                log.error("{}订单交易结果通知发送异常", customerOrder.getOrderNo(), e);
            }
       // });
    }

    @Override
    public void notifOrderNode(String orderNo, DeviceFlowProcessProgressEnum flowProcessProgressEnum) {
        // 检查是否在事务上下文中
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            // 在事务中，注册事务提交后的回调
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        DeviceFlowSyncReq deviceFlowSyncReq = new DeviceFlowSyncReq();
                        deviceFlowSyncReq.setOrderNo(orderNo);
                        deviceFlowSyncReq.setPayApplyFlowStatus(flowProcessProgressEnum);
                        replaceBatteryDeviceService.deviceFlowSync(deviceFlowSyncReq);
                    } catch (Exception e) {
                        log.error("{}订单节点通知异常:{}", orderNo, e);
                    }
                }
            });
        } else {
            // 不在事务中，直接执行
            log.warn("当前不在事务上下文中，直接执行订单节点通知，orderNo={}", orderNo);
            try {
                DeviceFlowSyncReq deviceFlowSyncReq = new DeviceFlowSyncReq();
                deviceFlowSyncReq.setOrderNo(orderNo);
                deviceFlowSyncReq.setPayApplyFlowStatus(flowProcessProgressEnum);
                replaceBatteryDeviceService.deviceFlowSync(deviceFlowSyncReq);
            } catch (Exception e) {
                log.error("{}订单节点通知异常:{}", orderNo, e);
            }
        }
    }

    /**
     * 发送交易结果通知（通过网关转发）
     */
    private void sendNotification(CustomerOrderEntity customerOrder) {
        log.info("{}开始发送订单交易结果通知", customerOrder.getOrderNo());
        // 初始化或查询订单扩展信息
        CustomerOrderExtendEntity extendEntity = customerOrderExtendService.getByOrderNo(customerOrder.getOrderNo());
        if (extendEntity == null) {
            log.warn("订单{}扩展信息不存在，无法发送交易结果通知", customerOrder.getOrderNo());
            return;
        }
        // 如果通知失败次数超过5次，不再发送通知
        if (extendEntity.getNotifyFailCount() != null && extendEntity.getNotifyFailCount() >= 5) {
            log.warn("订单{}交易结果通知失败次数已达{}次，超过最大重试次数5次，不再发送通知", 
                    customerOrder.getOrderNo(), extendEntity.getNotifyFailCount());
            return;
        }
        try {
            // 组装通知请求参数
            TransactionResultQueryRequest queryRequest = new TransactionResultQueryRequest();
            queryRequest.setOrderNo(customerOrder.getOrderNo());
            queryRequest.setPaySn(customerOrder.getBizNo());
            PaymentResultNotifyRequest notifyParams = paymentTransaction.queryTransactionResult(queryRequest);
           //PaymentResultNotifyRequest notifyParams = buildNotifyRequest(customerOrder);
            log.info("订单{}交易结果通知请求：{}", customerOrder.getOrderNo(), JSON.toJSONString(notifyParams));

            // 构建网关转发请求
            PayResultNotifyRequest<PaymentResultNotifyRequest> gatewayRequest = new PayResultNotifyRequest<>();
            gatewayRequest.setNotifyUrl(extendEntity.getNotifyUrl());
            gatewayRequest.setNotifyParams(notifyParams);
            gatewayRequest.setNotifyBusiness("PAYMENT_RESULT_NOTIFY"); // 交易结果通知
            gatewayRequest.setProjectCode(customerOrder.getProjectNo());
            gatewayRequest.setNotifyChannel(extendEntity.getOrgCode());
            // 可选：如果需要压缩参数
            // gatewayRequest.setCompressParams(true);
            // 通过网关转发通知
            Result<PayResultNotifyResp> result = gatewayApiService.notfiy(gatewayRequest);

            if (result != null && result.isSuccess() && result.getData() != null) {
                PayResultNotifyResp response = result.getData();
                if (response.getSyncStatus() == 1) {
                    log.info("订单{}交易结果通知发送成功", customerOrder.getOrderNo());
                    // 更新通知成功状态
                    extendEntity.setNotifyStatus(NotifyStatusEnum.SUCCESS);
                    extendEntity.setNotifyTime(LocalDateTime.now());
                    customerOrderExtendService.updateById(extendEntity);
                } else {
                    log.warn("订单{}交易结果通知发送失败，响应状态：{}，message={}",
                            customerOrder.getOrderNo(), response.getSyncStatus(), response.getMessage());
                    extendEntity.setNotifyStatus(NotifyStatusEnum.FAILED);
                    extendEntity.setNotifyFailCount(extendEntity.getNotifyFailCount() + 1);
                    extendEntity.setNotifyTime(LocalDateTime.now());
                    customerOrderExtendService.updateById(extendEntity);
                }
            } else {
                String failMsg = result != null ? result.getMessage() : "网关响应为空";
                log.warn("订单{}交易结果通知响应异常：{}", customerOrder.getOrderNo(), failMsg);
                extendEntity.setNotifyStatus(NotifyStatusEnum.FAILED);
                extendEntity.setNotifyTime(LocalDateTime.now());
                extendEntity.setNotifyFailCount(extendEntity.getNotifyFailCount() + 1);
                customerOrderExtendService.updateById(extendEntity);
            }
            log.info("订单{}交易结果通知响应：{}", customerOrder.getOrderNo(), JSON.toJSONString(result));
        } catch (Exception e) {
            log.error("订单{}交易结果通知发送异常", customerOrder.getOrderNo(), e);
            extendEntity.setNotifyStatus(NotifyStatusEnum.FAILED);
            extendEntity.setNotifyTime(LocalDateTime.now());
            extendEntity.setNotifyFailCount(extendEntity.getNotifyFailCount() + 1);
            customerOrderExtendService.updateById(extendEntity);
        }
    }

    /**
     * 构建交易结果通知请求参数（公共方法）
     *
     * @param customerOrder 客户订单
     * @return 交易结果通知请求
     */
    @Override
    public PaymentResultNotifyRequest buildNotifyRequest(CustomerOrderEntity customerOrder) {
        PaymentResultNotifyRequest request = new PaymentResultNotifyRequest();

        // 基本信息
        request.setPaySn(customerOrder.getBizNo());
        request.setOrderNo(customerOrder.getOrderNo());
        request.setChannelSn(customerOrder.getCserialNo());
        request.setSubType(customerOrder.getSubType() != null ? customerOrder.getSubType().getValue() : null);
        request.setStatus(customerOrder.getStatus() != null ? customerOrder.getStatus().getValue() : null);
        request.setFailMsg(customerOrder.getFailMsg());
        request.setTransAmount(customerOrder.getTransactionAmount());
        request.setFee(customerOrder.getPlatformFee() != null ? customerOrder.getPlatformFee() : customerOrder.getChannelFee());
        request.setFinishTime(customerOrder.getFinishTime());
        request.setChannel(customerOrder.getChannel() != null ? customerOrder.getChannel().getValue() : null);

        // 查询分账信息
        List<CustomerOrderAsinfoEntity> asinfoList = customerOrderAsinfoService.listByOrderNo(customerOrder.getOrderNo());
        if (CollectionUtils.isNotEmpty(asinfoList)) {
            List<PaymentResultShareDetail> shareDetails = new ArrayList<>();
            for (CustomerOrderAsinfoEntity asinfo : asinfoList) {
                PaymentResultShareDetail shareDetail = new PaymentResultShareDetail();
                // 使用payeeCode作为用户标识
                shareDetail.setBizUserid(asinfo.getPayeeCode());
                shareDetail.setAstype(asinfo.getAstype());
                shareDetail.setAsinfoSn(asinfo.getChannelSn());
                shareDetail.setAmount(asinfo.getAmount());
                shareDetail.setArriveAmount(asinfo.getArriveAmount());
                shareDetail.setFee(asinfo.getFee());
                shareDetail.setStatus(asinfo.getPaymentStatus().getValue());
                shareDetail.setRemark(asinfo.getRemark());
                shareDetails.add(shareDetail);
            }
            request.setAsinfoList(shareDetails);
        }
        // 查询结算明细（如果有的话）
        // 使用LambdaQueryWrapper查询账单支付明细
        List<CustomerBillPayDetailEntity> payDetailList = customerBillPayDetailService.list(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CustomerBillPayDetailEntity>()
                        .eq(CustomerBillPayDetailEntity::getOrderNo, customerOrder.getOrderNo())
        );
        if (CollectionUtils.isNotEmpty(payDetailList)) {
            List<PaymentResultSettlementDetail> settlementDetails = new ArrayList<>();
            for (CustomerBillPayDetailEntity payDetail : payDetailList) {
                // 查询账单信息获取applicationNo和period
                CustomerBillEntity bill = customerBillService.getByBillNo(payDetail.getBillNo());
                if (bill != null) {
                    PaymentResultSettlementDetail settlementDetail = new PaymentResultSettlementDetail();
                    settlementDetail.setApplicationNo(bill.getApplicationNo());
                    settlementDetail.setPeriod(bill.getPeriod());
                    settlementDetail.setBillNo(payDetail.getBillNo());
                    settlementDetail.setPayAmount(payDetail.getPayAmount());

                    // 查询支付科目明细
                    List<CustomerBillPaySubjectDetailEntity> paySubjectList = customerBillPaySubjectDetailService.list(
                            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CustomerBillPaySubjectDetailEntity>()
                                    .eq(CustomerBillPaySubjectDetailEntity::getPayDetailSerialNo, payDetail.getSerialNo())
                    );
                    if (CollectionUtils.isNotEmpty(paySubjectList)) {
                        List<PaymentResultSubjectDetail> subjectDetails = new ArrayList<>();
                        for (CustomerBillPaySubjectDetailEntity paySubject : paySubjectList) {
                            PaymentResultSubjectDetail subjectDetail = new PaymentResultSubjectDetail();
                            subjectDetail.setSubjectNo(paySubject.getSubjectNo());
                            subjectDetail.setSubjectName(paySubject.getSubjectName());
                            subjectDetail.setPayAmount(paySubject.getPaidAmount());
                            subjectDetails.add(subjectDetail);
                        }
                        settlementDetail.setPaySubjectList(subjectDetails);
                    }

                    settlementDetails.add(settlementDetail);
                }
            }
            request.setPayDetailList(settlementDetails);
        }
        return request;
    }


}

