package com.lease.saps.business.service.refund.impl;

import com.google.common.collect.Lists;
import com.lease.common.core.Result;
import com.lease.common.core.ResultCode;
import com.lease.saps.business.service.refund.RefundOrderSaveBiz;
import com.lease.saps.common.exception.SapsException;
import com.lease.saps.common.exception.SapsExceptionEnum;
import com.lease.saps.model.api.payment.RefundApplyReq;
import com.lease.saps.model.api.payment.RefundFeeDetailReq;
import com.lease.saps.model.entity.CustomerOrderDetailEntity;
import com.lease.saps.model.entity.CustomerOrderEntity;
import com.lease.saps.model.entity.CustomerOrderExtendEntity;
import com.lease.saps.model.entity.CustomerTransactionEntity;
import com.lease.saps.model.enums.ChannelEnum;
import com.lease.saps.model.enums.PaymentStatusEnum;
import com.lease.saps.model.enums.TransactionModeEnum;
import com.lease.saps.model.enums.TransactionSubTypeEnum;
import com.lease.saps.model.enums.TransactionTypeEnum;
import com.lease.saps.model.enums.notify.NotifyStatusEnum;
import com.lease.saps.service.CustomerOrderDetailService;
import com.lease.saps.service.CustomerOrderExtendService;
import com.lease.saps.service.CustomerOrderService;
import com.lease.saps.service.CustomerTransactionService;
import com.lease.saps.service.SequenceService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 退款订单保存。
 *
 * @author CaiRui
 */
@Slf4j
@Service
public class RefundOrderSaveBizImpl implements RefundOrderSaveBiz {

    @Autowired
    private CustomerOrderService customerOrderService;

    @Autowired
    private CustomerTransactionService customerTransactionService;

    @Autowired
    private CustomerOrderDetailService customerOrderDetailService;

    @Autowired
    private CustomerOrderExtendService customerOrderExtendService;

    @Autowired
    private SequenceService sequenceService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<CustomerOrderEntity> saveRefundOrderAndTrans(RefundApplyReq refundApplyReq, CustomerOrderEntity originOrder) {
        Result<CustomerOrderEntity> result = new Result<>(ResultCode.SUCCESS1);
        result = checkParams(refundApplyReq, originOrder, result);
        if (!result.isSuccess()) {
            return result;
        }

        CustomerOrderEntity refundOrder = saveCustomerOrder(refundApplyReq, originOrder);
        saveCustomerTransAction(refundOrder, refundApplyReq);
        saveCustomerOrderExtend(refundOrder.getOrderNo(), refundApplyReq);
        result.setData(refundOrder);
        return result;
    }

    private void saveCustomerOrderExtend(String orderNo, RefundApplyReq refundApplyReq) {
        CustomerOrderExtendEntity customerOrderExtend = new CustomerOrderExtendEntity();
        customerOrderExtend.setOrderNo(orderNo);
        customerOrderExtend.setOrgCode(refundApplyReq.getOrgCode());
        customerOrderExtend.setNotifyUrl(refundApplyReq.getNotifyUrl());
        customerOrderExtend.setNotifyStatus(NotifyStatusEnum.INIT);
        customerOrderExtend.setNotifyFailCount(0);
        customerOrderExtendService.save(customerOrderExtend);
    }

    private CustomerTransactionEntity saveCustomerTransAction(CustomerOrderEntity refundOrder, RefundApplyReq refundApplyReq) {
        CustomerTransactionEntity transaction = new CustomerTransactionEntity();
        transaction.setOrderNo(refundOrder.getOrderNo());
        transaction.setTenantCode(refundOrder.getTenantCode());
        transaction.setSerialNo(sequenceService.nextSecond6SerialNo("CT"));
        transaction.setGoodsNo(null);
        transaction.setTransactionName(null);
        transaction.setTransactionAmount(refundOrder.getTransactionAmount());
        transaction.setPayAmount(refundOrder.getPayAmount());
        transaction.setRechargeAmount(BigDecimal.ZERO);
        transaction.setSettleAmount(BigDecimal.ZERO);
        transaction.setStatus(PaymentStatusEnum.PROCESSING);
        customerTransactionService.save(transaction);

        List<CustomerOrderDetailEntity> customerOrderDetailLists = Lists.newArrayList();
        List<RefundFeeDetailReq> feeDetails = refundApplyReq.getFeeDetail();
        for (RefundFeeDetailReq refundFeeDetailReq : feeDetails) {
            CustomerOrderDetailEntity customerOrderDetailEntity = new CustomerOrderDetailEntity();
            customerOrderDetailEntity.setOrderNo(refundOrder.getOrderNo());
            customerOrderDetailEntity.setTransNo(transaction.getSerialNo());
            customerOrderDetailEntity.setSubjectNo(refundFeeDetailReq.getSubjectNo());
            customerOrderDetailEntity.setAmount(refundFeeDetailReq.getAmount());
            customerOrderDetailLists.add(customerOrderDetailEntity);
        }
        customerOrderDetailService.saveBatch(customerOrderDetailLists);
        return transaction;
    }

    private CustomerOrderEntity saveCustomerOrder(RefundApplyReq refundApplyReq, CustomerOrderEntity originOrder) {
        String refundOrderNo = sequenceService.nextSecond6SerialNo("CO");
        CustomerOrderEntity refundOrder = new CustomerOrderEntity();

        refundOrder.setOrderNo(refundOrderNo);
        refundOrder.setTenantCode(originOrder.getTenantCode());
        refundOrder.setBizNo(refundApplyReq.getPaySn());
        refundOrder.setSourceOrderNo(originOrder.getOrderNo());
        refundOrder.setProjectNo(originOrder.getProjectNo());
        refundOrder.setCustomerCode(originOrder.getCustomerCode());
        refundOrder.setCustomerName(originOrder.getCustomerName());
        refundOrder.setTransactionType(TransactionTypeEnum.REFUND);
        refundOrder.setSubType(TransactionSubTypeEnum.REFUND_NORMAL);
        refundOrder.setTransactionMode(TransactionModeEnum.ONLINE);
        refundOrder.setSubMode(originOrder.getSubMode());
        refundOrder.setChannel(ChannelEnum.ALLINPAY);

        refundOrder.setPayAmount(refundApplyReq.getRefundAmt());
        refundOrder.setTransactionAmount(refundApplyReq.getRefundAmt());
        refundOrder.setOrderDesc(refundApplyReq.getReason());
        refundOrder.setDiscountAmount(BigDecimal.ZERO);
        refundOrder.setRechargeAmount(BigDecimal.ZERO);
        refundOrder.setSettleAmount(BigDecimal.ZERO);
        refundOrder.setPlatformFee(BigDecimal.ZERO);
        refundOrder.setChannelFee(BigDecimal.ZERO);
        refundOrder.setAdvanceFee(BigDecimal.ZERO);
        refundOrder.setEarnFee(BigDecimal.ZERO);
        refundOrder.setStatus(PaymentStatusEnum.PROCESSING);

        refundOrder.setOrderTime(LocalDateTime.now());
        refundOrder.setIsPassive(false);
        refundOrder.setPriority(2);
        customerOrderService.save(refundOrder);
        return refundOrder;
    }

    private Result<CustomerOrderEntity> checkParams(RefundApplyReq refundApplyReq, CustomerOrderEntity originOrder, Result<CustomerOrderEntity> result) {
        List<RefundFeeDetailReq> feeDetails = refundApplyReq.getFeeDetail();

        if (CollectionUtils.isEmpty(feeDetails)
                || refundApplyReq.getRefundAmt() == null
                || refundApplyReq.getSubType() == null
                || StringUtils.isEmpty(refundApplyReq.getPaySn())
                || StringUtils.isEmpty(refundApplyReq.getOriginOrderNo())) {
            throw new SapsException(SapsExceptionEnum.COMMON_ERROR, "必传参数为空");
        }

        BigDecimal feeDetailAmount = BigDecimal.ZERO;
        for (RefundFeeDetailReq feeDetailReq : feeDetails) {
            String subjectNo = feeDetailReq.getSubjectNo();
            BigDecimal currentRefund = feeDetailReq.getAmount();
            if (StringUtils.isEmpty(subjectNo) || currentRefund == null) {
                throw new SapsException(SapsExceptionEnum.COMMON_ERROR, "必传参数为空");
            }
            feeDetailAmount = feeDetailAmount.add(currentRefund);
        }

        //校验本次退款明细合计与退款主单金额一致，避免主单与明细口径不一致导致下游状态/金额误判
        if (feeDetailAmount.compareTo(refundApplyReq.getRefundAmt()) != 0) {
            throw new SapsException(SapsExceptionEnum.COMMON_ERROR, "退款金额与明细金额不等");
        }

        if (!TransactionSubTypeEnum.REFUND_NORMAL.getValue().equals(refundApplyReq.getSubType())) {
            throw new SapsException(SapsExceptionEnum.COMMON_ERROR, "交易类型只支持退款");
        }

        CustomerOrderEntity originPayOrder = customerOrderService.getByBizNo(refundApplyReq.getPaySn());
        if (originPayOrder != null) {
            throw new SapsException(SapsExceptionEnum.COMMON_ERROR, "申请退款流水已存在");
        }
        if (originOrder == null) {
            throw new SapsException(SapsExceptionEnum.COMMON_ERROR, "支付单不存在：" + refundApplyReq.getOriginOrderNo());
        }
        if (!PaymentStatusEnum.SUCCESS.equals(originOrder.getStatus())) {
            throw new SapsException(SapsExceptionEnum.COMMON_ERROR, "当前支付订单支付未成功,暂不支持退款");
        }
        if (!PaymentStatusEnum.SUCCESS.equals(originOrder.getShareStatus())) {
            throw new SapsException(SapsExceptionEnum.COMMON_ERROR, "当前支付订单未分账成功,暂不允许退款");
        }

        List<CustomerOrderDetailEntity> originDetailList = customerOrderDetailService.listByOrderNo(originOrder.getOrderNo());
        if (CollectionUtils.isEmpty(originDetailList)) {
            throw new SapsException(SapsExceptionEnum.COMMON_ERROR, "原订单明细金额为空");
        }

        List<PaymentStatusEnum> payStatus = Lists.newArrayList();
        payStatus.add(PaymentStatusEnum.PROCESSING);
        payStatus.add(PaymentStatusEnum.SUCCESS);
        List<CustomerOrderEntity> refundOrderList = customerOrderService.queryBySourceNoAndType(
                refundApplyReq.getOriginOrderNo(), TransactionSubTypeEnum.REFUND_NORMAL.getValue(), payStatus);

        BigDecimal originRefundAmount = refundOrderList.stream()
                .map(CustomerOrderEntity::getTransactionAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (originOrder.getTransactionAmount().compareTo(originRefundAmount) <= 0) {
            saveFailCustomerOrderAndTransAction(refundApplyReq, originOrder, "已全部退款");
            result.setCode(ResultCode.FAIL.getCode());
            result.setMessage("已全部退款");
            return result;
        }

        BigDecimal refundedTotalAmount = originRefundAmount.add(refundApplyReq.getRefundAmt());
        if (refundedTotalAmount.compareTo(originOrder.getTransactionAmount()) > 0) {
            saveFailCustomerOrderAndTransAction(refundApplyReq, originOrder, "已超额退款");
            result.setCode(ResultCode.FAIL.getCode());
            result.setMessage("已超额退款");
            return result;
        }
        return result;
    }

    private void saveFailCustomerOrderAndTransAction(RefundApplyReq refundApplyReq, CustomerOrderEntity originOrder, String errmsg) {
        String refundOrderNo = sequenceService.nextSecond6SerialNo("CO");
        CustomerOrderEntity refundOrder = new CustomerOrderEntity();

        refundOrder.setOrderNo(refundOrderNo);
        refundOrder.setTenantCode(originOrder.getTenantCode());
        refundOrder.setBizNo(refundApplyReq.getPaySn());
        refundOrder.setSourceOrderNo(originOrder.getOrderNo());
        refundOrder.setCustomerCode(originOrder.getCustomerCode());
        refundOrder.setCustomerName(originOrder.getCustomerName());
        refundOrder.setTransactionType(TransactionTypeEnum.REFUND);
        refundOrder.setSubType(TransactionSubTypeEnum.REFUND_NORMAL);
        refundOrder.setTransactionMode(TransactionModeEnum.ONLINE);
        refundOrder.setSubMode(originOrder.getSubMode());
        refundOrder.setChannel(ChannelEnum.ALLINPAY);

        refundOrder.setPayAmount(refundApplyReq.getRefundAmt());
        refundOrder.setTransactionAmount(refundApplyReq.getRefundAmt());
        refundOrder.setOrderDesc(refundApplyReq.getReason());
        refundOrder.setDiscountAmount(BigDecimal.ZERO);
        refundOrder.setRechargeAmount(BigDecimal.ZERO);
        refundOrder.setSettleAmount(BigDecimal.ZERO);
        refundOrder.setPlatformFee(BigDecimal.ZERO);
        refundOrder.setChannelFee(BigDecimal.ZERO);
        refundOrder.setAdvanceFee(BigDecimal.ZERO);
        refundOrder.setEarnFee(BigDecimal.ZERO);
        refundOrder.setStatus(PaymentStatusEnum.FAILED);
        refundOrder.setFailMsg(errmsg);
        refundOrder.setOrderTime(LocalDateTime.now());
        refundOrder.setIsPassive(false);
        refundOrder.setPriority(2);
        customerOrderService.save(refundOrder);

        CustomerTransactionEntity transaction = new CustomerTransactionEntity();
        transaction.setOrderNo(refundOrder.getOrderNo());
        transaction.setTenantCode(refundOrder.getTenantCode());
        transaction.setSerialNo(sequenceService.nextSecond6SerialNo("CT"));
        transaction.setGoodsNo(null);
        transaction.setTransactionName(null);
        transaction.setTransactionAmount(refundOrder.getTransactionAmount());
        transaction.setPayAmount(refundOrder.getPayAmount());
        transaction.setRechargeAmount(BigDecimal.ZERO);
        transaction.setSettleAmount(BigDecimal.ZERO);
        transaction.setStatus(PaymentStatusEnum.FAILED);
        transaction.setFailMsg(errmsg);
        customerTransactionService.save(transaction);

        List<CustomerOrderDetailEntity> customerOrderDetailLists = Lists.newArrayList();
        List<RefundFeeDetailReq> feeDetails = refundApplyReq.getFeeDetail();
        for (RefundFeeDetailReq refundFeeDetailReq : feeDetails) {
            CustomerOrderDetailEntity customerOrderDetailEntity = new CustomerOrderDetailEntity();
            customerOrderDetailEntity.setOrderNo(refundOrder.getOrderNo());
            customerOrderDetailEntity.setTransNo(transaction.getSerialNo());
            customerOrderDetailEntity.setSubjectNo(refundFeeDetailReq.getSubjectNo());
            customerOrderDetailEntity.setAmount(refundFeeDetailReq.getAmount());
            customerOrderDetailLists.add(customerOrderDetailEntity);
        }
        customerOrderDetailService.saveBatch(customerOrderDetailLists);
    }
}
