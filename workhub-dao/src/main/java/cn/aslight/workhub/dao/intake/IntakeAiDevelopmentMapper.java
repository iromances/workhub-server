package cn.aslight.workhub.dao.intake;

import cn.aslight.workhub.model.intake.IntakeAiDevelopmentEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 需求 AI 分支开发执行记录 Mapper。
 */
@Mapper
public interface IntakeAiDevelopmentMapper {

    @Select("""
            SELECT id, intake_id, development_branch, agent_id, agent_task_id, request_key,
                   execution_status, execution_message, created_by, updated_by, created_at, updated_at
            FROM pm_intake_ai_development
            WHERE intake_id = #{intakeId}
            """)
    IntakeAiDevelopmentEntity findByIntakeId(@Param("intakeId") Long intakeId);

    @Insert("""
            INSERT INTO pm_intake_ai_development (
                intake_id, development_branch, agent_id, agent_task_id, request_key,
                execution_status, execution_message, created_by, updated_by
            ) VALUES (
                #{intakeId}, #{developmentBranch}, #{agentId}, #{agentTaskId}, #{requestKey},
                #{executionStatus}, #{executionMessage}, #{createdBy}, #{updatedBy}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(IntakeAiDevelopmentEntity entity);

    @Update("""
            UPDATE pm_intake_ai_development
            SET development_branch = #{developmentBranch},
                agent_id = #{agentId},
                agent_task_id = #{agentTaskId},
                execution_status = #{executionStatus},
                execution_message = #{executionMessage},
                updated_by = #{updatedBy},
                updated_at = CURRENT_TIMESTAMP
            WHERE intake_id = #{intakeId}
            """)
    int updateByIntakeId(IntakeAiDevelopmentEntity entity);

    /** 巡检目标：已启动 Agent 且尚未终态（PENDING/SUCCEEDED）的记录，附带该 Agent 任务最新一次运行状态。 */
    @Select("""
            SELECT d.intake_id AS intakeId,
                   d.agent_task_id AS agentTaskId,
                   d.execution_status AS executionStatus,
                   (SELECT r.status FROM pm_agent_run r WHERE r.task_id = d.agent_task_id ORDER BY r.created_at DESC LIMIT 1) AS latestRunStatus
            FROM pm_intake_ai_development d
            WHERE d.agent_task_id IS NOT NULL
              AND d.execution_status IN ('PENDING', 'SUCCEEDED')
            LIMIT 50
            """)
    java.util.List<java.util.Map<String, Object>> findWatchTargets();
}
