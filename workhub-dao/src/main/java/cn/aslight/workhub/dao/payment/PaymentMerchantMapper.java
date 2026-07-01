package cn.aslight.workhub.dao.payment;

import cn.aslight.workhub.model.payment.PaymentMerchantDetailView;
import cn.aslight.workhub.model.payment.PaymentMerchantEntity;
import cn.aslight.workhub.model.payment.PaymentMerchantSummaryResponse;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 支付商户数据访问接口。
 */
@Mapper
public interface PaymentMerchantMapper {

    @Select({
            "<script>",
            "SELECT DISTINCT m.id,",
            "m.channel_id AS channelId,",
            "c.channel_code AS channelCode,",
            "c.channel_name AS channelName,",
            "m.merchant_code AS merchantCode,",
            "m.merchant_name AS merchantName,",
            "m.environment,",
            "m.app_id AS appId,",
            "NULL AS purposeCodes,",
            "m.status",
            "FROM pay_merchant_account m",
            "JOIN pay_channel c ON c.id = m.channel_id",
            "<where>",
            "<if test='status != null and status != \"\"'>",
            "AND m.status = #{status}",
            "</if>",
            "<if test='channelId != null'>",
            "AND m.channel_id = #{channelId}",
            "</if>",
            "<if test='projectId != null or (businessLine != null and businessLine != \"\")'>",
            "AND EXISTS (",
            "SELECT 1",
            "FROM pay_project_merchant_binding b",
            "LEFT JOIN pm_project p ON p.id = b.project_id",
            "LEFT JOIN pm_business_line bl ON bl.business_line_code = COALESCE(b.business_line_code, p.business_line_code)",
            "WHERE b.merchant_id = m.id",
            "<if test='projectId != null'>",
            "AND b.project_id = #{projectId}",
            "</if>",
            "<if test='businessLine != null and businessLine != \"\"'>",
            "AND (COALESCE(b.business_line_code, p.business_line_code, b.business_line, p.business_line) = #{businessLine}",
            "OR COALESCE(bl.business_line_name, b.business_line, p.business_line) = #{businessLine})",
            "</if>",
            ")",
            "</if>",
            "<if test='purposeCode != null and purposeCode != \"\"'>",
            "AND EXISTS (SELECT 1 FROM pay_merchant_purpose mp WHERE mp.merchant_id = m.id AND mp.purpose_code = #{purposeCode})",
            "</if>",
            "<if test='keyword != null and keyword != \"\"'>",
            "AND (m.merchant_code LIKE CONCAT('%', #{keyword}, '%') OR m.merchant_name LIKE CONCAT('%', #{keyword}, '%'))",
            "</if>",
            "</where>",
            "ORDER BY m.id DESC",
            "</script>"
    })
    List<PaymentMerchantSummaryResponse> findAll(@Param("status") String status,
                                                 @Param("channelId") Long channelId,
                                                 @Param("projectId") Long projectId,
                                                 @Param("businessLine") String businessLine,
                                                 @Param("purposeCode") String purposeCode,
                                                 @Param("keyword") String keyword);

    @Select("""
            SELECT purpose_code
            FROM pay_merchant_purpose
            WHERE merchant_id = #{merchantId}
            ORDER BY id ASC
            """)
    List<String> findPurposeCodes(Long merchantId);

    @Delete("""
            DELETE FROM pay_merchant_purpose
            WHERE merchant_id = #{merchantId}
            """)
    int deletePurposes(Long merchantId);

    @Insert("""
            INSERT INTO pay_merchant_purpose (merchant_id, purpose_code)
            VALUES (#{merchantId}, #{purposeCode})
            """)
    int insertPurpose(@Param("merchantId") Long merchantId, @Param("purposeCode") String purposeCode);

    @Select("""
            SELECT m.id,
                   m.channel_id AS channelId,
                   c.channel_code AS channelCode,
                   c.channel_name AS channelName,
                   m.merchant_code AS merchantCode,
                   m.merchant_name AS merchantName,
                   m.environment,
                   m.app_id AS appId,
                   m.settlement_subject AS settlementSubject,
                   m.status,
                   m.remark,
                   m.created_at AS createdAt,
                   m.updated_at AS updatedAt
            FROM pay_merchant_account m
            JOIN pay_channel c ON c.id = m.channel_id
            WHERE m.id = #{id}
            """)
    PaymentMerchantDetailView findDetailById(Long id);

    @Select("""
            SELECT id,
                   channel_id,
                   merchant_code,
                   merchant_name,
                   environment,
                   status,
                   app_id,
                   settlement_subject,
                   remark
            FROM pay_merchant_account
            WHERE id = #{id}
            """)
    PaymentMerchantEntity findEntityById(Long id);

    @Select("""
            SELECT id,
                   channel_id,
                   merchant_code,
                   merchant_name,
                   environment,
                   status,
                   app_id,
                   settlement_subject,
                   remark
            FROM pay_merchant_account
            WHERE channel_id = #{channelId}
              AND merchant_code = #{merchantCode}
              AND environment = #{environment}
            """)
    PaymentMerchantEntity findEntityByUniqueKey(@Param("channelId") Long channelId,
                                                @Param("merchantCode") String merchantCode,
                                                @Param("environment") String environment);

    @Insert("""
            INSERT INTO pay_merchant_account (
                channel_id,
                merchant_code,
                merchant_name,
                environment,
                status,
                app_id,
                settlement_subject,
                remark
            ) VALUES (
                #{channelId},
                #{merchantCode},
                #{merchantName},
                #{environment},
                #{status},
                #{appId},
                #{settlementSubject},
                #{remark}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(PaymentMerchantEntity entity);

    @Update("""
            UPDATE pay_merchant_account
            SET channel_id = #{channelId},
                merchant_code = #{merchantCode},
                merchant_name = #{merchantName},
                environment = #{environment},
                status = #{status},
                app_id = #{appId},
                settlement_subject = #{settlementSubject},
                remark = #{remark}
            WHERE id = #{id}
            """)
    int update(PaymentMerchantEntity entity);
}
