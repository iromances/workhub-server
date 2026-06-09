package cn.aslight.workhub.dao.payment;

import cn.aslight.workhub.model.payment.PaymentMerchantCredentialEntity;
import cn.aslight.workhub.model.payment.PaymentMerchantCredentialResponse;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 商户敏感凭据数据访问接口。
 */
@Mapper
public interface PaymentMerchantCredentialMapper {

    @Select("""
            SELECT id,
                   credential_key AS credentialKey,
                   credential_name AS credentialName,
                   credential_type AS credentialType,
                   masked_value AS maskedValue,
                   fingerprint,
                   status,
                   remark,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM pay_merchant_credential
            WHERE merchant_id = #{merchantId}
            ORDER BY credential_key ASC, id ASC
            """)
    List<PaymentMerchantCredentialResponse> findByMerchantId(Long merchantId);

    @Select("""
            SELECT id,
                   merchant_id,
                   credential_key,
                   credential_name,
                   credential_type,
                   encrypted_value,
                   masked_value,
                   fingerprint,
                   status,
                   remark
            FROM pay_merchant_credential
            WHERE id = #{id}
            """)
    PaymentMerchantCredentialEntity findEntityById(Long id);

    @Select("""
            SELECT id,
                   merchant_id,
                   credential_key,
                   credential_name,
                   credential_type,
                   encrypted_value,
                   masked_value,
                   fingerprint,
                   status,
                   remark
            FROM pay_merchant_credential
            WHERE merchant_id = #{merchantId}
              AND credential_key = #{credentialKey}
            """)
    PaymentMerchantCredentialEntity findEntityByMerchantIdAndKey(@Param("merchantId") Long merchantId,
                                                                 @Param("credentialKey") String credentialKey);

    @Insert("""
            INSERT INTO pay_merchant_credential (
                merchant_id,
                credential_key,
                credential_name,
                credential_type,
                encrypted_value,
                masked_value,
                fingerprint,
                status,
                remark
            ) VALUES (
                #{merchantId},
                #{credentialKey},
                #{credentialName},
                #{credentialType},
                #{encryptedValue},
                #{maskedValue},
                #{fingerprint},
                #{status},
                #{remark}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(PaymentMerchantCredentialEntity entity);

    @Update("""
            UPDATE pay_merchant_credential
            SET credential_name = #{credentialName},
                credential_type = #{credentialType},
                encrypted_value = #{encryptedValue},
                masked_value = #{maskedValue},
                fingerprint = #{fingerprint},
                status = #{status},
                remark = #{remark}
            WHERE id = #{id}
            """)
    int update(PaymentMerchantCredentialEntity entity);
}
