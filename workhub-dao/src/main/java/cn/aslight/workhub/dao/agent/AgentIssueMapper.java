package cn.aslight.workhub.dao.agent;

import cn.aslight.workhub.model.agent.AgentIssueEntity;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

/** 巡检问题工单；跨轮按 (task_id, signature) 聚合，状态机由服务层驱动。 */
@Mapper
public interface AgentIssueMapper {
    String COLUMNS = "id, task_id, agent_id, business_line_code, environment_code, domain, signature, title, severity, status, "
            + "analysis_json, evidence_json, fix_json, review_json, deploy_json, first_run_id, last_run_id, "
            + "first_seen_at, last_seen_at, occurrence_count, unseen_rounds, reopen_count, version, created_at, updated_at";

    @Select("SELECT " + COLUMNS + " FROM pm_agent_issue WHERE id=#{id}")
    AgentIssueEntity find(@Param("id") String id);

    @Select("SELECT " + COLUMNS + " FROM pm_agent_issue WHERE id=#{id} FOR UPDATE")
    AgentIssueEntity lock(@Param("id") String id);

    @Select("SELECT " + COLUMNS + " FROM pm_agent_issue WHERE task_id=#{taskId} AND signature=#{signature}")
    AgentIssueEntity findBySignature(@Param("taskId") String taskId, @Param("signature") String signature);

    @Select("<script>SELECT " + COLUMNS + " FROM pm_agent_issue"
            + "<where><if test='status != null'> AND status=#{status}</if>"
            + "<if test='domain != null'> AND domain=#{domain}</if>"
            + "<if test='severity != null'> AND severity=#{severity}</if>"
            + "<if test='businessLineCode != null'> AND business_line_code=#{businessLineCode}</if>"
            + "<if test='agentId != null'> AND agent_id=#{agentId}</if>"
            + "<if test='runId != null'> AND (first_run_id=#{runId} OR last_run_id=#{runId})</if>"
            + "</where> ORDER BY CASE severity WHEN 'P0' THEN 0 WHEN 'P1' THEN 1 ELSE 2 END, updated_at DESC LIMIT #{limit}</script>")
    List<AgentIssueEntity> list(@Param("status") String status, @Param("domain") String domain,
                                @Param("severity") String severity, @Param("businessLineCode") String businessLineCode,
                                @Param("agentId") String agentId, @Param("runId") String runId, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM pm_agent_issue WHERE task_id=#{taskId} AND status IN ('OPEN','ANALYZED')")
    int countOpenByTask(@Param("taskId") String taskId);

    @Insert("""
            INSERT INTO pm_agent_issue(id, task_id, agent_id, business_line_code, environment_code, domain, signature, title, severity, status,
              analysis_json, evidence_json, first_run_id, last_run_id, first_seen_at, last_seen_at, occurrence_count, version, created_at, updated_at)
            VALUES (#{id}, #{taskId}, #{agentId}, #{businessLineCode}, #{environmentCode}, #{domain}, #{signature}, #{title}, #{severity}, #{status},
              #{analysisJson}, #{evidenceJson}, #{firstRunId}, #{lastRunId}, #{firstSeenAt}, #{lastSeenAt}, #{occurrenceCount}, #{version}, #{createdAt}, #{updatedAt})
            """)
    int insert(AgentIssueEntity entity);

    @Update("""
            UPDATE pm_agent_issue SET title=#{title}, severity=#{severity}, status=#{status}, analysis_json=#{analysisJson},
              evidence_json=#{evidenceJson}, last_run_id=#{lastRunId}, last_seen_at=#{lastSeenAt},
              occurrence_count=#{occurrenceCount}, unseen_rounds=#{unseenRounds}, reopen_count=#{reopenCount},
              version=version+1, updated_at=#{updatedAt} WHERE id=#{id} AND version=#{version}
            """)
    int updateObservation(AgentIssueEntity entity);

    @Update("""
            UPDATE pm_agent_issue SET status=#{status}, fix_json=#{fixJson}, review_json=#{reviewJson}, deploy_json=#{deployJson},
              version=version+1, updated_at=#{updatedAt} WHERE id=#{id} AND version=#{version}
            """)
    int updateWorkflow(@Param("id") String id, @Param("version") long version, @Param("status") String status,
                       @Param("fixJson") String fixJson, @Param("reviewJson") String reviewJson,
                       @Param("deployJson") String deployJson, @Param("updatedAt") LocalDateTime updatedAt);

    @Delete("DELETE FROM pm_agent_issue WHERE id=#{id} AND version=#{version}")
    int deleteById(@Param("id") String id, @Param("version") long version);
}
