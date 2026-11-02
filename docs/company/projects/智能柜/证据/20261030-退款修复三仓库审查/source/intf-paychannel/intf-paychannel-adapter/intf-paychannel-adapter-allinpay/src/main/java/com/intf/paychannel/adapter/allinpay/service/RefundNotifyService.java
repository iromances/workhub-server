package com.intf.paychannel.adapter.allinpay.service;

import com.alibaba.fastjson.JSON;
import com.intf.paychannel.adapter.allinpay.model.BaseAllinpayRequest;
import com.intf.paychannel.adapter.allinpay.model.pay.AllinpayNotifyResponse;
import com.intf.paychannel.adapter.allinpay.support.AllinpayRequestSigner;
import com.intf.paychannel.adapter.allinpay.support.AllinpayResponseSupport;
import com.intf.paychannel.adapter.api.cousumer.xxtPayApiService;
import com.intf.paychannel.common.enums.ChannelEnum;
import com.intf.paychannel.common.enums.PaymentStatusEnum;
import com.intf.paychannel.common.enums.ProductTypeEnum;
import com.intf.paychannel.common.exception.BizException;
import com.intf.paychannel.common.exception.BizExceptionEnum;
import com.intf.paychannel.common.util.MoneyUtils;
import com.intf.paychannel.model.entity.ChannelAccessConfigEntity;
import com.intf.paychannel.model.notify.AgreePaymentNotifyResultDTO;
import com.intf.paychannel.model.notify.ChannelNotifyRequestDTO;
import com.intf.paychannel.model.notify.ChannelNotifyResponseDTO;
import com.intf.paychannel.model.notify.UnifyPaymentNotifyResultDTO;
import com.intf.paychannel.service.ChannelAccessConfigService;
import com.lease.common.core.Result;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 通联异步通知服务。
 */
@Service
@Slf4j
public class RefundNotifyService {


    @Autowired
    private xxtPayApiService xxtPayApiService;



    /**
     * 处理通联异步通知。
     *
     * @param request 通知请求
     * @return 通知响应
     */
    @SuppressWarnings("all")
    public ChannelNotifyResponseDTO handleNotify(ChannelNotifyRequestDTO request) {

        ChannelNotifyResponseDTO channelNotifyResponseDTO = new ChannelNotifyResponseDTO();
        try {
            AllinpayNotifyResponse notifyResponse = AllinpayRequestSigner.parseResponse(
                    request.getBody(), AllinpayNotifyResponse.class);
            if (notifyResponse == null ) {
                throw new BizException(BizExceptionEnum.BUSINESS_EXCEPTION,"通知报文异常" );
            }

            String trxstatus = notifyResponse.getTrxstatus();
            // 退款完成时间优先取 paytime，chnlfintime 仅兜底
            String finishTime = StringUtils.defaultIfBlank(notifyResponse.getPaytime(), notifyResponse.getChnlfintime());

            if(AllinpayResponseSupport.SUCCESS_TXN_STATUS.contains(trxstatus)){
                channelNotifyResponseDTO.setStatus(PaymentStatusEnum.SUCCESS.getValue());
                channelNotifyResponseDTO.setRespCode(trxstatus);
                channelNotifyResponseDTO.setChannelSn(notifyResponse.getTrxid());
                channelNotifyResponseDTO.setSubChannelSn(notifyResponse.getChnltrxid());
                channelNotifyResponseDTO.setOrderId(notifyResponse.getCusorderid());
                channelNotifyResponseDTO.setSuccAmt(MoneyUtils.convertToYuan(notifyResponse.getTrxamt()));
                if(StringUtils.isNotEmpty(finishTime)){
                    DateTimeFormatter input = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
                    DateTimeFormatter output = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                    String format = LocalDateTime.parse(finishTime, input).format(output);
                    channelNotifyResponseDTO.setCompleteTime(format);
                }

            }else if(AllinpayResponseSupport.SUCCESS_TXN_STATUS.contains(trxstatus)){
                channelNotifyResponseDTO.setStatus(PaymentStatusEnum.PROCESSING.getValue());
            }else {
                channelNotifyResponseDTO.setStatus(PaymentStatusEnum.FAILED.getValue());
                channelNotifyResponseDTO.setRespCode(trxstatus);
                channelNotifyResponseDTO.setRespMsg(notifyResponse.getErrmsg());
                channelNotifyResponseDTO.setChannelSn(notifyResponse.getTrxid());
                channelNotifyResponseDTO.setSubChannelSn(notifyResponse.getChnltrxid());
                channelNotifyResponseDTO.setOrderId(notifyResponse.getCusorderid());
                if(StringUtils.isNotEmpty(finishTime)){
                    DateTimeFormatter input = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
                    DateTimeFormatter output = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                    String format = LocalDateTime.parse(finishTime, input).format(output);
                    channelNotifyResponseDTO.setCompleteTime(format);
                }
            }

            notifyBusiness(request,channelNotifyResponseDTO);

        } catch (Exception ex) {
            log.error("通联退款异步通知处理异常", ex);
        }
        return channelNotifyResponseDTO;

    }

    private void notifyBusiness(ChannelNotifyRequestDTO request, ChannelNotifyResponseDTO channelNotifyResponseDTO) {
        try {
            log.info("通联退款业务通知:{}", JSON.toJSONString(channelNotifyResponseDTO));
            channelNotifyResponseDTO.setChannel(5);
            Result<?> result = xxtPayApiService.handleNotifyRefund(channelNotifyResponseDTO);
            log.info("通联退款业务通知结果:{},{}",channelNotifyResponseDTO.getOrderId(), JSON.toJSONString(result));
        }catch (Exception e){
            log.error("通联退款异步通知处理异常:{}", e);
        }
    }


}
