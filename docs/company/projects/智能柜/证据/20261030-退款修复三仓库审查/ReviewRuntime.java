
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.lang.reflect.*;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson.*;
import com.google.common.collect.Lists;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.*;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.extension.plugins.OptimisticLockerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.baomidou.mybatisplus.extension.handlers.MybatisEnumTypeHandler;
import com.lease.saps.model.entity.*;
import com.lease.saps.model.entity.replacebattery.ReplaceBatteryDeviceFlow;
import com.lease.saps.model.enums.*;
import com.lease.saps.model.dto.replacebattery.*;
import com.lease.saps.model.vo.schedule.DeviceFlowScheduleReq;
import com.lease.saps.model.api.payment.*;
import com.lease.common.core.Result;
import com.lease.common.core.ResultCode;
import com.lease.saps.common.exception.*;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.util.StringUtils;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.*;
import org.h2.jdbcx.JdbcDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Target methods extracted verbatim from b83576e6. The database, Spring transaction manager,
// MyBatis-Plus update and optimistic-lock interceptor are real local libraries.
// Order/detail reads are in-memory doubles, and Redis locking is substituted with a local mutex.
public class ReviewRuntime {
 static Logger log=LoggerFactory.getLogger(ReviewRuntime.class);
 public interface FlowMapper extends BaseMapper<ReplaceBatteryDeviceFlow> {}
 static FlowMapper mapper;static JdbcTemplate jdbc;static DataSourceTransactionManager tm;
 static String columnType(Class<?> t){
  if(t==BigDecimal.class)return "DECIMAL(19,2)";
  if(t==LocalDateTime.class||t==java.util.Date.class)return "TIMESTAMP";
  if(t==String.class)return "VARCHAR(2000)";
  return "BIGINT";
 }
 static void init()throws Exception{
  JdbcDataSource ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:review;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000");
  jdbc=new JdbcTemplate(ds);tm=new DataSourceTransactionManager(ds);
  MybatisConfiguration cfg=new MybatisConfiguration();cfg.setMapUnderscoreToCamelCase(true);
  cfg.setDefaultEnumTypeHandler(MybatisEnumTypeHandler.class);cfg.addInterceptor(new OptimisticLockerInterceptor());cfg.addMapper(FlowMapper.class);
  MybatisSqlSessionFactoryBean bean=new MybatisSqlSessionFactoryBean();bean.setDataSource(ds);bean.setConfiguration(cfg);
  mapper=new SqlSessionTemplate(bean.getObject()).getMapper(FlowMapper.class);
  TableInfo info=TableInfoHelper.getTableInfo(ReplaceBatteryDeviceFlow.class);
  String ddl="CREATE TABLE replace_battery_device_flow (id BIGINT AUTO_INCREMENT PRIMARY KEY";
  for(TableFieldInfo f:info.getFieldList())ddl+=","+f.getColumn()+" "+columnType(f.getPropertyType());
  jdbc.execute(ddl+")");
 }
 static class Mutex {
  final ReentrantLock lock=new ReentrantLock();Runnable afterUnlock;
  boolean tryLock(String key,long seconds){try{return lock.tryLock(seconds,TimeUnit.SECONDS);}catch(InterruptedException e){throw new RuntimeException(e);}}
  boolean isHoldByCurrentThread(String key){return lock.isHeldByCurrentThread();}
  void unlock(String key){lock.unlock();if(afterUnlock!=null)afterUnlock.run();}
 }
 static class OrderData {
  Map<String,CustomerOrderEntity> data=new HashMap<>();
  CustomerOrderEntity getByOrderNo(String n){return data.get(n);}
  CustomerOrderEntity getByBizNo(String n){return null;}
  List<CustomerOrderEntity> queryBySourceNoAndType(String n,Integer st,List<PaymentStatusEnum> statuses){return data.values().stream().filter(o->n.equals(o.getSourceOrderNo())&&statuses.contains(o.getStatus())).collect(Collectors.toList());}
  List<CustomerOrderEntity> selectRefundOrderSyncPage(DeviceFlowSyncPageReq req){
   String done=mapper.selectById(1).getRefundOrderNo();
   return data.values().stream().filter(o->o.getSourceOrderNo()!=null && o.getId()>req.getStartId() && (done==null||!Arrays.asList(done.split(",")).contains(o.getOrderNo()))).sorted(Comparator.comparing(CustomerOrderEntity::getId)).limit(req.getPageSize()).collect(Collectors.toList());
  }
 }
 static class DetailData {
  Map<String,List<CustomerOrderDetailEntity>> data=new HashMap<>();Runnable onRead;
  List<CustomerOrderDetailEntity> listByOrderNo(String n){if(onRead!=null)onRead.run();return data.get(n);}
 }
 static class ServiceUnderReview extends ServiceImpl<FlowMapper,ReplaceBatteryDeviceFlow>{
  private final Logger log=LoggerFactory.getLogger(ServiceUnderReview.class);
  OrderData customerOrderService=new OrderData();DetailData customerOrderDetailService=new DetailData();Mutex redissonLock=new Mutex();
  ServiceUnderReview(){this.baseMapper=mapper;}
  void deviceFlowSync(DeviceFlowSyncReq req){updateFlowRefundStatus(req.getOrderNo());}
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
private BigDecimal countIncomeAmount(BigDecimal
                                                 oldIncomeAmount, List<CustomerOrderDetailEntity> detailEntities) {
        return detailEntities.stream()
                .filter(entity -> !PaySubjectNoEnum.MARGIN.getValue().equals(entity.getSubjectNo()))
                .map(CustomerOrderDetailEntity::getAmount)
                .reduce(oldIncomeAmount, BigDecimal::add);
    }
private BigDecimal countDepositAmount(BigDecimal
                                                  oldIncomeAmount, List<CustomerOrderDetailEntity> detailEntities) {
        return detailEntities.stream()
                .filter(entity -> PaySubjectNoEnum.MARGIN.getValue().equals(entity.getSubjectNo()))
                .map(CustomerOrderDetailEntity::getAmount)
                .reduce(oldIncomeAmount, BigDecimal::add);
    }
public ReplaceBatteryDeviceFlow queryFlowByOrderNo(String orderNo) {
        LambdaQueryWrapper<ReplaceBatteryDeviceFlow> flowQuery = new LambdaQueryWrapper<>();
        flowQuery.eq(ReplaceBatteryDeviceFlow::getOrderNo, orderNo);
        return this.getOne(flowQuery);
    }
 }
 static class BaselineService extends ServiceUnderReview {
private final Logger log=LoggerFactory.getLogger(BaselineService.class);
public ReplaceBatteryDeviceFlow updateFlowRefundStatus(String orderNo) {

        log.info("退款完成更新设备流程记录，订单编号: {}", orderNo);

        CustomerOrderEntity refundCustomerOrder = customerOrderService.getByOrderNo(orderNo);
        if (refundCustomerOrder == null)
            throw new RuntimeException("退款订单不存在");

        //只更新成功订单
        if (!refundCustomerOrder.getStatus().equals(PaymentStatusEnum.SUCCESS))
            throw new RuntimeException("退款订单未完成");

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

        //查询支付结果列表
        List<CustomerOrderDetailEntity> payDetailEntities = customerOrderDetailService.listByOrderNo(customerOrder.getOrderNo());

        //统计退款结果
        flow.setRefundDepositAmount(countDepositAmount(refundDepositAmount, payDetailEntities));
        flow.setRefundBusinessIncomeAmount(countIncomeAmount(oldIncomeAmount, payDetailEntities));

        //根据退款金额与订单金额对比判断退款状态
        if (refundCustomerOrder.getTransactionAmount().compareTo(customerOrder.getTransactionAmount()) < 0)
            flow.setRefundStatus(DeviceFlowRefundStatusEnum.PARTIAL_REFUND);
        else if (refundCustomerOrder.getTransactionAmount().compareTo(customerOrder.getTransactionAmount()) == 0)
            flow.setRefundStatus(DeviceFlowRefundStatusEnum.FULL_REFUND);
        else if (refundCustomerOrder.getTransactionAmount().compareTo(BigDecimal.ZERO) == 0)
            flow.setRefundStatus(DeviceFlowRefundStatusEnum.UNREFUNDED);

        flow.setRefundTime(refundCustomerOrder.getFinishTime());
        flow.setModifyTime(new Date());

        //更新账单数据
        LambdaUpdateWrapper<ReplaceBatteryDeviceFlow> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(ReplaceBatteryDeviceFlow::getId, flow.getId());
        update(flow, updateWrapper);

        return flow;
    }
private BigDecimal countIncomeAmount(BigDecimal
                                                 oldIncomeAmount, List<CustomerOrderDetailEntity> detailEntities) {
        return detailEntities.stream()
                .filter(entity ->
                        StrUtil.equals(PaySubjectNoEnum.RENTAL.getValue(), entity.getSubjectNo()) ||
                                StrUtil.equals(PaySubjectNoEnum.COUPON_PACKAGE.getValue(), entity.getSubjectNo())
                ).map(CustomerOrderDetailEntity::getAmount)
                .reduce(oldIncomeAmount, BigDecimal::add);
    }
private BigDecimal countDepositAmount(BigDecimal
                                                  oldIncomeAmount, List<CustomerOrderDetailEntity> detailEntities) {
        return detailEntities.stream()
                .filter(entity -> PaySubjectNoEnum.MARGIN.getValue().equals(entity.getSubjectNo()))
                .map(CustomerOrderDetailEntity::getAmount)
                .reduce(oldIncomeAmount, BigDecimal::add);
    }
}
 static class JobUnderReview {
  OrderData customerOrderService;ServiceUnderReview replaceBatteryDeviceService;
  JobUnderReview(ServiceUnderReview s){customerOrderService=s.customerOrderService;replaceBatteryDeviceService=s;}
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
 static class SaveUnderReview {
  OrderData customerOrderService;DetailData customerOrderDetailService;
  SaveUnderReview(ServiceUnderReview s){customerOrderService=s.customerOrderService;customerOrderDetailService=s.customerOrderDetailService;}
  void saveFailCustomerOrderAndTransAction(RefundApplyReq a,CustomerOrderEntity o,String m){throw new AssertionError(m);}
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
 }
 static class TimeUnderReview {
  static final DateTimeFormatter FINISH_TIME_FORMATTER=DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
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
 }
 static CustomerOrderEntity order(long id,String no,String source,String amount,String time){
  CustomerOrderEntity o=new CustomerOrderEntity();o.setId(id);o.setOrderNo(no);o.setSourceOrderNo(source);o.setTransactionAmount(new BigDecimal(amount));o.setFinishTime(LocalDateTime.parse(time));o.setStatus(PaymentStatusEnum.SUCCESS);o.setShareStatus(PaymentStatusEnum.SUCCESS);return o;
 }
 static CustomerOrderDetailEntity detail(String subject,String amount){CustomerOrderDetailEntity d=new CustomerOrderDetailEntity();d.setSubjectNo(subject);d.setAmount(new BigDecimal(amount));return d;}
 static ServiceUnderReview fixture(){
  jdbc.execute("TRUNCATE TABLE replace_battery_device_flow");jdbc.execute("ALTER TABLE replace_battery_device_flow ALTER COLUMN id RESTART WITH 1");
  ReplaceBatteryDeviceFlow f=new ReplaceBatteryDeviceFlow();f.setId(1L);f.setOrderNo("P");f.setVersion(1);f.setFlowStatus(DeviceFlowProcessProgressEnum.SHARING);f.setRefundStatus(DeviceFlowRefundStatusEnum.UNREFUNDED);f.setRefundBusinessIncomeAmount(BigDecimal.ZERO);f.setRefundDepositAmount(BigDecimal.ZERO);f.setTradeAmount(new BigDecimal("260"));mapper.insert(f);
  ServiceUnderReview s=new ServiceUnderReview();
  s.customerOrderService.data.put("P",order(1,"P",null,"260","2026-10-28T09:00:00"));
  s.customerOrderService.data.put("R1",order(2,"R1","P","1","2026-10-28T10:00:00"));
  s.customerOrderService.data.put("R2",order(3,"R2","P","259","2026-10-28T11:00:00"));
  s.customerOrderDetailService.data.put("P",Arrays.asList(detail("101","259"),detail("202","1")));
  s.customerOrderDetailService.data.put("R1",Arrays.asList(detail("202","1")));
  s.customerOrderDetailService.data.put("R2",Arrays.asList(detail("101","259")));
  return s;
 }
 static void require(boolean x,String message){if(!x)throw new AssertionError(message);}
 static void await(CountDownLatch l){try{require(l.await(4,TimeUnit.SECONDS),"latch timeout");}catch(InterruptedException e){throw new RuntimeException(e);}}
 public static void main(String[] a)throws Exception{
  init();
  ServiceUnderReview s=fixture();s.updateFlowRefundStatus("R1");ReplaceBatteryDeviceFlow f=mapper.selectById(1);
  require(f.getRefundDepositAmount().compareTo(BigDecimal.ONE)==0&&f.getRefundBusinessIncomeAmount().signum()==0,"subject error");
  System.out.println("PASS deposit only -> income 0 / deposit 1");
  s.updateFlowRefundStatus("R2");f=mapper.selectById(1);require(f.getRefundStatus()==DeviceFlowRefundStatusEnum.FULL_REFUND,"cumulative status not fixed");
  System.out.println("PASS cumulative 1+259 / paid260 -> FULL_REFUND");
  int version=f.getVersion();s.updateFlowRefundStatus("R2");require(mapper.selectById(1).getVersion()==version,"duplicate wrote again");
  System.out.println("PASS duplicate refund -> no new update");
  s=fixture();DeviceFlowSyncPageReq req=new DeviceFlowSyncPageReq();req.setPageSize(20);new JobUnderReview(s).refundFlowSync(req,0L);f=mapper.selectById(1);
  require(f.getRefundTime().equals(LocalDateTime.parse("2026-10-28T11:00:00")),"refund timestamp regressed");
  System.out.println("PASS R3: newest-first compensation R2,R1 -> refund_time 11:00, latest success 11:00");
  DeviceFlowScheduleReq params=new DeviceFlowScheduleReq();params.setSearchDate("{\"orderNo\":\"P\"}");
  require(new JobUnderReview(s).getOrderNosFromParams(params).isEmpty(),"legacy filter not lost");System.out.println("REPRO legacy orderNo -> [] (order restriction omitted)");
  s=fixture();s.customerOrderService.data.remove("R1");s.customerOrderService.data.remove("R2");s.customerOrderService.data.get("P").setTransactionAmount(new BigDecimal("100"));s.customerOrderDetailService.data.put("P",Arrays.asList(detail("101","100")));jdbc.update("UPDATE replace_battery_device_flow SET trade_amount=100 WHERE id=1");
  RefundApplyReq input=new RefundApplyReq();input.setOriginOrderNo("P");input.setPaySn("unique-refund-request");input.setSubType(3001);input.setRefundAmt(new BigDecimal("50"));RefundFeeDetailReq fee=new RefundFeeDetailReq();fee.setSubjectNo("101");fee.setAmount(new BigDecimal("100"));input.setFeeDetail(Arrays.asList(fee));
  boolean rejected=false;try{new SaveUnderReview(s).checkParams(input,s.customerOrderService.getByOrderNo("P"),new Result<CustomerOrderEntity>(ResultCode.SUCCESS1));}catch(SapsException e){rejected=true;}
  require(rejected,"mismatched main/detail accepted");System.out.println("PASS R1: refundAmt50/detail100 rejected by current checkParams");
  fee.setAmount(new BigDecimal("50"));require(new SaveUnderReview(s).checkParams(input,s.customerOrderService.getByOrderNo("P"),new Result<CustomerOrderEntity>(ResultCode.SUCCESS1)).isSuccess(),"valid request rejected");System.out.println("PASS valid refundAmt50/detail50 accepted");
  s=fixture();s.customerOrderService.data.put("OTHER",order(5,"OTHER","P","5","2026-10-28T12:00:00"));s.customerOrderDetailService.data.put("OTHER",Arrays.asList(detail("400","5")));s.updateFlowRefundStatus("OTHER");f=mapper.selectById(1);require(f.getRefundBusinessIncomeAmount().compareTo(new BigDecimal("5"))==0&&f.getRefundDepositAmount().signum()==0,"other subject mapping");System.out.println("PASS subject400 -> income5 / deposit0 per new convention");
  s=fixture();jdbc.update("UPDATE replace_battery_device_flow SET refund_order_no='R1,R2',refund_business_income_amount=259,refund_deposit_amount=1,refund_status=2,flow_status=50 WHERE id=1");s.updateFlowRefundStatus("R2");require(mapper.selectById(1).getRefundStatus()==DeviceFlowRefundStatusEnum.PARTIAL_REFUND,"historical state recalculated unexpectedly");System.out.println("REPRO historical full amount with PARTIAL status stays PARTIAL when replaying recorded refund");
  s=fixture();final ServiceUnderReview conflict=s;
  s.customerOrderDetailService.onRead=()->{ReplaceBatteryDeviceFlow other=mapper.selectById(1);other.setTenantArrivalAmount(new BigDecimal("9.99"));require(mapper.updateById(other)==1,"competing writer failed");};
  s.updateFlowRefundStatus("R1");f=mapper.selectById(1);require(f.getRefundDepositAmount().signum()==0&&f.getRefundOrderNo()==null&&f.getVersion()==2,"optimistic conflict not reproduced");
  System.out.println("REPRO concurrent non-refund update wins version; refund update affects0, method returns normally, DB refund remains0");
  s=fixture();final ServiceUnderReview parallel=s;ExecutorService threads=Executors.newFixedThreadPool(2);
  Future<?> one=threads.submit(()->parallel.updateFlowRefundStatus("R1"));Future<?> two=threads.submit(()->parallel.updateFlowRefundStatus("R2"));one.get();two.get();threads.shutdown();f=mapper.selectById(1);require(f.getRefundStatus()==DeviceFlowRefundStatusEnum.FULL_REFUND,"mutex-only concurrency failed");
  System.out.println("PASS two refund writers without outer transaction, same mutex -> both persisted");
  s=fixture();final ServiceUnderReview after=s;CountDownLatch released=new CountDownLatch(1),otherRead=new CountDownLatch(1);
  s.redissonLock.afterUnlock=()->{if(Thread.currentThread().getName().equals("callback")){released.countDown();await(otherRead);}};
  s.customerOrderDetailService.onRead=()->{if(Thread.currentThread().getName().equals("job"))otherRead.countDown();};
  ExecutorService workers=Executors.newFixedThreadPool(2);
  Future<?> t1=workers.submit(()->{Thread.currentThread().setName("callback");new TransactionTemplate(tm).execute(status->{mapper.selectById(1);TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){public void afterCommit(){after.updateFlowRefundStatus("R1");}});return null;});});
  Future<?> t2=workers.submit(()->{Thread.currentThread().setName("job");await(released);after.updateFlowRefundStatus("R2");});
  t1.get(8,TimeUnit.SECONDS);t2.get(8,TimeUnit.SECONDS);workers.shutdown();f=mapper.selectById(1);
  System.out.println("OBSERVE afterCommit + job with same mutex: recorded="+f.getRefundOrderNo()+", income="+f.getRefundBusinessIncomeAmount()+", deposit="+f.getRefundDepositAmount());
  require("R1".equals(f.getRefundOrderNo())&&f.getRefundBusinessIncomeAmount().signum()==0,"afterCommit race not reproduced");
  System.out.println("REPRO lock released before afterCommit JDBC cleanup commits; job reads stale version, refund R2 lost with update0");
  s=fixture();s.customerOrderDetailService.data.put("R2",Arrays.asList(detail("101","259")));s.updateFlowRefundStatus("R2");f=mapper.selectById(1);require(f.getRefundDepositAmount().signum()==0 && f.getRefundBusinessIncomeAmount().compareTo(new BigDecimal("259"))==0,"income-only wrong");System.out.println("PASS income-only refund does not increment deposit");
  s=fixture();s.customerOrderService.data.put("FULL",order(4,"FULL","P","260","2026-10-28T12:00:00"));s.customerOrderDetailService.data.put("FULL",Arrays.asList(detail("101","259"),detail("202","1")));s.updateFlowRefundStatus("FULL");require(mapper.selectById(1).getRefundStatus()==DeviceFlowRefundStatusEnum.FULL_REFUND,"one shot full refund wrong");System.out.println("PASS single full refund -> FULL_REFUND");
  s=fixture();DeviceFlowSyncPageReq small=new DeviceFlowSyncPageReq();small.setPageSize(1);new JobUnderReview(s).refundFlowSync(small,0L);require(mapper.selectById(1).getRefundTime().equals(LocalDateTime.parse("2026-10-28T11:00:00")),"cross page time wrong");System.out.println("PASS pageSize1 compensation retains max finishTime");
  JobUnderReview j=new JobUnderReview(s);DeviceFlowScheduleReq p=new DeviceFlowScheduleReq();p.setSearchDate("{\"orders\":[\" P \"]}");require(j.getOrderNosFromParams(p).equals(Arrays.asList("P")),"orders trim wrong");System.out.println("PASS orders parameter trims whitespace");
  for(String bad:Arrays.asList("oops","[]","{\"orders\":[\" \"]}")){p.setSearchDate(bad);boolean fail=false;try{j.getOrderNosFromParams(p);}catch(Exception e){fail=true;}require(fail,"invalid parameter accepted: "+bad);}System.out.println("PASS malformed / non-object / all-blank orders rejected");
  s=fixture();jdbc.update("UPDATE replace_battery_device_flow SET refund_order_no='R1,R2',refund_business_income_amount=518,refund_deposit_amount=2,refund_status=2,flow_status=50,refund_time='1970-10-27 09:03:53' WHERE id=1");s.updateFlowRefundStatus("R2");f=mapper.selectById(1);require(f.getRefundBusinessIncomeAmount().compareTo(new BigDecimal("518"))==0&&f.getRefundTime().getYear()==1970,"history corrected unexpectedly");System.out.println("REPRO historical polluted amounts and 1970 time unchanged on duplicate replay");
  require(new TimeUnderReview().parseFinishTime("19701027090353").getYear()==1970,"1970 parser changed");System.out.println("REPRO 1970 time accepted by unchanged callback parser");
 }
}
