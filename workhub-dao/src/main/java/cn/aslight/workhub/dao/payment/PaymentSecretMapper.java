package cn.aslight.workhub.dao.payment;

import cn.aslight.workhub.model.payment.PaymentSecretEntity;
import cn.aslight.workhub.model.payment.PaymentSecretSummaryResponse;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 商户秘钥数据访问接口。
 */
@Mapper
public interface PaymentSecretMapper {

    @Select("""
            SELECT id,
                   secret_name AS secretName,
                   secret_type AS secretType,
                   version_no AS versionNo,
                   masked_value AS maskedValue,
                   fingerprint,
                   algorithm,
                   status,
                   valid_from AS validFrom,
                   valid_to AS validTo,
                   remark,
                   created_at AS createdAt
            FROM pay_merchant_secret
            WHERE merchant_id = #{merchantId}
            ORDER BY secret_name ASC, version_no DESC
            """)
    List<PaymentSecretSummaryResponse> findByMerchantId(Long merchantId);

    @Select("""
            SELECT COALESCE(MAX(version_no), 0)
            FROM pay_merchant_secret
            WHERE merchant_id = #{merchantId}
              AND secret_name = #{secretName}
            """)
    int findMaxVersion(@Param("merchantId") Long merchantId, @Param("secretName") String secretName);

    @Update("""
            UPDATE pay_merchant_secret
            SET status = 'INACTIVE'
            WHERE merchant_id = #{merchantId}
              AND secret_name = #{secretName}
              AND status = 'ACTIVE'
            """)
    int deactivateActiveVersions(@Param("merchantId") Long merchantId, @Param("secretName") String secretName);

    @Insert("""
            INSERT INTO pay_merchant_secret (
                merchant_id,
                secret_name,
                secret_type,
                encrypted_value,
                masked_value,
                fingerprint,
                algorithm,
                version_no,
                status,
                valid_from,
                valid_to,
                remark
            ) VALUES (
                #{merchantId},
                #{secretName},
                #{secretType},
                #{encryptedValue},
                #{maskedValue},
                #{fingerprint},
                #{algorithm},
                #{versionNo},
                #{status},
                #{validFrom},
                #{validTo},
                #{remark}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(PaymentSecretEntity entity);
}
