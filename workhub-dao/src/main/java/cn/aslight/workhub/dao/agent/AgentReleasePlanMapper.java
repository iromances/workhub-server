package cn.aslight.workhub.dao.agent;

import cn.aslight.workhub.model.agent.*;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AgentReleasePlanMapper {
    @Select("SELECT * FROM pm_agent_release_plan WHERE id=#{id}")
    AgentReleasePlanEntity find(String id);
    @Select("SELECT * FROM pm_agent_release_plan WHERE run_id=#{runId} AND request_key=#{key}")
    AgentReleasePlanEntity byKey(@Param("runId") String runId, @Param("key") String key);
    @Select("SELECT * FROM pm_agent_release_plan WHERE change_set_id=#{id} ORDER BY created_at DESC,id LIMIT 100")
    List<AgentReleasePlanEntity> list(String id);
    @Select("SELECT p.* FROM pm_agent_release_plan p JOIN pm_agent_release_plan_step s ON s.plan_id=p.id WHERE s.request_id=#{id}")
    AgentReleasePlanEntity forRequest(String id);
    @Insert("""
            INSERT INTO pm_agent_release_plan(id,run_id,change_set_id,request_key,definition_hash,manifest_hash,stage,snapshot_json,snapshot_hash,status,created_by,version,created_at,updated_at)
            VALUES(#{id},#{runId},#{changeSetId},#{requestKey},#{definitionHash},#{manifestHash},#{stage},#{snapshotJson},#{snapshotHash},#{status},#{createdBy},#{version},#{createdAt},#{updatedAt})
            """)
    int insert(AgentReleasePlanEntity value);
    @Insert("INSERT INTO pm_agent_release_plan_step(request_id,approval_id,plan_id,step_order) VALUES(#{requestId},#{approvalId},#{planId},#{stepOrder})")
    int step(@Param("requestId") String requestId, @Param("approvalId") String approvalId, @Param("planId") String planId, @Param("stepOrder") int stepOrder);
    @Insert("INSERT INTO pm_agent_release_step_dependency(request_id,depends_on_request_id) VALUES(#{id},#{dependency})")
    int dependency(@Param("id") String id, @Param("dependency") String dependency);
    @Update("""
            UPDATE pm_agent_deployment SET status='BLOCKED',error_message=#{reason},owner_token=NULL,lease_until=NULL,version=version+1,updated_at=#{now}
            WHERE id=#{id} AND status='PREPARED'
            """)
    int blockUnsent(@Param("id") String id, @Param("reason") String reason, @Param("now") LocalDateTime now);
    @Update("UPDATE pm_agent_release_plan SET status=#{status},version=version+1,updated_at=#{now} WHERE id=#{id} AND version=#{version}")
    int status(@Param("id") String id, @Param("version") long version, @Param("status") String status, @Param("now") LocalDateTime now);
    @Select("SELECT * FROM pm_agent_release_review WHERE plan_id=#{id} ORDER BY created_at,id")
    List<AgentReleaseReviewEntity> reviews(String id);
    @Select("SELECT * FROM pm_agent_release_review WHERE plan_id=#{id} AND request_key=#{key}")
    AgentReleaseReviewEntity review(@Param("id") String id, @Param("key") String key);
    @Insert("""
            INSERT INTO pm_agent_release_review(id,plan_id,request_key,decision,reviewed_by,comment,snapshot_hash,expected_version,created_at)
            VALUES(#{id},#{planId},#{requestKey},#{decision},#{reviewedBy},#{comment},#{snapshotHash},#{expectedVersion},#{createdAt})
            """)
    int insertReview(AgentReleaseReviewEntity value);
}
