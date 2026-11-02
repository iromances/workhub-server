package com.lease.saps.business.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.lease.saps.business.DeviceFlowLYBizService;
import com.lease.saps.common.constants.ProjectConstants;
import com.lease.saps.common.exception.SystemException;
import com.lease.saps.common.exception.SystemExceptionEnum;
import com.lease.saps.common.util.ThreadPoolTaskConfig;
import com.lease.saps.model.dto.replacebattery.DeviceFlowPaySyncDto;
import com.lease.saps.model.dto.replacebattery.DeviceFlowSyncPageReq;
import com.lease.saps.model.dto.replacebattery.DeviceFlowSyncReq;
import com.lease.saps.model.dto.replacebattery.PayApplyResp;
import com.lease.saps.model.entity.ConsumeFlowEntity;
import com.lease.saps.model.entity.CustomerOrderEntity;
import com.lease.saps.model.entity.replacebattery.ReplaceBatteryDeviceFlow;
import com.lease.saps.model.enums.DeviceFlowProcessProgressEnum;
import com.lease.saps.model.enums.PaymentStatusEnum;
import com.lease.saps.model.vo.schedule.DeviceFlowScheduleReq;
import com.lease.saps.service.CustomerOrderService;
import com.lease.saps.service.replacebattery.ReplaceBatteryDeviceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DeviceFlowLYBizServiceImpl implements DeviceFlowLYBizService {

    /**
     * 未指定或非法批量大小时的默认值
     */
    private static final int DEFAULT_BATCH_SIZE = 20;

    /**
     * 单页最大条数，防止一次拉取过多数据
     */
    private static final int MAX_BATCH_SIZE = 500;

    @Autowired
    private ReplaceBatteryDeviceService replaceBatteryDeviceService;
    @Autowired
    private CustomerOrderService customerOrderService;
    @Autowired
    private ThreadPoolTaskConfig threadPoolTaskConfig;

    @Override
    public void execute(DeviceFlowScheduleReq params) {

        //手动指定订单号与时间窗在入口统一处理，四个同步流程共用
        List<String> orderNos = getOrderNosFromParams(params);
        LocalDate yesterday = params.getExecuteDate().minusDays(1);

        //批量大小兜底与上限校验：JSON 显式传 null 会覆盖字段默认值
        Integer batchSize = params.getBatchSize();
        if (batchSize == null || batchSize <= 0)
            batchSize = DEFAULT_BATCH_SIZE;
        if (batchSize > MAX_BATCH_SIZE)
            batchSize = MAX_BATCH_SIZE;

        DeviceFlowSyncPageReq pageReq = new DeviceFlowSyncPageReq();
        pageReq.setDayStart(yesterday.atStartOfDay());
        pageReq.setDayEnd(yesterday.atTime(23, 59, 59));
        pageReq.setOrderNos(orderNos);
        pageReq.setPageSize(batchSize);

        //未保证同步所有数据，所以从初始流程开始保证数据完整
        CompletableFuture.runAsync(() -> {

            //未创建流水
            unCreateFlowSync(pageReq, 0L);
            //同步支付状态流水
            paymentStatusFlowSync(pageReq, 0L);
            //未同步分账状态流水
            sharingFlowSync(pageReq, 0L);
            //未同步退款状态流水
            refundFlowSync(pageReq, 0L);

        }, threadPoolTaskConfig.getConsumeFlowExecutor());
    }

    /**
     * 同步未创建流水
     *
     * @param req     分页查询条件（入口已填充时间窗、订单号、页大小）
     * @param startId 分页起点id
     */
    public void unCreateFlowSync(DeviceFlowSyncPageReq req, Long startId) {

        //设置本步骤查询条件：项目（已建流水、退款单由 SQL 过滤）
        req.setProjectNo(ProjectConstants.LIYI_PROJECT_NO);

        //步骤整体（查询+分页）异常只记录日志，不影响其他步骤
        Long currentStartId = startId;
        try {
            //循环分页处理，避免订单量大时递归过深
            while (true) {

                req.setStartId(currentStartId);
                log.info("未创建流水同步开始，startId={}", currentStartId);

                //分页查询时间窗内未建流水的订单（SQL过滤：已有流水的不返回）
                List<CustomerOrderEntity> customerOrderPage = customerOrderService.selectUnCreatedFlowOrderPage(req);
                if (customerOrderPage.isEmpty()) {
                    log.info("未创建流水同步无待处理数据，startId={}", currentStartId);
                    break;
                }
                log.info("未创建流水同步查询到{}条待处理订单，startId={}", customerOrderPage.size(), currentStartId);

                //反转列表
                Collections.reverse(customerOrderPage);

                int successCount = 0;
                int failCount = 0;

                for (CustomerOrderEntity customerOrder : customerOrderPage) {

                    //单条异常不中断整批，记录日志后继续下一个订单
                    try {
                        DeviceFlowSyncReq deviceFlowSyncReq = new DeviceFlowSyncReq();
                        deviceFlowSyncReq.setPayApplyFlowStatus(DeviceFlowProcessProgressEnum.NOT_PAID);
                        deviceFlowSyncReq.setOrderNo(customerOrder.getOrderNo());

                        log.info("未创建流水同步开始处理订单，orderNo={}", customerOrder.getOrderNo());

                        replaceBatteryDeviceService.deviceFlowSync(deviceFlowSyncReq);
                        successCount++;
                    } catch (Exception e) {
                        log.error("未创建流水同步失败，orderNo={}", customerOrder.getOrderNo(), e);
                        failCount++;
                    }
                }

                log.info("未创建流水同步本页处理完成，startId={}，成功{}条，失败{}条", currentStartId, successCount, failCount);

                //本页数量小于分页大小说明已到最后一页
                if (customerOrderPage.size() < req.getPageSize())
                    break;

                currentStartId = customerOrderPage.get(0).getId();
            }
        } catch (Exception e) {
            log.error("未创建流水同步异常，startId={}", currentStartId, e);
        }

    }

    /**
     * 同步订单状态
     *
     * @param req     分页查询条件（入口已填充时间窗、订单号、页大小）
     * @param startId 分页起点id
     */
    public void paymentStatusFlowSync(DeviceFlowSyncPageReq req, Long startId) {

        //步骤整体（查询+分页）异常只记录日志，不影响其他步骤
        Long currentStartId = startId;
        try {
            //循环分页处理，避免订单量大时递归过深
            while (true) {

                req.setStartId(currentStartId);
                log.info("支付状态同步开始，startId={}", currentStartId);

                //分页查询时间窗内流水及其订单状态（SQL过滤：排除未支付/无效订单）
                List<DeviceFlowPaySyncDto> flowPage = replaceBatteryDeviceService.selectPaySyncPage(req);
                if (flowPage.isEmpty()) {
                    log.info("支付状态同步无待处理数据，startId={}", currentStartId);
                    break;
                }
                log.info("支付状态同步查询到{}条待处理流水，startId={}", flowPage.size(), currentStartId);

                //反转列表
                Collections.reverse(flowPage);

                int successCount = 0;
                int failCount = 0;

                for (DeviceFlowPaySyncDto flow : flowPage) {

                    //单条异常不中断整批，记录日志后继续下一个订单
                    try {
                        PaymentStatusEnum orderStatus = PaymentStatusEnum.getByValue(flow.getOrderStatus());
                        if (orderStatus == null) {
                            log.warn("支付状态同步跳过：订单状态无法识别，orderNo={}，status={}", flow.getOrderNo(), flow.getOrderStatus());
                            continue;
                        }

                        DeviceFlowSyncReq deviceFlowSyncReq = new DeviceFlowSyncReq();

                        //这里的pay_success实际指支付结束，即成功失败都属于结束
                        DeviceFlowProcessProgressEnum flowStatus = DeviceFlowProcessProgressEnum.PAY_SUCCESS;

                        if (orderStatus == PaymentStatusEnum.PROCESSING)
                            flowStatus = DeviceFlowProcessProgressEnum.PAY_PROCESS;

                        //如果渠道流水为空则视为验证失败
                        if (orderStatus == PaymentStatusEnum.FAILED && StrUtil.isEmpty(flow.getCserialNo()))
                            flowStatus = DeviceFlowProcessProgressEnum.VERIFY_FAIL;

                        deviceFlowSyncReq.setPayApplyFlowStatus(flowStatus);
                        deviceFlowSyncReq.setOrderNo(flow.getOrderNo());

                        log.info("支付状态同步开始处理订单，orderNo={}，订单状态={}，目标流水状态={}",
                                flow.getOrderNo(), orderStatus, flowStatus);

                        replaceBatteryDeviceService.deviceFlowSync(deviceFlowSyncReq);
                        successCount++;
                    } catch (Exception e) {
                        log.error("支付状态同步失败，orderNo={}", flow.getOrderNo(), e);
                        failCount++;
                    }
                }

                log.info("支付状态同步本页处理完成，startId={}，成功{}条，失败{}条", currentStartId, successCount, failCount);

                //本页数量小于分页大小说明已到最后一页
                if (flowPage.size() < req.getPageSize())
                    break;

                currentStartId = flowPage.get(0).getId();
            }
        } catch (Exception e) {
            log.error("支付状态同步异常，startId={}", currentStartId, e);
        }

    }

    /**
     * 同步分账状态
     *
     * @param req     分页查询条件（入口已填充时间窗、订单号、页大小）
     * @param startId 分页起点id
     */
    public void sharingFlowSync(DeviceFlowSyncPageReq req, Long startId) {

        //步骤整体（查询+分页）异常只记录日志，不影响其他步骤
        Long currentStartId = startId;
        try {
            //循环分页处理，避免订单量大时递归过深
            while (true) {

                req.setStartId(currentStartId);
                log.info("分账状态同步开始，startId={}", currentStartId);

                //分页查询时间窗内分账全部成功的流水（SQL过滤：存在分账单且全部成功）
                List<ReplaceBatteryDeviceFlow> flowPage = replaceBatteryDeviceService.selectSharingSyncPage(req);
                if (flowPage.isEmpty()) {
                    log.info("分账状态同步无待处理数据，startId={}", currentStartId);
                    break;
                }
                log.info("分账状态同步查询到{}条待处理流水，startId={}", flowPage.size(), currentStartId);

                //倒序列表
                Collections.reverse(flowPage);

                int successCount = 0;
                int failCount = 0;

                for (ReplaceBatteryDeviceFlow flow : flowPage) {

                    //单条异常不中断整批，记录日志后继续下一个订单
                    try {
                        DeviceFlowSyncReq deviceFlowSyncReq = new DeviceFlowSyncReq();
                        deviceFlowSyncReq.setPayApplyFlowStatus(DeviceFlowProcessProgressEnum.SHARING);
                        deviceFlowSyncReq.setOrderNo(flow.getOrderNo());

                        log.info("分账状态同步开始处理订单，orderNo={}", flow.getOrderNo());

                        replaceBatteryDeviceService.deviceFlowSync(deviceFlowSyncReq);
                        successCount++;
                    } catch (Exception e) {
                        log.error("分账状态同步失败，orderNo={}", flow.getOrderNo(), e);
                        failCount++;
                    }
                }

                log.info("分账状态同步本页处理完成，startId={}，成功{}条，失败{}条", currentStartId, successCount, failCount);

                //本页数量小于分页大小说明已到最后一页
                if (flowPage.size() < req.getPageSize())
                    break;

                currentStartId = flowPage.get(0).getId();
            }
        } catch (Exception e) {
            log.error("分账状态同步异常，startId={}", currentStartId, e);
        }

    }

    /**
     * 同步退款状态（以退款单为驱动）
     *
     * @param req     分页查询条件（入口已填充时间窗、订单号、页大小）
     * @param startId 分页起点id
     */
    public void refundFlowSync(DeviceFlowSyncPageReq req, Long startId) {

        //步骤整体（查询+分页）异常只记录日志，不影响其他步骤
        Long currentStartId = startId;
        try {
            //循环分页处理，避免订单量大时递归过深
            while (true) {

                req.setStartId(currentStartId);
                log.info("退款状态同步开始，startId={}", currentStartId);

                //按退款单分页查询待同步数据（分账成功后才退款、验证失败不参与、已记入流水的不返回）
                List<CustomerOrderEntity> refundOrderPage = customerOrderService.selectRefundOrderSyncPage(req);
                if (refundOrderPage.isEmpty()) {
                    log.info("退款状态同步无待处理数据，startId={}", currentStartId);
                    break;
                }
                log.info("退款状态同步查询到{}条待处理退款单，startId={}", refundOrderPage.size(), currentStartId);

                //反转列表
                Collections.reverse(refundOrderPage);

                int successCount = 0;
                int failCount = 0;

                for (CustomerOrderEntity refundOrder : refundOrderPage) {

                    //单条异常不中断整批，记录日志后继续下一个订单
                    try {
                        DeviceFlowSyncReq deviceFlowSyncReq = new DeviceFlowSyncReq();
                        deviceFlowSyncReq.setPayApplyFlowStatus(DeviceFlowProcessProgressEnum.REFUND);
                        //必须传退款单号，下游按退款单解析原支付单并累加退款金额
                        deviceFlowSyncReq.setOrderNo(refundOrder.getOrderNo());

                        log.info("退款状态同步开始处理退款单，orderNo={}", refundOrder.getOrderNo());

                        replaceBatteryDeviceService.deviceFlowSync(deviceFlowSyncReq);
                        successCount++;
                    } catch (Exception e) {
                        log.error("退款状态同步失败，orderNo={}", refundOrder.getOrderNo(), e);
                        failCount++;
                    }
                }

                log.info("退款状态同步本页处理完成，startId={}，成功{}条，失败{}条", currentStartId, successCount, failCount);

                //本页数量小于分页大小说明已到最后一页
                if (refundOrderPage.size() < req.getPageSize())
                    break;

                currentStartId = refundOrderPage.get(0).getId();
            }
        } catch (Exception e) {
            log.error("退款状态同步异常，startId={}", currentStartId, e);
        }

    }

    /**
     * 获取手动指定的订单号列表（searchDate JSON 中的 orders 数组，仅支持数组格式）
     *
     * @param params 调度参数
     * @return 订单号列表，未指定时返回空列表
     */
    private List<String> getOrderNosFromParams(DeviceFlowScheduleReq params) {

        if (StrUtil.isEmpty(params.getSearchDate()))
            return Collections.emptyList();

        //参数非空但格式非法时直接报错，避免静默退化为全时间窗扫描
        if (!JSONUtil.isJson(params.getSearchDate()))
            throw new SystemException(SystemExceptionEnum.PARAMS_ERROR);

        Object searchData = JSON.parse(params.getSearchDate());
        if (!(searchData instanceof JSONObject))
            throw new SystemException(SystemExceptionEnum.PARAMS_ERROR);

        JSONArray orders = ((JSONObject) searchData).getJSONArray("orders");
        if (orders == null || orders.isEmpty())
            return Collections.emptyList();

        //过滤空白单号，避免拼出 IN ('') 导致本页静默 0 条
        List<String> orderNos = orders.toJavaList(String.class).stream()
                .filter(StrUtil::isNotBlank)
                .map(String::trim)
                .collect(Collectors.toList());

        //传了 orders 但全部无效，按参数错误处理
        if (orderNos.isEmpty())
            throw new SystemException(SystemExceptionEnum.PARAMS_ERROR);

        return orderNos;
    }

}
