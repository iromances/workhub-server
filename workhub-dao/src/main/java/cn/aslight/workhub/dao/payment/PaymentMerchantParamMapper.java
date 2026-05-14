package cn.aslight.workhub.dao.payment;

import cn.aslight.workhub.model.payment.PaymentMerchantParamEntity;
import cn.aslight.workhub.model.payment.PaymentMerchantParamResponse;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 商户参数数据访问接口。
 */
@Mapper
public interface PaymentMerchantParamMapper {

    @Select("""
            SELECT id,
                   param_key AS paramKey,
                   value_type AS valueType,
                   sensitive_flag AS `sensitive`,
                   CASE WHEN sensitive_flag = 1 THEN masked_value ELSE plain_value END AS displayValue,
                   remark,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM pay_merchant_param
            WHERE merchant_id = #{merchantId}
            ORDER BY id ASC
            """)
    List<PaymentMerchantParamResponse> findByMerchantId(Long merchantId);

    @Select("""
            SELECT id,
                   merchant_id,
                   param_key,
                   value_type,
                   sensitive_flag,
                   plain_value,
                   encrypted_value,
                   masked_value,
                   remark
            FROM pay_merchant_param
            WHERE id = #{id}
            """)
    PaymentMerchantParamEntity findEntityById(Long id);

    @Select("""
            SELECT id,
                   merchant_id,
                   param_key,
                   value_type,
                   sensitive_flag,
                   plain_value,
                   encrypted_value,
                   masked_value,
                   remark
            FROM pay_merchant_param
            WHERE merchant_id = #{merchantId}
              AND param_key = #{paramKey}
            """)
    PaymentMerchantParamEntity findEntityByMerchantIdAndKey(@Param("merchantId") Long merchantId,
                                                            @Param("paramKey") String paramKey);

    @Insert("""
            INSERT INTO pay_merchant_param (
                merchant_id,
                param_key,
                value_type,
                sensitive_flag,
                plain_value,
                encrypted_value,
                masked_value,
                remark
            ) VALUES (
                #{merchantId},
                #{paramKey},
                #{valueType},
                #{sensitiveFlag},
                #{plainValue},
                #{encryptedValue},
                #{maskedValue},
                #{remark}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(PaymentMerchantParamEntity entity);

    @Update("""
            UPDATE pay_merchant_param
            SET value_type = #{valueType},
                sensitive_flag = #{sensitiveFlag},
                plain_value = #{plainValue},
                encrypted_value = #{encryptedValue},
                masked_value = #{maskedValue},
                remark = #{remark}
            WHERE id = #{id}
            """)
    int update(PaymentMerchantParamEntity entity);
}
