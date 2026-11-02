package com.intf.paychannel.adapter.allinpay.service;

import com.intf.paychannel.adapter.allinpay.model.pay.*;
import com.intf.paychannel.adapter.allinpay.support.convter.req.AllinpayUnifyRefundRequestConverter;
import com.intf.paychannel.adapter.allinpay.support.convter.AllinpayUnifyRefundResponseConverter;
import com.intf.paychannel.adapter.allinpay.support.convter.req.AllinpayUnifyPaymentRequestConverter;
import com.intf.paychannel.adapter.allinpay.support.convter.AllinpayUnifyPaymentResponseConverter;
import com.intf.paychannel.common.enums.ProductTypeEnum;
import com.intf.paychannel.common.exception.BizException;
import com.intf.paychannel.common.exception.BizExceptionEnum;
import com.intf.paychannel.model.entity.ChannelAccessConfigEntity;
import com.intf.paychannel.model.entity.ChannelApiConfigEntity;
import com.intf.paychannel.model.payment.*;
import com.intf.paychannel.model.refund.BalanceTransfeResponse;
import com.intf.paychannel.model.refund.BalanceTransferquestDTO;
import com.intf.paychannel.model.refund.RefundRequestDTO;
import com.intf.paychannel.model.refund.RefundResponseDTO;
import org.springframework.stereotype.Service;

/**
 * 通联聚合支付服务。
 */
@Service
public class AllinpayUnifyPaymentService extends AllinpayServer {

    /**
     * 聚合支付申请。
     */
    public PaymentResponseDTO applyPayment(UnifiedPaymentRequestDTO request, ChannelAccessConfigEntity accessConfig) {
        AllinpayUnifyPayRequest allinpayRequest = AllinpayUnifyPaymentRequestConverter
                .buildPayApplyRequest(request, accessConfig);

        ChannelApiConfigEntity apiConfig = requireApiConfig(accessConfig,  ProductTypeEnum.UNIFY_PAYMENT);
        allinpayRequest.setNotify_url(apiConfig.getCallbackUrl());

        AllinpayUnifyPayResponse response = execute(request.getOrderId(), accessConfig, ProductTypeEnum.UNIFY_PAYMENT,
                allinpayRequest, AllinpayUnifyPayResponse.class);
        return AllinpayUnifyPaymentResponseConverter.buildPayApplyResponse(request, response);
    }

    /**
     * 聚合支付查单。
     */
    @SuppressWarnings("all")
    public UnifiedOrderQueryResponseDTO queryPayment(PaymentQueryRequestDTO request,
                                                     ChannelAccessConfigEntity accessConfig) {
        AllinUnifyPayQueryRequest allinpayRequest = AllinpayUnifyPaymentRequestConverter
                .buildPayQueryRequest(request, accessConfig);
        String serialNo = request.getOrderId();
        AllinpayQueryPayResponse response = execute(serialNo, accessConfig, ProductTypeEnum.UNIFY_PAYMENT_QUERY,
                allinpayRequest, AllinpayQueryPayResponse.class);
        return AllinpayUnifyPaymentResponseConverter.buildPayQueryResponse(request, response);
    }


    /**
     * 聚合退款查单。--退款和支付查单接口一样，路由到queryPayment
     */
    @SuppressWarnings("all")
    public UnifyRefundOrderQueryResponseDTO queryRefund(PaymentQueryRequestDTO request,
                                                        ChannelAccessConfigEntity accessConfig) {
        UnifiedOrderQueryResponseDTO unifiedOrderQueryResponseDTO = queryPayment(request, accessConfig);
        UnifyRefundOrderQueryResponseDTO unifyRefundOrderQueryResponseDTO = new UnifyRefundOrderQueryResponseDTO();
        unifyRefundOrderQueryResponseDTO.setStatus(unifiedOrderQueryResponseDTO.getStatus());
        unifyRefundOrderQueryResponseDTO.setOrderId(unifiedOrderQueryResponseDTO.getOrderId());
        unifyRefundOrderQueryResponseDTO.setChannelSn(unifiedOrderQueryResponseDTO.getChannelSn());
        unifyRefundOrderQueryResponseDTO.setCompleteTime(unifiedOrderQueryResponseDTO.getCompleteTime());
        unifyRefundOrderQueryResponseDTO.setRespCode(unifiedOrderQueryResponseDTO.getRespCode());
        unifyRefundOrderQueryResponseDTO.setRespMsg(unifiedOrderQueryResponseDTO.getRespMsg());
        return unifyRefundOrderQueryResponseDTO;
    }


    /**
     * 发起退款。
     */
    public RefundResponseDTO applyRefund(RefundRequestDTO request, ChannelAccessConfigEntity accessConfig) {
        AllinpayRefundPayRequest allinpayRequest = AllinpayUnifyRefundRequestConverter
                .buildRefundApplyRequest(request, accessConfig);

        ChannelApiConfigEntity apiConfig = requireApiConfig(accessConfig,  ProductTypeEnum.REFUND);
        allinpayRequest.setNotify_url(apiConfig.getCallbackUrl());

        AllinpayRefundPayResponse response = execute(request.getOrderId(), accessConfig, ProductTypeEnum.REFUND,
                allinpayRequest, AllinpayRefundPayResponse.class);
        return AllinpayUnifyRefundResponseConverter.buildRefundApplyResponse(request, response);
    }

    public BalanceTransfeResponse applyTransferUnify(BalanceTransferquestDTO request, ChannelAccessConfigEntity accessConfig){
        AllinBalanceTransferApiRequest allinpayRequest = AllinpayUnifyRefundRequestConverter
                .buildTransferApplyRequest(request, accessConfig);
        AllinpayQueryPayResponse response = execute(request.getOrderId(), accessConfig, ProductTypeEnum.BALLANCE_TRANSFER,
                allinpayRequest, AllinpayQueryPayResponse.class);
        return AllinpayUnifyRefundResponseConverter.buildTransferApplyResponse(request, response);
    }

    public  BalanceTransfeResponse querytransfer(BalanceTransferquestDTO request, ChannelAccessConfigEntity accessConfig){
        AllinBalanceTransferQueryRequest allinpayRequest = AllinpayUnifyRefundRequestConverter
                .buildTransferQueryRequest(request, accessConfig);
        AllinpayQueryPayResponse response = execute(request.getOrderId(), accessConfig, ProductTypeEnum.BALLANCE_TRANSFER_QUERY,
                allinpayRequest, AllinpayQueryPayResponse.class);
        return AllinpayUnifyRefundResponseConverter.buildTransferQueryResponse(request, response);
    }

    /**
     * 聚合支付取消。
     */
    public PaymentResponseDTO cancelPayment(PaymentCancelRequestDTO request, ChannelAccessConfigEntity accessConfig) {
        throw new BizException(BizExceptionEnum.BUSINESS_EXCEPTION, "当前平台暂不支持撤销申请");
    }
}
