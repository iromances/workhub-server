package cn.aslight.workhub.dao.payment;

import cn.aslight.workhub.model.payment.PaymentProjectBindingEntity;
import cn.aslight.workhub.model.payment.PaymentProjectBindingResponse;
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
            "b.priority,",
            "b.is_default AS defaultBinding,",
            "b.binding_status AS status,",
            "b.remark,",
            "b.created_at AS createdAt,",
            "b.updated_at AS updatedAt",
            "FROM pay_project_merchant_binding b",
            "JOIN pm_project p ON p.id = b.project_id",
            "JOIN pay_merchant_account m ON m.id = b.merchant_id",
            "JOIN pay_channel c ON c.id = m.channel_id",
            "<where>",
            "<if test='projectId != null'>",
            "AND b.project_id = #{projectId}",
            "</if>",
            "<if test='merchantId != null'>",
            "AND b.merchant_id = #{merchantId}",
            "</if>",
            "<if test='purposeCode != null and purposeCode != \"\"'>",
            "AND b.purpose_code = #{purposeCode}",
            "</if>",
            "<if test='status != null and status != \"\"'>",
            "AND b.binding_status = #{status}",
            "</if>",
            "</where>",
            "ORDER BY b.project_id ASC, b.purpose_code ASC, b.is_default DESC, b.priority ASC, b.id DESC",
            "</script>"
    })
    List<PaymentProjectBindingResponse> findAll(@Param("projectId") Long projectId,
                                                @Param("merchantId") Long merchantId,
                                                @Param("purposeCode") String purposeCode,
                                                @Param("status") String status);

    @Select("""
            SELECT b.id,
                   b.project_id AS projectId,
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
                   b.priority,
                   b.is_default AS defaultBinding,
                   b.binding_status AS status,
                   b.remark,
                   b.created_at AS createdAt,
                   b.updated_at AS updatedAt
            FROM pay_project_merchant_binding b
            JOIN pm_project p ON p.id = b.project_id
            JOIN pay_merchant_account m ON m.id = b.merchant_id
            JOIN pay_channel c ON c.id = m.channel_id
            WHERE b.id = #{id}
            """)
    PaymentProjectBindingResponse findResponseById(Long id);

    @Select("""
            SELECT id,
                   project_id,
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

    @Select("""
            SELECT id,
                   project_id,
                   merchant_id,
                   purpose_code,
                   priority,
                   is_default AS defaultBinding,
                   binding_status,
                   remark
            FROM pay_project_merchant_binding
            WHERE project_id = #{projectId}
              AND merchant_id = #{merchantId}
              AND purpose_code = #{purposeCode}
            """)
    PaymentProjectBindingEntity findEntityByUniqueKey(@Param("projectId") Long projectId,
                                                      @Param("merchantId") Long merchantId,
                                                      @Param("purposeCode") String purposeCode);

    @Insert("""
            INSERT INTO pay_project_merchant_binding (
                project_id,
                merchant_id,
                purpose_code,
                priority,
                is_default,
                binding_status,
                remark
            ) VALUES (
                #{projectId},
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
                merchant_id = #{merchantId},
                purpose_code = #{purposeCode},
                priority = #{priority},
                is_default = #{defaultBinding},
                binding_status = #{bindingStatus},
                remark = #{remark}
            WHERE id = #{id}
            """)
    int update(PaymentProjectBindingEntity entity);

    @Update("""
            UPDATE pay_project_merchant_binding
            SET is_default = 0
            WHERE project_id = #{projectId}
              AND purpose_code = #{purposeCode}
              AND id != COALESCE(#{excludeId}, -1)
            """)
    int clearDefaultBindings(@Param("projectId") Long projectId,
                             @Param("purposeCode") String purposeCode,
                             @Param("excludeId") Long excludeId);

    @Select("""
            SELECT b.id,
                   b.project_id AS projectId,
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
                   b.priority,
                   b.is_default AS defaultBinding,
                   b.binding_status AS status,
                   b.remark,
                   b.created_at AS createdAt,
                   b.updated_at AS updatedAt
            FROM pay_project_merchant_binding b
            JOIN pm_project p ON p.id = b.project_id
            JOIN pay_merchant_account m ON m.id = b.merchant_id
            JOIN pay_channel c ON c.id = m.channel_id
            WHERE b.project_id = #{projectId}
              AND b.purpose_code = #{purposeCode}
              AND b.binding_status = 'ACTIVE'
              AND m.status = 'ACTIVE'
              AND c.status = 'ACTIVE'
            ORDER BY b.is_default DESC, b.priority ASC, b.id ASC
            LIMIT 1
            """)
    PaymentProjectBindingResponse resolveActiveBinding(@Param("projectId") Long projectId,
                                                       @Param("purposeCode") String purposeCode);
}
