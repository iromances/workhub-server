package com.intf.paychannel.adapter.bcm.service;

import com.alibaba.fastjson.JSON;
import com.lease.common.core.Result;
import com.lease.common.core.ResultCode;
import com.lease.common.util.DateUtils;
import com.intf.paychannel.common.enums.ChannelEnum;
import com.intf.paychannel.common.enums.ProductTypeEnum;
import com.intf.paychannel.common.exception.BizException;
import com.intf.paychannel.common.exception.BizExceptionEnum;
import com.intf.paychannel.common.util.XmlChangeUtils;
import com.intf.paychannel.adapter.bcm.model.req.TransContentBcm;
import com.intf.paychannel.adapter.bcm.model.req.TransHeadBcm;
import com.intf.paychannel.adapter.bcm.model.req.TransReqBCM310201;
import com.intf.paychannel.adapter.bcm.model.rsp.TransRespBCM310201;
import com.intf.paychannel.adapter.bcm.support.BcmApiConfigSupport;
import com.intf.paychannel.model.dto.bcm.AccountDetailQueryVo;
import com.intf.paychannel.model.dto.bcm.BcmDetailDto;
import com.intf.paychannel.model.dto.bcm.BcmDetailResultDto;
import com.intf.paychannel.model.entity.ChannelAccessConfigEntity;
import com.intf.paychannel.model.entity.ChannelApiConfigEntity;
import com.intf.paychannel.model.enums.BcmTrCodeEnum;
import com.intf.paychannel.adapter.bcm.support.BcmDirectResponseParser;
import com.intf.paychannel.common.bcm.BcmToolUtils;
import com.intf.paychannel.service.ChannelAccessConfigService;
import com.intf.paychannel.service.ChannelApiConfigService;
import com.intf.paychannel.service.bcm.BcmInterfaceServer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component
public class BcmEventServer extends BcmServer implements BcmInterfaceServer {

    @Autowired
    private ChannelAccessConfigService channelAccessConfigService;
    @Autowired
    private ChannelApiConfigService channelApiConfigService;

    @Override
    public Result<BcmDetailResultDto> queryDayDetail(AccountDetailQueryVo queryVo) {
        log.info("查询当日交易明细参数：{}", JSON.toJSONString(queryVo));
        Result<BcmDetailResultDto> result = new Result<>();
        result.setCode(ResultCode.INTERNAL_SERVER_ERROR.getCode());
        if (StringUtils.isBlank(queryVo.getAcctNo())) {
            result.setCode(ResultCode.PARAM_PARAMETER_ERROR.getCode());
            result.setMessage("查询当日交易明细必填参数缺失，请核实！");
            return result;
        }

        String today = DateUtils.format(new Date(), DateUtils.YYYYMMDD);
        String startDate = normalizeDate(queryVo.getStartDate());
        String endDate = normalizeDate(queryVo.getEndDate());
        if (StringUtils.isNotEmpty(startDate) && StringUtils.isNotEmpty(endDate) && !today.equals(endDate)) {
            return queryHistoryDetail(queryVo);
        }

        return queryDetails(resolveAccessConfig(queryVo), queryVo.getAcctNo(), null, null, BcmTrCodeEnum.DAY_PAYMENT_DETAIL, "当日", result);
    }

    @Override
    public Result<BcmDetailResultDto> queryHistoryDetail(AccountDetailQueryVo queryVo) {
        queryVo.setStartDate(normalizeDate(queryVo.getStartDate()));
        queryVo.setEndDate(normalizeDate(queryVo.getEndDate()));
        log.info("查询历史交易明细参数：{}", JSON.toJSONString(queryVo));
        Result<BcmDetailResultDto> result = new Result<>();
        result.setCode(ResultCode.INTERNAL_SERVER_ERROR.getCode());
        if (StringUtils.isAnyBlank(queryVo.getAcctNo(), queryVo.getStartDate(), queryVo.getEndDate())) {
            result.setCode(ResultCode.PARAM_PARAMETER_ERROR.getCode());
            result.setMessage("必填参数缺失，请核实！");
            return result;
        }

        return queryDetails(resolveAccessConfig(queryVo), queryVo.getAcctNo(), queryVo.getStartDate(), queryVo.getEndDate(),
                BcmTrCodeEnum.HISTORY_PAYMENT_DETAIL, "历史", result);
    }

    private Result<BcmDetailResultDto> queryDetails(ChannelAccessConfigEntity accessConfig,
                                                    String acctNo, String startDate, String endDate,
                                                    BcmTrCodeEnum trCode, String queryDesc,
                                                    Result<BcmDetailResultDto> result) {
        TransContentBcm<TransReqBCM310201> transContent = new TransContentBcm<>();
        TransReqBCM310201 reqData = new TransReqBCM310201();
        reqData.setAcno(acctNo);
        reqData.setStart_date(startDate);
        reqData.setEnd_date(endDate);
        transContent.setBody(reqData);

        TransHeadBcm head = buildTransHeadBcm(trCode.getCode(), accessConfig);
        transContent.setHead(head);
        String requestXml = transContent.obj2Str(transContent);
        try {
            String address = buildDirectAddress(accessConfig,trCode.getCode());
            log.info("请求交行{}交易明细参数:{}", queryDesc, JSON.toJSONString(reqData));
            String responseXml = BcmToolUtils.httpRequestDirect(requestXml, address);
            log.info("请求交行{}交易明细返回结果：{}", queryDesc, responseXml);
            TransRespBCM310201 response = XmlChangeUtils.convertToJavaBean(responseXml, TransRespBCM310201.class);
            if (response == null || response.getHead() == null) {
                throw new BizException(BizExceptionEnum.API_INVOKE_ERROR, "交通银行交易明细返回报文解析失败");
            }

            String returnCode = response.getHead().getParticular_code();
            String returnMsg = response.getHead().getParticular_info();
            log.info("请求交行{}交易明细返回状态：{}，描述：{}", queryDesc, returnCode, returnMsg);
            if (!"0000".equals(returnCode)) {
                result.setCode(ResultCode.FAIL.getCode());
                result.setMessage("查询" + queryDesc + "交易明细失败，渠道返回码["
                        + StringUtils.defaultIfBlank(returnCode, "UNKNOWN") + "]："
                        + StringUtils.defaultIfBlank(returnMsg, "渠道未返回错误描述"));
                return result;
            }

            // 查无流水是正常结果：返回码正常时不论有没有报文体，都按成功 + 空列表返回
            List<BcmDetailDto> details = response.getBody() == null
                    ? Collections.emptyList()
                    : BcmDirectResponseParser.parseDetailRecords(response.getBody().getSerial_record(),
                            response.getBody().getRecord_num(), response.getBody().getField_num());

            BcmDetailResultDto resultDto = new BcmDetailResultDto();
            resultDto.setDetailLists(details);
            result.setData(resultDto);
            result.setCode(ResultCode.SUCCESS.getCode());
            result.setMessage("查询" + queryDesc + "明细成功！");
            return result;
        } catch (Exception e) {
            log.error("交行查询{}交易明细异常", queryDesc, e);
            throw new BizException(BizExceptionEnum.API_INVOKE_ERROR);
        }
    }

    private String normalizeDate(String date) {
        if (StringUtils.isBlank(date)) {
            return date;
        }
        return StringUtils.contains(date, "-") ? StringUtils.replace(date, "-", "") : date;
    }

    private String buildDirectAddress(ChannelAccessConfigEntity accessConfig,String trCode) {
        ChannelApiConfigEntity apiConfig = resolveApiConfig();
        String requestUrl = BcmApiConfigSupport.resolveApiAddress(apiConfig, trCode);
        if (StringUtils.isNotBlank(requestUrl)) {
            return requestUrl;
        }
        throw new BizException(BizExceptionEnum.BUSINESS_EXCEPTION, "交通银行渠道请求地址配置缺失");
    }

    private TransHeadBcm buildTransHeadBcm(String trCode, ChannelAccessConfigEntity accessConfig) {
        TransHeadBcm head = new TransHeadBcm();
        head.setAtom_tr_count("1");
        head.setChannel("0");
        head.setCorp_no(resolveRequiredField(accessConfig == null ? null : accessConfig.getCorpNo()));
        head.setUser_no(resolveRequiredField(accessConfig == null ? null : accessConfig.getUserNo()));
        head.setReq_no(createRequestNo(trCode));
        head.setTr_acdt(DateUtils.format(new Date(), DateUtils.YYYYMMDD));
        head.setTr_time(DateUtils.format(new Date(), "HHmmss"));
        head.setTr_code(trCode);
        head.setReserved("");
        return head;
    }

    private ChannelAccessConfigEntity resolveAccessConfig(AccountDetailQueryVo queryVo) {
        return channelAccessConfigService.getActiveConfig(
                queryVo.getChannel() != null ? queryVo.getChannel() : ChannelEnum.BCM.getValue(),
                queryVo.getMerchantNo());
    }

    private ChannelApiConfigEntity resolveApiConfig() {
        try {
            return channelApiConfigService.getActiveConfig(ChannelEnum.BCM.getCode());
        } catch (Exception ignore) {
            return null;
        }
    }

    private String resolveRequiredField(String value) {
        if (StringUtils.isBlank(value)) {
            throw new BizException(BizExceptionEnum.BUSINESS_EXCEPTION, "交通银行渠道接入配置缺失");
        }
        return value;
    }

    private String createRequestNo(String seed) {
        String prefix = DateUtils.format(new Date(), "yyMMddHHmmss");
        if (StringUtils.isBlank(seed)) {
            return prefix + ThreadLocalRandom.current().nextInt(100000, 1000000);
        }
        return prefix + String.format("%06d", Math.abs(seed.hashCode()) % 1000000);
    }
}
