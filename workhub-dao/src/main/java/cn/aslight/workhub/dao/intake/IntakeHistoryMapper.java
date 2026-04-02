package cn.aslight.workhub.dao.intake;

import cn.aslight.workhub.model.intake.IntakeHistoryEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 需求管理历史数据访问接口。
 */
@Mapper
public interface IntakeHistoryMapper {

    @Insert("""
            INSERT INTO pm_intake_history (
                intake_id,
                action_type,
                action_summary,
                detail_text,
                operator_user_name
            ) VALUES (
                #{intakeId},
                #{actionType},
                #{actionSummary},
                #{detailText},
                #{operatorUserName}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(IntakeHistoryEntity entity);

    @Select("""
            SELECT id,
                   intake_id,
                   action_type,
                   action_summary,
                   detail_text,
                   operator_user_name,
                   created_at
            FROM pm_intake_history
            WHERE intake_id = #{intakeId}
            ORDER BY id DESC
            LIMIT #{limit}
            """)
    List<IntakeHistoryEntity> findRecentByIntakeId(@Param("intakeId") Long intakeId,
                                                   @Param("limit") int limit);
}
