package com.intf.paychannel.adapter.allinpay.support.convter;

import com.intf.paychannel.adapter.allinpay.model.BaseAllinpayResponse;
import com.intf.paychannel.adapter.allinpay.model.pay.AllinpayProfitSharingResponse;
import com.intf.paychannel.adapter.allinpay.support.AllinpayResponseSupport;
import com.intf.paychannel.common.enums.PaymentStatusEnum;
import com.intf.paychannel.model.payment.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.formula.functions.T;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 通联分账响应转换器。
 */
public  class CommonResponseConverter {


    /**
     * 构建标准申请响应（填充已有DTO）
     */
    public static <T extends BaseAllinpayResponse, R extends BaseResponseDTO> R buildCommonResponse(
            T response, R dto) {
        boolean successFlag = AllinpayResponseSupport.getSuccessFaileRetCode(response);

        // 默认处理中
        dto.setStatus(PaymentStatusEnum.PROCESSING.getValue());

        if (successFlag) {
            String trxstatus = response.getTrxstatus();
            // 成功
            if (AllinpayResponseSupport.SUCCESS_TXN_STATUS.contains(trxstatus)) {
                dto.setStatus(PaymentStatusEnum.SUCCESS.getValue());
                dto.setRespCode(trxstatus);
                if(StringUtils.isNotEmpty(response.getFintime())){
                    DateTimeFormatter input = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
                    DateTimeFormatter output = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                    String format = LocalDateTime.parse(response.getFintime(), input).format(output);
                    dto.setCompleteTime(format);
                }
            } else if (AllinpayResponseSupport.PROCESSING_TXN_STATUS.contains(trxstatus)) {
                // 保持 PROCESSING
            } else {
                // 失败
                dto.setStatus(PaymentStatusEnum.FAILED.getValue());
                dto.setRespCode(trxstatus);
                dto.setRespMsg(response.getErrmsg());
            }
        } else {
            // 失败
            dto.setStatus(PaymentStatusEnum.FAILED.getValue());
            dto.setRespCode(response.getRetcode());
            dto.setRespMsg(response.getRetmsg());
        }
        return dto;
    }


}
