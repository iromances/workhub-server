package cn.aslight.workhub.dao.intake;

import cn.aslight.workhub.model.intake.IntakeStructuredFieldEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 需求动态结构化字段数据访问接口。
 */
@Mapper
public interface IntakeStructuredFieldMapper {

    @Select("""
            SELECT id,
                   intake_id,
                   field_label,
                   field_value,
                   normalized_key,
                   source_type,
                   sort_order,
                   created_at,
                   updated_at
            FROM pm_intake_structured_field
            WHERE intake_id = #{intakeId}
            ORDER BY sort_order ASC, id ASC
            """)
    List<IntakeStructuredFieldEntity> findByIntakeId(Long intakeId);

    @Delete("""
            DELETE FROM pm_intake_structured_field
            WHERE intake_id = #{intakeId}
            """)
    int deleteByIntakeId(Long intakeId);

    @Insert({
            "<script>",
            "INSERT INTO pm_intake_structured_field (",
            "intake_id, field_label, field_value, normalized_key, source_type, sort_order",
            ") VALUES",
            "<foreach collection='fields' item='field' separator=','>",
            "(",
            "#{field.intakeId},",
            "#{field.fieldLabel},",
            "#{field.fieldValue},",
            "#{field.normalizedKey},",
            "#{field.sourceType},",
            "#{field.sortOrder}",
            ")",
            "</foreach>",
            "ON DUPLICATE KEY UPDATE",
            "field_label = VALUES(field_label),",
            "field_value = VALUES(field_value),",
            "normalized_key = VALUES(normalized_key),",
            "source_type = VALUES(source_type),",
            "updated_at = CURRENT_TIMESTAMP",
            "</script>"
    })
    int upsertBatch(@Param("fields") List<IntakeStructuredFieldEntity> fields);
}
