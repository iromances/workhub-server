package cn.aslight.workhub.dao.intake;

import cn.aslight.workhub.model.intake.IntakeTodoEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 需求待办数据访问接口。
 */
@Mapper
public interface IntakeTodoMapper {

    @Select("""
            SELECT id,
                   intake_id,
                   todo_title AS title,
                   todo_content AS content,
                   todo_status,
                   assignee_user_name,
                   planned_at,
                   completed_at,
                   process_result,
                   created_at,
                   updated_at
            FROM pm_intake_todo
            WHERE intake_id = #{intakeId}
            ORDER BY id DESC
            """)
    List<IntakeTodoEntity> findByIntakeId(Long intakeId);

    @Select("""
            SELECT id,
                   intake_id,
                   todo_title AS title,
                   todo_content AS content,
                   todo_status,
                   assignee_user_name,
                   planned_at,
                   completed_at,
                   process_result,
                   created_at,
                   updated_at
            FROM pm_intake_todo
            WHERE id = #{id}
            """)
    IntakeTodoEntity findById(Long id);

    @Insert("""
            INSERT INTO pm_intake_todo (
                intake_id,
                todo_title,
                todo_content,
                todo_status,
                assignee_user_name,
                planned_at,
                completed_at,
                process_result
            ) VALUES (
                #{intakeId},
                #{title},
                #{content},
                #{todoStatus},
                #{assigneeUserName},
                #{plannedAt},
                #{completedAt},
                #{processResult}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(IntakeTodoEntity entity);

    @Update("""
            UPDATE pm_intake_todo
            SET todo_title = #{title},
                todo_content = #{content},
                assignee_user_name = #{assigneeUserName},
                planned_at = #{plannedAt},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updateEditableFields(IntakeTodoEntity entity);

    @Update("""
            UPDATE pm_intake_todo
            SET todo_status = #{todoStatus},
                process_result = #{processResult},
                completed_at = #{completedAt},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updateStatus(IntakeTodoEntity entity);

    @Delete("""
            DELETE FROM pm_intake_todo
            WHERE id = #{id}
            """)
    int deleteById(Long id);

}
