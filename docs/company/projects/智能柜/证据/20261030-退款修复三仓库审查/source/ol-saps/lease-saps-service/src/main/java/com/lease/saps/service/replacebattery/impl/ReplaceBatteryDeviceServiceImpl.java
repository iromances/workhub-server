package com.lease.saps.service.replacebattery.impl;

import java.util.Date;

import java.math.BigDecimal;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.google.common.collect.Lists;
import com.lease.redission.RedissonLock;
import com.lease.saps.common.constants.ProjectConstants;
import com.lease.saps.common.util.ThreadPoolTaskConfig;
import com.lease.saps.model.dto.provider.biz.CustomerOrderReq;
import com.lease.saps.model.dto.provider.biz.PayDetailRes;
import com.lease.saps.model.enums.*;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lease.permission.model.RowFilter;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lease.saps.mapper.ReplaceBatteryDeviceFlowMapper;
import com.lease.saps.model.dto.replacebattery.DeviceFlowPaySyncDto;
import com.lease.saps.model.dto.replacebattery.DeviceFlowSyncPageReq;
import com.lease.saps.model.dto.replacebattery.PayApplyResp;
import com.lease.saps.model.dto.replacebattery.SettlementQueryPage;
import com.lease.saps.model.dto.replacebattery.DeviceFlowSyncReq;
import com.lease.saps.model.dto.replacebattery.SettlementQueryReq;
import com.lease.saps.model.dto.replacebattery.SettlementQueryResp;
import com.lease.saps.model.dto.replacebattery.ReplaceBatteryDeviceFlowQueryPage;
import com.lease.saps.model.entity.CustomerBillEntity;
import com.lease.saps.model.entity.CustomerBillPayDetailEntity;
import com.lease.saps.model.entity.CustomerBillPaySubjectDetailEntity;
import com.lease.saps.model.entity.*;
import com.lease.saps.model.entity.replacebattery.ReplaceBatteryDeviceFlow;
import com.lease.saps.model.vo.replacebattery.ReplaceBatteryDeviceFlowVO;
import com.lease.saps.service.CustomerBillPayDetailService;
import com.lease.saps.service.CustomerBillPaySubjectDetailService;
import com.lease.saps.service.CustomerBillService;
import com.lease.saps.service.*;
import com.lease.saps.service.replacebattery.ReplaceBatteryDeviceService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.lease.saps.model.enums.DeviceFlowProcessProgressEnum.*;

@Slf4j
@Service
public class ReplaceBatteryDeviceServiceImpl extends ServiceImpl<ReplaceBatteryDeviceFlowMapper, ReplaceBatteryDeviceFlow> implements ReplaceBatteryDeviceService {

    @Autowired
    private CustomerOrderService customerOrderService;

    @Autowired
    private CustomerOrderDetailService customerOrderDetailService;

    @Autowired
    private CustomerBillService customerBillService;

    @Autowired
    private CustomerBillPayDetailService customerBillPayDetailService;

    @Autowired
    private CustomerBillPaySubjectDetailService customerBillPaySubjectDetailService;

    @Autowired
    private BusinessConfigService businessConfigService;

    @Autowired
    private CustomerOrderAsinfoService customerOrderAsinfoService;

    @Autowired
    private ThreadPoolTaskConfig threadPoolTaskConfig;

    @Autowired
    private RedissonLock redissonLock;

    private final String PROJECT_NAME = "name";

    @Override
    public List<SettlementQueryResp> billSettlementDetail(SettlementQueryReq settlementQueryReq) {

        List<String> billNoPage = Arrays.stream(settlementQueryReq.getSettlementBillNo().split(",")).collect(Collectors.toList());

        //查询所有符合条件的科目记录
        return customerBillPaySubjectDetailService.queryDeviceBillDetail(billNoPage);
    }

    @Override
    public Page<PayApplyResp> queryPayApply(SettlementQueryPage settlementQueryPage) {

        if (settlementQueryPage.getPayTimeArray() != null && settlementQueryPage.getPayTimeArray().length > 0) {
            settlementQueryPage.setStartPayTime(settlementQueryPage.getPayTimeArray()[0]);
            settlementQueryPage.setEndPayTime(settlementQueryPage.getPayTimeArray()[1]);
        }
        if (settlementQueryPage.getRefundTimeArray() != null && settlementQueryPage.getRefundTimeArray().length > 0) {
            settlementQueryPage.setStartRefundTime(settlementQueryPage.getRefundTimeArray()[0]);
            settlementQueryPage.setEndRefundTime(settlementQueryPage.getRefundTimeArray()[1]);
        }
        if (settlementQueryPage.getCreateTimeArray() != null && settlementQueryPage.getCreateTimeArray().length > 0) {
            settlementQueryPage.setStartCreateTime(settlementQueryPage.getCreateTimeArray()[0]);
            settlementQueryPage.setEndCreateTime(settlementQueryPage.getCreateTimeArray()[1]);
        }
        return baseMapper.queryPayApply(settlementQueryPage);
    }

    @Override
    public Page<ReplaceBatteryDeviceFlowVO> queryPage(ReplaceBatteryDeviceFlowQueryPage queryPage) {

        // 行权限条件按前缀拆分：主表字段留主 WHERE，关联表字段走 IN 子查询
        splitRowFilters(queryPage);

        // 执行分页查询（走 XML queryPage：主表条件 + 资产订单/账单关联过滤 + 行权限）
        Page<ReplaceBatteryDeviceFlow> pageResult = baseMapper.queryPage(queryPage);

        // 转换为VO对象
        Page<ReplaceBatteryDeviceFlowVO> voPage = new Page<>(pageResult.getCurrent(), pageResult.getSize(), pageResult.getTotal());
        List<ReplaceBatteryDeviceFlowVO> voList = pageResult.getRecords().stream()
                .map(ReplaceBatteryDeviceFlowVO::new)
                .peek(vo -> {
                    //收款账户金额要与押金保持一致，单独押金订单会因拆分无法正常处理，所以这里要单独处理
                    vo.setReceiveAccountSplitAmount(vo.getDepositAmount());
                    //子支付类型翻译
                    if(vo.getSubMode() != null){
                        vo.setPayMode(vo.getSubMode().getDesc());
                    }
                })
                .collect(Collectors.toList());
        voPage.setRecords(voList);

        return voPage;
    }

    private LambdaQueryWrapper<ReplaceBatteryDeviceFlow> getFlowQueryWrapper(ReplaceBatteryDeviceFlowQueryPage
                                                                                     queryPage) {
        // 构建查询条件
        LambdaQueryWrapper<ReplaceBatteryDeviceFlow> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ReplaceBatteryDeviceFlow::getIsDelete, 0);
        queryWrapper.eq(ReplaceBatteryDeviceFlow::getIsCancel, 0);

        // 添加各种查询条件
        if (queryPage.getPayApplyFlow() != null && !queryPage.getPayApplyFlow().isEmpty()) {
            queryWrapper.eq(ReplaceBatteryDeviceFlow::getPayApplyFlow, queryPage.getPayApplyFlow());
        }
        if (queryPage.getProjectNo() != null && !queryPage.getProjectNo().isEmpty()) {
            queryWrapper.eq(ReplaceBatteryDeviceFlow::getProjectNo, queryPage.getProjectNo());
        }
        if (queryPage.getProjectName() != null && !queryPage.getProjectName().isEmpty()) {
            queryWrapper.like(ReplaceBatteryDeviceFlow::getProjectName, queryPage.getProjectName());
        }
        if (queryPage.getChannelFlow() != null && !queryPage.getChannelFlow().isEmpty()) {
            queryWrapper.eq(ReplaceBatteryDeviceFlow::getChannelFlow, queryPage.getChannelFlow());
        }
        if (queryPage.getPayChannel() != null) {
            queryWrapper.eq(ReplaceBatteryDeviceFlow::getPayChannel, queryPage.getPayChannel().getValue());
        }
        if (queryPage.getVerifyStatus() != null) {
            queryWrapper.eq(ReplaceBatteryDeviceFlow::getVerifyStatus, queryPage.getVerifyStatus().getValue());
        }
        if (queryPage.getTradeStatus() != null) {
            queryWrapper.eq(ReplaceBatteryDeviceFlow::getTradeStatus, queryPage.getTradeStatus().getValue());
        }
        if (queryPage.getOrderType() != null) {
            queryWrapper.eq(ReplaceBatteryDeviceFlow::getOrderType, queryPage.getOrderType()
                    .getValue());
        }
        if (queryPage.getEpNo() != null && !queryPage.getEpNo().isEmpty()) {
            queryWrapper.eq(ReplaceBatteryDeviceFlow::getEpNo, queryPage.getEpNo());
        }
        if (queryPage.getEpName() != null && !queryPage.getEpName().isEmpty()) {
            queryWrapper.like(ReplaceBatteryDeviceFlow::getEpName, queryPage.getEpName());
        }
        if (queryPage.getRefundStatus() != null && !queryPage.getRefundStatus().isEmpty()) {
            queryWrapper.eq(ReplaceBatteryDeviceFlow::getRefundStatus, queryPage.getRefundStatus());
        }
        if (StringUtils.isNotBlank(queryPage.getStartPayTime()) && StringUtils.isNotBlank(queryPage.getEndPayTime())) {
            queryWrapper.between(ReplaceBatteryDeviceFlow::getPayTime, queryPage.getStartPayTime(), queryPage.getEndPayTime());
        }

        if (StringUtils.isNotBlank(queryPage.getStartRefundTime()) && StringUtils.isNotBlank(queryPage.getEndRefundTime())) {
            queryWrapper.between(ReplaceBatteryDeviceFlow::getRefundTime, queryPage.getStartRefundTime(), queryPage.getEndRefundTime());
        }

        // 行数据权限过滤（列表/导出/下载共用的 wrapper）
        applyRowFilters(queryWrapper, queryPage.getRowFilters());

        //按时间倒序
        queryWrapper.orderByDesc(ReplaceBatteryDeviceFlow::getCreateTime);

        return queryWrapper;
    }

    /**
     * 行权限条件应用到 LambdaQueryWrapper。
     * 字段名已在公共切面里经 RowFieldMappingProvider 白名单校验（纯标识符），
     * 故这里用 apply 拼接字段名是安全的；值一律走 {0}/{1} 占位符参数化绑定。
     */
    private void applyRowFilters(LambdaQueryWrapper<ReplaceBatteryDeviceFlow> queryWrapper, List<com.lease.permission.model.RowFilter> rowFilters) {
        if (rowFilters == null || rowFilters.isEmpty()) {
            return;
        }
        for (com.lease.permission.model.RowFilter rs : rowFilters) {
            if (rs == null || StringUtils.isBlank(rs.getField())) {
                continue;
            }
            String field = rs.getField();
            List<String> values = rs.getValues();
            String op = rs.getOp() == null ? "=" : rs.getOp();
            switch (op) {
                case "IN":
                case "NOT IN":
                    if (values != null && !values.isEmpty()) {
                        queryWrapper.apply(buildInSql(field, op, values), values.toArray());
                    }
                    break;
                case "LIKE":
                    if (values != null && !values.isEmpty()) {
                        queryWrapper.apply(field + " LIKE CONCAT('%', {0}, '%')", values.get(0));
                    }
                    break;
                case "!=":
                    if (values != null && !values.isEmpty()) {
                        queryWrapper.apply(field + " != {0}", values.get(0));
                    }
                    break;
                default:
                    if (values != null && !values.isEmpty()) {
                        queryWrapper.apply(field + " = {0}", values.get(0));
                    }
                    break;
            }
        }
    }

    /** 构造 IN / NOT IN 的参数化占位符 SQL：field IN ({0}, {1}, ...) */
    private String buildInSql(String field, String op, List<String> values) {
        StringBuilder sb = new StringBuilder(field).append(' ').append(op).append(" (");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append('{').append(i).append('}');
        }
        sb.append(')');
        return sb.toString();
    }

    @Override
    public List<PayDetailRes> queryPayDetailList(CustomerOrderReq dto) {
        return baseMapper.queryPayDetailList(dto);
    }

    @Override
    public ReplaceBatteryDeviceFlowVO queryPageStatistics(ReplaceBatteryDeviceFlowQueryPage queryPage) {
        // 设置不分页,避免 MyBatis-Plus 自动添加 LIMIT
        queryPage.setSearchCount(false);
        queryPage.setCurrent(-1);
        queryPage.setSize(-1);

        // 行权限条件按前缀拆分：主表字段留主 WHERE，关联表字段走 IN 子查询
        splitRowFilters(queryPage);

        return baseMapper.queryPageStatistics(queryPage);
    }

    /**
     * 行权限条件拆分：ao./cb./cbpd. 前缀 → joinFilters（资产订单/账单关联过滤，走 IN 子查询）；
     * 其余（r. 前缀或裸列名）→ flowFilters（主表过滤，直接拼主 WHERE）。
     * 不修改原始 rowFilters，可重复调用。
     */
    private void splitRowFilters(ReplaceBatteryDeviceFlowQueryPage queryPage) {
        List<RowFilter> rowFilters = queryPage.getRowFilters();
        if (rowFilters == null || rowFilters.isEmpty()) {
            queryPage.setFlowFilters(null);
            queryPage.setJoinFilters(null);
            return;
        }
        List<RowFilter> flowFilters = new ArrayList<>();
        List<RowFilter> joinFilters = new ArrayList<>();
        for (RowFilter rs : rowFilters) {
            if (rs == null || StringUtils.isBlank(rs.getField())) {
                continue;
            }
            String field = rs.getField();
            if (field.startsWith("ao.") || field.startsWith("cb.") || field.startsWith("cbpd.")) {
                joinFilters.add(rs);
            } else {
                flowFilters.add(rs);
            }
        }
        queryPage.setFlowFilters(flowFilters);
        queryPage.setJoinFilters(joinFilters);
    }

    @Override
    public void deviceFlowSync(DeviceFlowSyncReq deviceFlowSyncReq) {

        if (StrUtil.isEmpty(deviceFlowSyncReq.getOrderNo()))
            throw new RuntimeException("订单编号不能为空");

        log.info("设备流程同步接口请求参数: {}", deviceFlowSyncReq);

        //根据状态来处理不同业务
        switch (deviceFlowSyncReq.getPayApplyFlowStatus()) {

            case PAY_PROCESS:
                updateFlowPayStatus(deviceFlowSyncReq.getOrderNo(), PAY_PROCESS);
                break;
            case VERIFY_FAIL:
                updateFlowPayStatus(deviceFlowSyncReq.getOrderNo(), VERIFY_FAIL);
                break;
            case NOT_PAID:
                createDeviceFlow(deviceFlowSyncReq.getOrderNo());
                break;
            case PAY_SUCCESS:
                updateFlowPayStatus(deviceFlowSyncReq.getOrderNo(), PAY_SUCCESS);
                break;
            case SHARING:
                updateFlowRevenueSharingStatus(deviceFlowSyncReq.getOrderNo());
                break;
            case REFUND:
                updateFlowRefundStatus(deviceFlowSyncReq.getOrderNo());
                break;
            default:
                throw new RuntimeException("业务类型不支持");
        }
    }

    @Override
    public ReplaceBatteryDeviceFlow queryFlowByOrderNo(String orderNo) {
        LambdaQueryWrapper<ReplaceBatteryDeviceFlow> flowQuery = new LambdaQueryWrapper<>();
        flowQuery.eq(ReplaceBatteryDeviceFlow::getOrderNo, orderNo);
        return this.getOne(flowQuery);
    }

    @Override
    public ReplaceBatteryDeviceFlow createDeviceFlow(String orderNo) {

        log.info("创建设备流程记录，订单编号: {}", orderNo);

        //按订单号加锁：查无流水后插入属于读-改-写，order_no 无唯一索引，job/手工接口并发会重复建流水
        String lockKey = RedisPrefixKeyEnum.DEVICE_FLOW_CREATE.getKey(orderNo);
        boolean locked = false;
        try {
            locked = redissonLock.tryLock(lockKey, 30L);
            if (!locked)
                throw new RuntimeException("设备流水创建获取锁失败：" + orderNo);

            return createDeviceFlowWithLock(orderNo);
        } finally {
            if (locked && redissonLock.isHoldByCurrentThread(lockKey))
                redissonLock.unlock(lockKey);
        }
    }

    /**
     * 持锁执行流水创建：并发请求在锁内二次查询，已创建则直接返回
     */
    private ReplaceBatteryDeviceFlow createDeviceFlowWithLock(String orderNo) {

        ReplaceBatteryDeviceFlow flow = queryFlowByOrderNo(orderNo);
        if (flow != null) {
            log.info("设备流程记录已存在，跳过创建，订单编号: {}", orderNo);
            return flow;
        }

        flow = new ReplaceBatteryDeviceFlow();

        CustomerOrderEntity customerOrder = customerOrderService.getByOrderNo(orderNo);
        if (customerOrder == null)
            throw new RuntimeException("订单不存在");

        if (!ProjectConstants.LIYI_PROJECT_NO.equals(customerOrder.getProjectNo()))
            throw new RuntimeException("当前订单无需统计");

        //退款单不建流水：退款只更新原支付单流水；手工接口传 NOT_PAID 可绕过 job SQL 的退款单过滤
        if (TransactionTypeEnum.REFUND.equals(customerOrder.getTransactionType())
                || (customerOrder.getSubType() != null
                && TransactionTypeEnum.REFUND.equals(customerOrder.getSubType().getTransactionType())))
            throw new RuntimeException("退款单不建流水：" + orderNo);

        //只复制流水需要承接的订单字段，避免把订单的 version/is_delete/is_cancel 等通用字段带入流水
        flow.setOrderNo(customerOrder.getOrderNo());
        flow.setProjectNo(customerOrder.getProjectNo());
        flow.setShareStatus(customerOrder.getShareStatus());
        //流水创建时间用于 job 时间窗筛选，必须取订单创建时间
        flow.setCreateTime(customerOrder.getCreateTime());
        flow.setCreateBy(customerOrder.getCreateBy());

        //无法直接获取项目名称，所以配置在配置同步之中获取
        BusinessConfigEntity projectNameConfig = businessConfigService.queryConfig(customerOrder.getProjectNo(), PROJECT_NAME);
        if (projectNameConfig == null)
            throw new RuntimeException("项目名称配置缺失：" + customerOrder.getProjectNo());

        flow.setFlowStatus(NOT_PAID);
        flow.setPayApplyFlow(customerOrder.getBizNo());
        flow.setTradeStatus(customerOrder.getStatus());
        flow.setPayChannel(customerOrder.getChannel());
        flow.setSubMode(customerOrder.getSubMode());
        flow.setOrderType(customerOrder.getSubType());
        flow.setOpenId(customerOrder.getOpenId());
        flow.setProjectName(projectNameConfig.getConfigValue());
        flow.setTradeAmount(customerOrder.getTransactionAmount());
        flow.setRefundStatus(DeviceFlowRefundStatusEnum.UNREFUNDED);
        //暂时写死 数据暂未寻找到来源
        flow.setEpNo("OP000001");
        flow.setEpName("上海里易技术服务有限公司");

        //统计收入金额
        BigDecimal incomeAmount = flow.getBusinessIncomeAmount() == null ? BigDecimal.ZERO : flow.getBusinessIncomeAmount();
        BigDecimal depositAmount = flow.getDepositAmount() == null ? BigDecimal.ZERO : flow.getDepositAmount();

        //获取当前订单所有科目金额
        List<CustomerOrderDetailEntity> detailEntities = customerOrderDetailService.listByOrderNo(customerOrder.getOrderNo());

        //保存金额
        flow.setBusinessIncomeAmount(countIncomeAmount(incomeAmount, detailEntities));
        flow.setDepositAmount(countDepositAmount(depositAmount, detailEntities));

        save(flow);

        return flow;

    }

    @Override
    public ReplaceBatteryDeviceFlow updateFlowPayStatus(String orderNo, DeviceFlowProcessProgressEnum flowStatus) {

        log.info("支付完成更新设备流程记录，订单编号: {}", orderNo);

        ReplaceBatteryDeviceFlow flow = queryFlowByOrderNo(orderNo);
        if (flow == null)
            throw new RuntimeException("订单不存在");

        //无法降级处理
        if (flow.getFlowStatus().getValue() >= flowStatus.getValue())
            return flow;

        CustomerOrderEntity customerOrder = customerOrderService.getByOrderNo(orderNo);
        if (customerOrder == null)
            throw new RuntimeException("订单不存在");

        flow.setFlowStatus(flowStatus);
        flow.setTradeStatus(customerOrder.getStatus());
        flow.setModifyTime(customerOrder.getModifyTime());
        flow.setChannelFlow(customerOrder.getCserialNo());
        flow.setPayFee(customerOrder.getChannelFee());
        flow.setRemark(customerOrder.getFailMsg());
        flow.setVerifyStatus(DeviceFlowVerifyStatusEnum.SUCCESS);
        //验证失败修改验证状态
        if (Objects.equals(flowStatus.getValue(), VERIFY_FAIL.getValue()))
            flow.setVerifyStatus(DeviceFlowVerifyStatusEnum.FAIL);

        //只有支付成功才修改完成时间
        if (Objects.equals(customerOrder.getStatus().getValue(), PaymentStatusEnum.SUCCESS.getValue()))
            flow.setPayTime(customerOrder.getFinishTime());

        LambdaUpdateWrapper<ReplaceBatteryDeviceFlow> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(ReplaceBatteryDeviceFlow::getId, flow.getId());

        //更新数据
        update(flow, updateWrapper);

        return flow;

    }

    @Override
    public ReplaceBatteryDeviceFlow updateFlowRevenueSharingStatus(String orderNo) {

        log.info("收益分润完成更新设备流程记录，订单编号: {}", orderNo);

        ReplaceBatteryDeviceFlow flow = queryFlowByOrderNo(orderNo);
        if (flow == null)
            throw new RuntimeException("订单不存在");

        //无法降级处理
        if (flow.getFlowStatus().getValue() >= DeviceFlowProcessProgressEnum.SHARING.getValue())
            return flow;

        CustomerOrderEntity customerOrder = customerOrderService.getByOrderNo(orderNo);
        if (customerOrder == null)
            throw new RuntimeException("订单不存在");

        flow.setShareStatus(customerOrder.getShareStatus());
        flow.setFlowStatus(DeviceFlowProcessProgressEnum.SHARING);

        //查询所有账单
        LambdaQueryWrapper<CustomerBillPayDetailEntity> billQuery = new LambdaQueryWrapper<>();
        billQuery.eq(CustomerBillPayDetailEntity::getOrderNo, orderNo);
        //账单编号以，分割
        String customerBills = customerBillPayDetailService.list(billQuery)
                .stream().map(CustomerBillPayDetailEntity::getBillNo)
                .collect(Collectors.joining(","));

        //保存账单结算编号
        flow.setSettleBillNo(customerBills);
        flow.setModifyTime(new Date());

        //更新分账数据 同时获取是否为调整订单标记
        List<CustomerOrderAsinfoEntity> asinfoList = customerOrderAsinfoService.listByOrderNo(orderNo);
        if (asinfoList.isEmpty())
            throw new RuntimeException("分账明细不存在：" + orderNo);

        Integer markAsInfo = asinfoList.stream()
                .peek(order -> {

                    //只更新分账成功的账单
                    if (!order.getPaymentStatus().equals(PaymentStatusEnum.SUCCESS))
                        return;

                    switch (order.getUserType()) {

                        //租户金额
                        case TENANT:

                            flow.setTenantArrivalAmount(order.getArriveAmount());
                            flow.setTenantSplitAmount(order.getAmount());

                            break;

                        //先行通金额
                        case RENTER:

                            flow.setXxtArrivalAmount(order.getArriveAmount());
                            flow.setXxtSplitAmount(order.getAmount());

                            break;

                        //合资公司即收款账户
                        case OPERATOR:

                            flow.setReceiveAccountSplitAmount(order.getArriveAmount());
                            flow.setReceiveAccountArrivalAmount(order.getArriveAmount());

                            break;
                    }

                }).map(CustomerOrderAsinfoEntity::getAdjustMark)
                .collect(Collectors.toList()).get(0);

        //原因：因为分账会涉及到无法整分订单导致分账失败
        //策略：总分账金额= 合资公司保留金额+租户实际被分金额
        //1:合资公司保留多余无法被分账金额
        //2:展示租户被分账金额为：总分账金额
        if (markAsInfo != null && markAsInfo == 1) {

            BigDecimal tenantSplitAmount = flow.getTenantArrivalAmount() == null ? BigDecimal.ZERO : flow.getTenantArrivalAmount();
            BigDecimal receiveAccountSplitAmount = flow.getReceiveAccountSplitAmount() == null ? BigDecimal.ZERO : flow.getReceiveAccountSplitAmount();
            flow.setTenantSplitAmount(tenantSplitAmount.add(receiveAccountSplitAmount));
            flow.setRemark("分账金额四舍五入导致收款账户金额留存");
        }
        //更新账单数据
        LambdaUpdateWrapper<ReplaceBatteryDeviceFlow> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(ReplaceBatteryDeviceFlow::getId, flow.getId());
        update(flow, updateWrapper);

        return flow;
    }

    @Override
    public ReplaceBatteryDeviceFlow updateFlowRefundStatus(String orderNo) {

        log.info("退款完成更新设备流程记录，订单编号: {}", orderNo);

        CustomerOrderEntity refundCustomerOrder = customerOrderService.getByOrderNo(orderNo);
        if (refundCustomerOrder == null)
            throw new RuntimeException("退款订单不存在");

        //只更新成功订单
        if (!refundCustomerOrder.getStatus().equals(PaymentStatusEnum.SUCCESS))
            throw new RuntimeException("退款订单未完成");

        //按支付单号加分布式锁：退款单号追加与退款金额累加属于读-改-写，防止 job/回调/API 并发丢更新
        String lockKey = RedisPrefixKeyEnum.DEVICE_FLOW_REFUND.getKey(refundCustomerOrder.getSourceOrderNo());
        boolean locked = false;
        try {
            locked = redissonLock.tryLock(lockKey, 30L);
            if (!locked)
                throw new RuntimeException("退款流水同步获取锁失败：" + refundCustomerOrder.getSourceOrderNo());

            CustomerOrderEntity customerOrder = customerOrderService.getByOrderNo(refundCustomerOrder.getSourceOrderNo());
            if (customerOrder == null)
                throw new RuntimeException("支付订单不存在");

            ReplaceBatteryDeviceFlow flow = queryFlowByOrderNo(refundCustomerOrder.getSourceOrderNo());
            if (flow == null)
                throw new RuntimeException("流水订单不存在");

            //无法降级处理
            if (flow.getFlowStatus().getValue() > DeviceFlowProcessProgressEnum.REFUND.getValue())
                return flow;

            //获取当前表中已存在的退款单号，如果已经存在当前单号，则表示已处理过，直接跳过处理,反之增加一条新纪录
            List<String> refundOrderNos = StrUtil.isEmpty(flow.getRefundOrderNo()) ?
                    Lists.newArrayList() :
                    Arrays.stream(flow.getRefundOrderNo().split(",")).collect(Collectors.toList());

            if (refundOrderNos.contains(orderNo))
                return flow;

            //新增新纪录
            refundOrderNos.add(orderNo);
            flow.setRefundOrderNo(String.join(",", refundOrderNos));

            flow.setFlowStatus(DeviceFlowProcessProgressEnum.REFUND);

            //获取当前退款金额
            BigDecimal refundDepositAmount = flow.getRefundDepositAmount() == null ?
                    BigDecimal.ZERO : flow.getRefundDepositAmount();
            BigDecimal oldIncomeAmount = flow.getRefundBusinessIncomeAmount() == null ? BigDecimal.ZERO : flow.getRefundBusinessIncomeAmount();

            //查询退款单科目明细统计退款金额（退款单申请时已按科目写入本次退款金额；不能取支付单明细，否则部分退款会按支付全额累计）
            List<CustomerOrderDetailEntity> payDetailEntities = customerOrderDetailService.listByOrderNo(refundCustomerOrder.getOrderNo());

            //统计退款结果（累计：本次退款明细金额叠加到已累计金额上）
            BigDecimal newRefundDepositAmount = countDepositAmount(refundDepositAmount, payDetailEntities);
            BigDecimal newRefundIncomeAmount = countIncomeAmount(oldIncomeAmount, payDetailEntities);
            flow.setRefundDepositAmount(newRefundDepositAmount);
            flow.setRefundBusinessIncomeAmount(newRefundIncomeAmount);

            //按累计退款金额判断退款状态：累计退款 >= 支付单金额为全额退款，否则为部分退款
            BigDecimal totalRefundAmount = newRefundDepositAmount.add(newRefundIncomeAmount);
            if (totalRefundAmount.compareTo(customerOrder.getTransactionAmount()) >= 0)
                flow.setRefundStatus(DeviceFlowRefundStatusEnum.FULL_REFUND);
            else if (totalRefundAmount.compareTo(BigDecimal.ZERO) > 0)
                flow.setRefundStatus(DeviceFlowRefundStatusEnum.PARTIAL_REFUND);
            else
                flow.setRefundStatus(DeviceFlowRefundStatusEnum.UNREFUNDED);

            //退款时间取最大值：多笔退款乱序/并发处理时，已记录的退款时间不允许回退
            LocalDateTime refundFinishTime = refundCustomerOrder.getFinishTime();
            if (flow.getRefundTime() == null || (refundFinishTime != null && refundFinishTime.isAfter(flow.getRefundTime())))
                flow.setRefundTime(refundFinishTime);

            flow.setModifyTime(new Date());

            //更新账单数据
            LambdaUpdateWrapper<ReplaceBatteryDeviceFlow> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(ReplaceBatteryDeviceFlow::getId, flow.getId());
            update(flow, updateWrapper);

            return flow;
        } finally {
            if (locked && redissonLock.isHoldByCurrentThread(lockKey))
                redissonLock.unlock(lockKey);
        }
    }

    //统计收入金额（押金科目仅统计押金，其余科目全部计入收入，后续新增科目无需单独调整；
    //注意：若新增科目既非押金也非收入，会被归入收入，届时需重新评估口径）
    private BigDecimal countIncomeAmount(BigDecimal
                                                 oldIncomeAmount, List<CustomerOrderDetailEntity> detailEntities) {
        return detailEntities.stream()
                .filter(entity -> !PaySubjectNoEnum.MARGIN.getValue().equals(entity.getSubjectNo()))
                .map(CustomerOrderDetailEntity::getAmount)
                .reduce(oldIncomeAmount, BigDecimal::add);
    }

    //查询当前退款金额关联的科目，单独计算押金金额，叠加之前退款金额并保存
    private BigDecimal countDepositAmount(BigDecimal
                                                  oldIncomeAmount, List<CustomerOrderDetailEntity> detailEntities) {
        return detailEntities.stream()
                .filter(entity -> PaySubjectNoEnum.MARGIN.getValue().equals(entity.getSubjectNo()))
                .map(CustomerOrderDetailEntity::getAmount)
                .reduce(oldIncomeAmount, BigDecimal::add);
    }

    @Override
    public List<DeviceFlowPaySyncDto> selectPaySyncPage(DeviceFlowSyncPageReq req) {
        return baseMapper.selectPaySyncPage(req);
    }

    @Override
    public List<ReplaceBatteryDeviceFlow> selectSharingSyncPage(DeviceFlowSyncPageReq req) {
        return baseMapper.selectSharingSyncPage(req);
    }


}