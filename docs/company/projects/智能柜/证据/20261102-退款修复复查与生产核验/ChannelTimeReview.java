import java.time.*;
import java.time.format.*;
import java.util.*;
import java.lang.reflect.*;
import cn.hutool.core.util.StrUtil;
import org.slf4j.*;
import com.alibaba.fastjson.*;
import com.intf.paychannel.adapter.allinpay.service.RefundNotifyService;
import com.intf.paychannel.adapter.allinpay.model.pay.AllinpayQueryPayResponse;
import com.intf.paychannel.adapter.allinpay.support.convter.CommonResponseConverter;
import com.intf.paychannel.adapter.api.cousumer.xxtPayApiService;
import com.intf.paychannel.model.payment.UnifyRefundOrderQueryResponseDTO;
import com.intf.paychannel.model.notify.*;
import com.lease.common.core.Result;

// Actual compiled channel RefundNotifyService, only Feign client replaced by an in-memory proxy.
// The two downstream parse methods are extracted verbatim from the pinned xxt-pay and SAPS commits.
public class ChannelTimeReview {
 static Logger log=LoggerFactory.getLogger(ChannelTimeReview.class);
 static class XxtParser {
  static final DateTimeFormatter DT_FORMATTER=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
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
 static class SapsParser {
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
 static List<ChannelNotifyResponseDTO> calls=new ArrayList<>();
 static RefundNotifyService service;
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 static ChannelNotifyResponseDTO notify(String paytime,String chnl,String status)throws Exception {
  calls.clear(); JSONObject body=new JSONObject();body.put("trxstatus",status);body.put("trxid","synthetic-channel");body.put("cusorderid","synthetic-refund");body.put("trxamt","100");body.put("paytime",paytime);body.put("chnlfintime",chnl);
  ChannelNotifyRequestDTO request=new ChannelNotifyRequestDTO();request.setBody(body.toJSONString());
  return service.handleNotify(request);
 }
 static LocalDateTime downstream(ChannelNotifyResponseDTO result){LocalDateTime t=new XxtParser().parseFinishTime(result.getCompleteTime());return new SapsParser().parseFinishTime(t.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));}
 public static void main(String[] args)throws Exception {
  service=new RefundNotifyService();Field f=RefundNotifyService.class.getDeclaredField("xxtPayApiService");f.setAccessible(true);
  f.set(service,Proxy.newProxyInstance(xxtPayApiService.class.getClassLoader(),new Class[]{xxtPayApiService.class},(proxy,m,a)->{if(m.getName().equals("handleNotifyRefund"))calls.add((ChannelNotifyResponseDTO)a[0]);return new Result<Object>();}));
  ChannelNotifyResponseDTO r=notify("20261027114449","19701027090353","0000");check(calls.size()==1,"not notified");check("2026-10-27 11:44:49".equals(r.getCompleteTime()),"paytime not preferred");check(downstream(r).equals(LocalDateTime.parse("2026-10-27T11:44:49")),"downstream changes time");System.out.println("PASS current paytime + 1970 chnlfintime -> 2026-10-27 11:44:49; xxt and SAPS parsers preserve value");
  r=notify("20261027114449",null,"0000");check(calls.size()==1&&downstream(r).getYear()==2026,"optional channel time failure");System.out.println("PASS valid paytime + absent chnlfintime -> normal finish time");
  r=notify("20261027114449","19701027090353","9999");check(calls.size()==1&&r.getStatus()==3&&downstream(r).getYear()==2026,"failed branch wrong");System.out.println("PASS failed refund notification also prefers paytime");
  r=notify("", "19701027090353","0000");check(calls.size()==1&&downstream(r).getYear()==1970,"fallback no longer accepts1970");System.out.println("GAP malformed/missing required paytime: fallback 1970 survives channel -> xxt -> SAPS parsers");
  r=notify("19701027090353","20261027114449","0000");check(calls.size()==1&&downstream(r).getYear()==1970,"paytime validated");System.out.println("GAP parseable but implausible paytime1970 wins over valid channel time");
  r=notify("BAD-TIME","20261027114449","0000");check(calls.isEmpty(),"invalid paytime unexpectedly sent");System.out.println("GAP nonblank malformed paytime: no fallback, no business notification (synthetic invalid channel input)");
  r=notify(null,null,"0000");check(calls.size()==1&&r.getCompleteTime()==null&&downstream(r).getYear()==LocalDateTime.now().getYear(),"empty path changed");System.out.println("GAP both times absent: xxt substitutes local now as finish time");
  AllinpayQueryPayResponse query=new AllinpayQueryPayResponse();query.setRetcode("SUCCESS");query.setTrxstatus("0000");query.setFintime("20261027114449");UnifyRefundOrderQueryResponseDTO q=CommonResponseConverter.buildCommonResponse(query,new UnifyRefundOrderQueryResponseDTO());check("2026-10-27 11:44:49".equals(q.getCompleteTime()),"query conversion wrong");System.out.println("PASS refund query common converter uses fintime yyyyMMddHHmmss");
 }
}
