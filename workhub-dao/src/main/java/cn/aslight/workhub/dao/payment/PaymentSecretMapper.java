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
	                   source_type AS sourceType,
	                   file_name AS fileName,
	                   file_content_type AS fileContentType,
	                   file_value_type AS fileValueType,
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
	            SELECT id,
	                   merchant_id,
	                   secret_name,
	                   secret_type,
	                   encrypted_value,
	                   masked_value,
	                   fingerprint,
	                   algorithm,
	                   source_type,
	                   file_name,
	                   file_content_type,
	                   file_value_type,
	                   version_no,
	                   status,
	                   valid_from,
	                   valid_to,
	                   remark
	            FROM pay_merchant_secret
	            WHERE id = #{id}
	            """)
	    PaymentSecretEntity findEntityById(Long id);

    @Select("""
            SELECT id,
                   merchant_id,
                   secret_name,
                   secret_type,
                   encrypted_value,
                   masked_value,
                   fingerprint,
                   algorithm,
                   source_type,
                   file_name,
                   file_content_type,
                   file_value_type,
                   version_no,
                   status,
                   valid_from,
                   valid_to,
                   remark
            FROM pay_merchant_secret
            WHERE merchant_id = #{merchantId}
              AND secret_name = #{secretName}
            ORDER BY version_no DESC, id DESC
            LIMIT 1
            """)
    PaymentSecretEntity findLatestEntityByMerchantIdAndName(@Param("merchantId") Long merchantId,
                                                            @Param("secretName") String secretName);

    @Insert("""
            INSERT INTO pay_merchant_secret (
                merchant_id,
                secret_name,
                secret_type,
                encrypted_value,
                masked_value,
                fingerprint,
                algorithm,
                source_type,
                file_name,
                file_content_type,
                file_value_type,
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
                #{sourceType},
                #{fileName},
                #{fileContentType},
                #{fileValueType},
                #{versionNo},
                #{status},
                #{validFrom},
                #{validTo},
                #{remark}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(PaymentSecretEntity entity);

    @Update("""
            UPDATE pay_merchant_secret
            SET secret_name = #{secretName},
                secret_type = #{secretType},
                encrypted_value = #{encryptedValue},
                masked_value = #{maskedValue},
                fingerprint = #{fingerprint},
                algorithm = #{algorithm},
                source_type = #{sourceType},
                file_name = #{fileName},
                file_content_type = #{fileContentType},
                file_value_type = #{fileValueType},
                status = #{status},
                valid_from = #{validFrom},
                valid_to = #{validTo},
                remark = #{remark}
            WHERE id = #{id}
            """)
    int update(PaymentSecretEntity entity);
}
