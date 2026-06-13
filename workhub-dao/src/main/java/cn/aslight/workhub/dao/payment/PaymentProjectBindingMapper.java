package cn.aslight.workhub.dao.payment;

import cn.aslight.workhub.model.payment.PaymentBindingRelationEntity;
import cn.aslight.workhub.model.payment.PaymentBindingRelationResponse;
import cn.aslight.workhub.model.payment.PaymentProjectBindingEntity;
import cn.aslight.workhub.model.payment.PaymentProjectBindingResponse;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 项目商户绑定数据访问接口。
 */
@Mapper
public interface PaymentProjectBindingMapper {

    @Select({
            "<script>",
            "SELECT b.id,",
            "b.project_id AS projectId,",
            "COALESCE(b.business_line, p.business_line) AS businessLine,",
            "p.project_code AS projectCode,",
            "p.project_name AS projectName,",
            "b.merchant_id AS merchantId,",
            "m.merchant_code AS merchantCode,",
            "m.merchant_name AS merchantName,",
            "c.id AS channelId,",
            "c.channel_code AS channelCode,",
            "c.channel_name AS channelName,",
            "m.environment,",
            "b.purpose_code AS purposeCode,",
            "NULL AS purposeCodes,",
            "b.priority,",
            "b.is_default AS defaultBinding,",
            "b.binding_status AS status,",
            "b.remark,",
            "NULL AS relations,",
            "b.created_at AS createdAt,",
            "b.updated_at AS updatedAt",
            "FROM pay_project_merchant_binding b",
            "LEFT JOIN pm_project p ON p.id = b.project_id",
            "JOIN pay_merchant_account m ON m.id = b.merchant_id",
            "JOIN pay_channel c ON c.id = m.channel_id",
            "<where>",
            "<if test='projectId != null'>",
            "AND b.project_id = #{projectId}",
            "</if>",
            "<if test='businessLine != null and businessLine != \"\"'>",
            "AND COALESCE(b.business_line, p.business_line) = #{businessLine}",
            "</if>",
            "<if test='merchantId != null'>",
            "AND b.merchant_id = #{merchantId}",
            "</if>",
            "<if test='purposeCode != null and purposeCode != \"\"'>",
            "AND EXISTS (SELECT 1 FROM pay_project_merchant_binding_purpose bp WHERE bp.binding_id = b.id AND bp.purpose_code = #{purposeCode})",
            "</if>",
            "<if test='status != null and status != \"\"'>",
            "AND b.binding_status = #{status}",
            "</if>",
            "</where>",
            "ORDER BY COALESCE(b.business_line, p.business_line) ASC, b.project_id ASC, b.purpose_code ASC, b.is_default DESC, b.priority ASC, b.id DESC",
            "</script>"
    })
    List<PaymentProjectBindingResponse> findAll(@Param("projectId") Long projectId,
                                                @Param("businessLine") String businessLine,
                                                @Param("merchantId") Long merchantId,
                                                @Param("purposeCode") String purposeCode,
                                                @Param("status") String status);

    @Select("""
            SELECT b.id,
                   b.project_id AS projectId,
                   COALESCE(b.business_line, p.business_line) AS businessLine,
                   p.project_code AS projectCode,
                   p.project_name AS projectName,
                   b.merchant_id AS merchantId,
                   m.merchant_code AS merchantCode,
                   m.merchant_name AS merchantName,
                   c.id AS channelId,
                   c.channel_code AS channelCode,
                   c.channel_name AS channelName,
                   m.environment,
                   b.purpose_code AS purposeCode,
                   NULL AS purposeCodes,
                   b.priority,
                   b.is_default AS defaultBinding,
                   b.binding_status AS status,
                   b.remark,
                   NULL AS relations,
                   b.created_at AS createdAt,
                   b.updated_at AS updatedAt
            FROM pay_project_merchant_binding b
            LEFT JOIN pm_project p ON p.id = b.project_id
            JOIN pay_merchant_account m ON m.id = b.merchant_id
            JOIN pay_channel c ON c.id = m.channel_id
            WHERE b.id = #{id}
            """)
    PaymentProjectBindingResponse findResponseById(Long id);

    @Select("""
            SELECT id,
                   project_id,
                   business_line,
                   merchant_id,
                   purpose_code,
                   priority,
                   is_default AS defaultBinding,
                   binding_status,
                   remark
            FROM pay_project_merchant_binding
            WHERE id = #{id}
            """)
    PaymentProjectBindingEntity findEntityById(Long id);

    @Select({
            "<script>",
            "SELECT id,",
            "project_id,",
            "business_line,",
            "merchant_id,",
            "purpose_code,",
            "priority,",
            "is_default AS defaultBinding,",
            "binding_status,",
            "remark",
            "FROM pay_project_merchant_binding",
            "<where>",
            "<choose>",
            "<when test='projectId != null'>",
            "project_id = #{projectId}",
            "</when>",
            "<otherwise>",
            "project_id IS NULL",
            "AND business_line = #{businessLine}",
            "</otherwise>",
            "</choose>",
            "AND merchant_id = #{merchantId}",
            "AND EXISTS (",
            "SELECT 1",
            "FROM pay_project_merchant_binding_purpose bp",
            "WHERE bp.binding_id = pay_project_merchant_binding.id",
            "AND bp.purpose_code = #{purposeCode}",
            ")",
            "</where>",
            "</script>"
    })
    PaymentProjectBindingEntity findEntityByUniqueKey(@Param("projectId") Long projectId,
                                                      @Param("businessLine") String businessLine,
                                                      @Param("merchantId") Long merchantId,
                                                      @Param("purposeCode") String purposeCode);

    @Insert("""
            INSERT INTO pay_project_merchant_binding (
                project_id,
                business_line,
                merchant_id,
                purpose_code,
                priority,
                is_default,
                binding_status,
                remark
            ) VALUES (
                #{projectId},
                #{businessLine},
                #{merchantId},
                #{purposeCode},
                #{priority},
                #{defaultBinding},
                #{bindingStatus},
                #{remark}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(PaymentProjectBindingEntity entity);

    @Update("""
            UPDATE pay_project_merchant_binding
            SET project_id = #{projectId},
                business_line = #{businessLine},
                merchant_id = #{merchantId},
                purpose_code = #{purposeCode},
                priority = #{priority},
                is_default = #{defaultBinding},
                binding_status = #{bindingStatus},
                remark = #{remark}
            WHERE id = #{id}
            """)
    int update(PaymentProjectBindingEntity entity);

    @Update({
            "<script>",
            """
            UPDATE pay_project_merchant_binding
            SET is_default = 0
            WHERE
            """,
            "<choose>",
            "<when test='projectId != null'>",
            "project_id = #{projectId}",
            "</when>",
            "<otherwise>",
            "project_id IS NULL AND business_line = #{businessLine}",
            "</otherwise>",
            "</choose>",
            """
              AND EXISTS (
                  SELECT 1
                  FROM pay_project_merchant_binding_purpose bp
                  WHERE bp.binding_id = pay_project_merchant_binding.id
                    AND bp.purpose_code = #{purposeCode}
              )
              AND id != COALESCE(#{excludeId}, -1)
            """,
            "</script>"
    })
    int clearDefaultBindings(@Param("projectId") Long projectId,
                             @Param("businessLine") String businessLine,
                             @Param("purposeCode") String purposeCode,
                             @Param("excludeId") Long excludeId);

    @Select("""
            SELECT b.id,
                   b.project_id AS projectId,
                   COALESCE(b.business_line, p.business_line) AS businessLine,
                   p.project_code AS projectCode,
                   p.project_name AS projectName,
                   b.merchant_id AS merchantId,
                   m.merchant_code AS merchantCode,
                   m.merchant_name AS merchantName,
                   c.id AS channelId,
                   c.channel_code AS channelCode,
                   c.channel_name AS channelName,
                   m.environment,
                   b.purpose_code AS purposeCode,
                   NULL AS purposeCodes,
                   b.priority,
                   b.is_default AS defaultBinding,
                   b.binding_status AS status,
                   b.remark,
                   NULL AS relations,
                   b.created_at AS createdAt,
                   b.updated_at AS updatedAt
            FROM pay_project_merchant_binding b
            LEFT JOIN pm_project p ON p.id = b.project_id
            JOIN pay_merchant_account m ON m.id = b.merchant_id
            JOIN pay_channel c ON c.id = m.channel_id
            WHERE b.project_id = #{projectId}
              AND EXISTS (SELECT 1 FROM pay_project_merchant_binding_purpose bp WHERE bp.binding_id = b.id AND bp.purpose_code = #{purposeCode})
              AND b.binding_status = 'ACTIVE'
              AND m.status = 'ACTIVE'
              AND c.status = 'ACTIVE'
            ORDER BY b.is_default DESC, b.priority ASC, b.id ASC
            LIMIT 1
            """)
    PaymentProjectBindingResponse resolveActiveBinding(@Param("projectId") Long projectId,
                                                       @Param("purposeCode") String purposeCode);

    @Select("""
            SELECT b.id,
                   b.project_id AS projectId,
                   b.business_line AS businessLine,
                   p.project_code AS projectCode,
                   p.project_name AS projectName,
                   b.merchant_id AS merchantId,
                   m.merchant_code AS merchantCode,
                   m.merchant_name AS merchantName,
                   c.id AS channelId,
                   c.channel_code AS channelCode,
                   c.channel_name AS channelName,
                   m.environment,
                   b.purpose_code AS purposeCode,
                   NULL AS purposeCodes,
                   b.priority,
                   b.is_default AS defaultBinding,
                   b.binding_status AS status,
                   b.remark,
                   NULL AS relations,
                   b.created_at AS createdAt,
                   b.updated_at AS updatedAt
            FROM pay_project_merchant_binding b
            LEFT JOIN pm_project p ON p.id = b.project_id
            JOIN pay_merchant_account m ON m.id = b.merchant_id
            JOIN pay_channel c ON c.id = m.channel_id
            WHERE b.project_id IS NULL
              AND b.business_line = #{businessLine}
              AND EXISTS (SELECT 1 FROM pay_project_merchant_binding_purpose bp WHERE bp.binding_id = b.id AND bp.purpose_code = #{purposeCode})
              AND b.binding_status = 'ACTIVE'
              AND m.status = 'ACTIVE'
              AND c.status = 'ACTIVE'
            ORDER BY b.is_default DESC, b.priority ASC, b.id ASC
            LIMIT 1
            """)
    PaymentProjectBindingResponse resolveBusinessLineActiveBinding(@Param("businessLine") String businessLine,
                                                                   @Param("purposeCode") String purposeCode);

    @Select("""
            SELECT purpose_code
            FROM pay_project_merchant_binding_purpose
            WHERE binding_id = #{bindingId}
            ORDER BY id ASC
            """)
    List<String> findPurposeCodes(Long bindingId);

    @Delete("""
            DELETE FROM pay_project_merchant_binding_purpose
            WHERE binding_id = #{bindingId}
            """)
    int deletePurposes(Long bindingId);

    @Insert("""
            INSERT INTO pay_project_merchant_binding_purpose (binding_id, purpose_code)
            VALUES (#{bindingId}, #{purposeCode})
            """)
    int insertPurpose(@Param("bindingId") Long bindingId, @Param("purposeCode") String purposeCode);

    @Delete("""
            DELETE FROM pay_project_merchant_binding_relation
            WHERE binding_id = #{bindingId}
            """)
    int deleteRelations(Long bindingId);

    @Insert("""
            INSERT INTO pay_project_merchant_binding_relation (
                binding_id,
                merchant_id,
                relation_role,
                relation_name,
                priority,
                remark
            ) VALUES (
                #{bindingId},
                #{merchantId},
                #{relationRole},
                #{relationName},
                #{priority},
                #{remark}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertRelation(PaymentBindingRelationEntity entity);

    @Select("""
            SELECT r.id,
                   r.binding_id AS bindingId,
                   r.merchant_id AS merchantId,
                   m.merchant_code AS merchantCode,
                   m.merchant_name AS merchantName,
                   r.relation_role AS relationRole,
                   r.relation_name AS relationName,
                   r.priority,
                   r.remark
            FROM pay_project_merchant_binding_relation r
            JOIN pay_merchant_account m ON m.id = r.merchant_id
            WHERE r.binding_id = #{bindingId}
            ORDER BY r.priority ASC, r.id ASC
            """)
    List<PaymentBindingRelationResponse> findRelations(Long bindingId);
}
