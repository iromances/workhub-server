package cn.aslight.workhub.dao.agent;

import cn.aslight.workhub.model.agent.*;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AgentDeploymentMapper {
    String COLUMNS = "id,approval_id,run_id,change_set_id,manifest_hash,binding_json,binding_hash,parameters_json,parameters_hash,status,queue_id,build_number,build_result,receipt_json,error_message,owner_token,lease_until,next_check_at,version,created_at,updated_at";
    @Select("SELECT * FROM pm_agent_release_approval WHERE id=#{id}")
    AgentReleaseApprovalEntity approval(String id);
    @Insert("""
            INSERT INTO pm_agent_release_approval(id,request_id,run_id,change_set_id,manifest_hash,binding_json,binding_hash,parameters_json,parameters_hash,decision,reviewed_by,comment,snapshot_json,reviewed_at)
            VALUES(#{id},#{requestId},#{runId},#{changeSetId},#{manifestHash},#{bindingJson},#{bindingHash},#{parametersJson},#{parametersHash},#{decision},#{reviewedBy},#{comment},#{snapshotJson},#{reviewedAt})
            """)
    int insertApproval(AgentReleaseApprovalEntity value);
    @Select("SELECT id FROM pm_agent_dispatch_lock WHERE id='RELEASE' FOR UPDATE")
    String lockDispatcher();
    @Select("SELECT " + COLUMNS + " FROM pm_agent_deployment WHERE id=#{id}")
    AgentDeploymentEntity find(String id);
    @Select("SELECT " + COLUMNS + " FROM pm_agent_deployment WHERE id=#{id} FOR UPDATE")
    AgentDeploymentEntity lock(String id);
    @Select("SELECT " + COLUMNS + " FROM pm_agent_deployment WHERE change_set_id=#{id} ORDER BY created_at,id")
    List<AgentDeploymentEntity> list(String id);
    @Insert("""
            INSERT INTO pm_agent_deployment(id,approval_id,run_id,change_set_id,manifest_hash,binding_json,binding_hash,parameters_json,parameters_hash,status,next_check_at,version,created_at,updated_at)
            VALUES(#{id},#{approvalId},#{runId},#{changeSetId},#{manifestHash},#{bindingJson},#{bindingHash},#{parametersJson},#{parametersHash},#{status},#{nextCheckAt},#{version},#{createdAt},#{updatedAt})
            """)
    int insert(AgentDeploymentEntity value);
    @Select("SELECT request_id FROM pm_agent_release_resource WHERE resource_key=#{key}")
    String resourceOwner(String key);
    @Insert("INSERT INTO pm_agent_release_resource(resource_key,request_id,created_at) VALUES(#{key},#{requestId},#{now})")
    int acquire(@Param("key") String key, @Param("requestId") String requestId, @Param("now") LocalDateTime now);
    @Delete("DELETE FROM pm_agent_release_resource WHERE request_id=#{id}")
    int release(String id);
    @Select("""
            SELECT d.id FROM pm_agent_deployment d
            WHERE d.status IN ('PREPARED','SUBMITTING','QUEUED','BUILDING','DEPLOYING','UNKNOWN') AND d.next_check_at<=#{now}
              AND (d.owner_token IS NULL OR d.lease_until<=#{now})
              AND (d.status<>'PREPARED' OR NOT EXISTS (
                SELECT 1 FROM pm_agent_release_step_dependency e JOIN pm_agent_deployment p ON p.id=e.depends_on_request_id
                WHERE e.request_id=d.id AND p.status<>'SUCCEEDED'))
            ORDER BY d.next_check_at,d.id LIMIT 20
            """)
    List<String> due(LocalDateTime now);
    @Select("SELECT COUNT(*) FROM pm_agent_deployment WHERE owner_token IS NOT NULL AND lease_until>#{now}")
    int countOwned(LocalDateTime now);
    @Update("""
            UPDATE pm_agent_deployment SET owner_token=#{owner},lease_until=#{lease},version=version+1,updated_at=#{now}
            WHERE id=#{id} AND version=#{version}
            """)
    int claim(@Param("id") String id,@Param("version") long version,@Param("owner") String owner,@Param("lease") LocalDateTime lease,@Param("now") LocalDateTime now);
    @Update("""
            UPDATE pm_agent_deployment SET status='SUBMITTING',version=version+1,updated_at=#{now}
            WHERE id=#{id} AND version=#{version} AND owner_token=#{owner} AND status='PREPARED'
            """)
    int intent(@Param("id") String id,@Param("version") long version,@Param("owner") String owner,@Param("now") LocalDateTime now);
    @Update("""
            UPDATE pm_agent_deployment SET status=#{status},queue_id=#{queue},build_number=#{build},build_result=#{result},receipt_json=#{receipt},error_message=#{error},
              owner_token=NULL,lease_until=NULL,next_check_at=#{next},version=version+1,updated_at=#{now}
            WHERE id=#{id} AND version=#{version} AND owner_token=#{owner}
            """)
    int finish(@Param("id") String id,@Param("version") long version,@Param("owner") String owner,@Param("status") String status,
               @Param("queue") Long queue,@Param("build") Long build,@Param("result") String result,@Param("receipt") String receipt,
               @Param("error") String error,@Param("next") LocalDateTime next,@Param("now") LocalDateTime now);
}
