package cn.aslight.workhub.dao.agent;

import cn.aslight.workhub.model.agent.*;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AgentBuildTestMapper {
    @Select("SELECT * FROM pm_agent_build_test WHERE id=#{id}")
    AgentBuildTestEntity find(String id);
    @Select("SELECT * FROM pm_agent_build_test WHERE id=#{id} FOR UPDATE")
    AgentBuildTestEntity lock(String id);
    @Select("SELECT * FROM pm_agent_build_test WHERE run_id=#{runId} AND request_key=#{key}")
    AgentBuildTestEntity byKey(@Param("runId") String runId,@Param("key") String key);
    @Select("SELECT * FROM pm_agent_build_test WHERE change_set_id=#{id} AND binding_id=#{binding} ORDER BY attempt DESC LIMIT 1")
    AgentBuildTestEntity latest(@Param("id") String id,@Param("binding") String binding);
    @Insert("""
            INSERT INTO pm_agent_build_test(id,run_id,change_set_id,binding_id,attempt,request_key,request_hash,manifest_hash,
              binding_json,binding_hash,parameters_json,parameters_hash,status,next_check_at,wake_pending,version,created_at,updated_at)
            VALUES(#{id},#{runId},#{changeSetId},#{bindingId},#{attempt},#{requestKey},#{requestHash},#{manifestHash},
              #{bindingJson},#{bindingHash},#{parametersJson},#{parametersHash},#{status},#{nextCheckAt},#{wakePending},#{version},#{createdAt},#{updatedAt})
            """)
    int insert(AgentBuildTestEntity row);
    @Select("""
            SELECT id FROM pm_agent_build_test WHERE status IN ('PREPARED','SUBMITTING','QUEUED','BUILDING','AWAITING_REPORT','UNKNOWN')
              AND next_check_at<=#{now} AND (owner_token IS NULL OR lease_until<=#{now}) ORDER BY next_check_at,id LIMIT 20
            """)
    List<String> due(LocalDateTime now);
    @Select("SELECT COUNT(*) FROM pm_agent_build_test WHERE owner_token IS NOT NULL AND lease_until>#{now}")
    int countOwned(LocalDateTime now);
    @Update("UPDATE pm_agent_build_test SET owner_token=#{owner},lease_until=#{lease},version=version+1,updated_at=#{now} WHERE id=#{id} AND version=#{version}")
    int claim(@Param("id") String id,@Param("version") long version,@Param("owner") String owner,@Param("lease") LocalDateTime lease,@Param("now") LocalDateTime now);
    @Update("UPDATE pm_agent_build_test SET status='SUBMITTING',version=version+1,updated_at=#{now} WHERE id=#{id} AND version=#{version} AND owner_token=#{owner} AND status='PREPARED'")
    int intent(@Param("id") String id,@Param("version") long version,@Param("owner") String owner,@Param("now") LocalDateTime now);
    @Update("""
            UPDATE pm_agent_build_test SET status=#{status},queue_id=#{queue},build_number=#{build},build_result=#{result},receipt_json=#{receipt},
              error_message=#{error},owner_token=NULL,lease_until=NULL,next_check_at=#{next},version=version+1,updated_at=#{now}
            WHERE id=#{id} AND version=#{version} AND owner_token=#{owner}
            """)
    int finish(@Param("id") String id,@Param("version") long version,@Param("owner") String owner,@Param("status") String status,
            @Param("queue") Long queue,@Param("build") Long build,@Param("result") String result,@Param("receipt") String receipt,
            @Param("error") String error,@Param("next") LocalDateTime next,@Param("now") LocalDateTime now);
    @Select("""
            SELECT t.id FROM pm_agent_build_test t JOIN pm_agent_run r ON r.id=t.run_id WHERE t.wake_pending=TRUE
              AND t.status IN ('SUCCEEDED','FAILED','ABORTED','REJECTED','BLOCKED')
              AND (r.status IN ('COMPLETED','FAILED','CANCELLED') OR r.cancel_requested=TRUE
                OR (r.status='WAITING_INPUT' AND r.owner_token IS NULL AND r.external_handle IS NULL)) ORDER BY t.updated_at,t.id LIMIT 20
            """)
    List<String> wakeDue();
    @Update("UPDATE pm_agent_build_test SET wake_pending=FALSE WHERE id=#{id} AND wake_pending=TRUE")
    int acknowledgeWake(String id);
    @Select("SELECT * FROM pm_agent_build_test_call WHERE run_id=#{runId} AND tool_key=#{key}")
    AgentBuildTestCall call(@Param("runId") String runId,@Param("key") String key);
    @Insert("INSERT INTO pm_agent_build_test_call(run_id,tool_key,tool_code,request_hash,result_json,created_at) VALUES(#{runId},#{toolKey},#{toolCode},#{requestHash},#{resultJson},#{createdAt})")
    int insertCall(AgentBuildTestCall value);
}
