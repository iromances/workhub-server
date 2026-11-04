package cn.aslight.workhub.dao.agent;

import cn.aslight.workhub.model.agent.*;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AgentVerificationMapper {
    @Select("SELECT * FROM pm_agent_verification WHERE id=#{id}")
    AgentVerificationEntity find(String id);
    @Select("SELECT * FROM pm_agent_verification WHERE plan_id=#{id}")
    AgentVerificationEntity forPlan(String id);
    @Select("SELECT * FROM pm_agent_verification WHERE artifact_id=#{id} AND status='PASSED'")
    AgentVerificationEntity forArtifact(String id);
    @Insert("""
            INSERT INTO pm_agent_verification(id,plan_id,run_id,change_set_id,manifest_hash,stage,approved_snapshot_hash,
              snapshot_json,snapshot_hash,status,artifact_id,artifact_hash,conclusion_json,next_check_at,version,created_at,updated_at)
            VALUES(#{id},#{planId},#{runId},#{changeSetId},#{manifestHash},#{stage},#{approvedSnapshotHash},
              #{snapshotJson},#{snapshotHash},#{status},#{artifactId},#{artifactHash},#{conclusionJson},#{nextCheckAt},#{version},#{createdAt},#{updatedAt})
            """)
    int insert(AgentVerificationEntity value);
    @Update("""
            UPDATE pm_agent_verification SET status=#{status},artifact_id=#{artifactId},artifact_hash=#{artifactHash},
              conclusion_json=#{conclusionJson},next_check_at=#{nextCheckAt},version=version+1,updated_at=#{updatedAt}
            WHERE id=#{id} AND version=#{version}
            """)
    int update(AgentVerificationEntity value);
    @Select("SELECT * FROM pm_agent_verification_sample WHERE verification_id=#{id} AND check_id=#{checkId} ORDER BY sample_order")
    List<AgentVerificationSample> samples(@Param("id") String id, @Param("checkId") String checkId);
    @Select("SELECT * FROM pm_agent_verification_sample WHERE verification_id=#{id} AND request_key=#{key}")
    AgentVerificationSample sampleByKey(@Param("id") String id, @Param("key") String key);
    @Insert("""
            INSERT INTO pm_agent_verification_sample(id,verification_id,check_id,sample_order,request_key,request_hash,
              assessment,reason,historical_data_pending,evidence_json,evidence_hash,created_at)
            VALUES(#{id},#{verificationId},#{checkId},#{sampleOrder},#{requestKey},#{requestHash},
              #{assessment},#{reason},#{historicalDataPending},#{evidenceJson},#{evidenceHash},#{createdAt})
            """)
    int insertSample(AgentVerificationSample value);
    @Select("SELECT * FROM pm_agent_verification_call WHERE run_id=#{runId} AND tool_key=#{key}")
    AgentVerificationCall call(@Param("runId") String runId, @Param("key") String key);
    @Insert("""
            INSERT INTO pm_agent_verification_call(run_id,tool_key,tool_code,request_hash,result_json,created_at)
            VALUES(#{runId},#{toolKey},#{toolCode},#{requestHash},#{resultJson},#{createdAt})
            """)
    int insertCall(AgentVerificationCall value);
    @Select("""
            SELECT v.id FROM pm_agent_verification v JOIN pm_agent_run r ON r.id=v.run_id
            WHERE v.status='OBSERVING' AND v.next_check_at<=#{now}
              AND (r.cancel_requested=1 OR r.status IN ('COMPLETED','FAILED','CANCELLED')
                OR (r.status='WAITING_INPUT' AND r.owner_token IS NULL AND r.external_handle IS NULL))
            ORDER BY v.next_check_at,v.id LIMIT 100
            """)
    List<String> due(LocalDateTime now);
}
