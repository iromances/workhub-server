package cn.aslight.workhub.dao.payment;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 支付配置操作日志接口。
 */
@Mapper
public interface PaymentOperationLogMapper {

    @Insert("""
            INSERT INTO pay_operation_log (
                biz_type,
                biz_id,
                action_type,
                action_summary,
                detail_text,
                operator_user_name
            ) VALUES (
                #{bizType},
                #{bizId},
                #{actionType},
                #{actionSummary},
                #{detailText},
                #{operatorUserName}
            )
            """)
    int insert(@Param("bizType") String bizType,
               @Param("bizId") Long bizId,
               @Param("actionType") String actionType,
               @Param("actionSummary") String actionSummary,
               @Param("detailText") String detailText,
               @Param("operatorUserName") String operatorUserName);
}
