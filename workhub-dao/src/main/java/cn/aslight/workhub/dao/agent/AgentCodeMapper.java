package cn.aslight.workhub.dao.agent;

import cn.aslight.workhub.model.agent.AgentCodeOperationEntity;
import cn.aslight.workhub.model.agent.AgentCodeWorkspaceEntity;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AgentCodeMapper {
    String COLUMNS = "id,run_id,repository_id,policy_hash,host_id,base_branch,base_sha,candidate_sha,source_branch,status,version,created_at,updated_at";

    @Select("SELECT " + COLUMNS + " FROM pm_agent_code_workspace WHERE id=#{id}")
    AgentCodeWorkspaceEntity find(String id);

    @Select("SELECT " + COLUMNS + " FROM pm_agent_code_workspace WHERE id=#{id} FOR UPDATE")
    AgentCodeWorkspaceEntity lock(String id);

    @Select("SELECT " + COLUMNS + " FROM pm_agent_code_workspace WHERE run_id=#{runId} AND repository_id=#{repositoryId}")
    AgentCodeWorkspaceEntity findByRepository(@Param("runId") String runId, @Param("repositoryId") String repositoryId);

    @Select("SELECT " + COLUMNS + " FROM pm_agent_code_workspace WHERE run_id=#{runId} ORDER BY repository_id")
    List<AgentCodeWorkspaceEntity> list(String runId);

    @Insert("""
            INSERT INTO pm_agent_code_workspace(id,run_id,repository_id,policy_hash,host_id,base_branch,base_sha,candidate_sha,source_branch,status,version,created_at,updated_at)
            VALUES (#{id},#{runId},#{repositoryId},#{policyHash},#{hostId},#{baseBranch},#{baseSha},#{candidateSha},#{sourceBranch},#{status},#{version},#{createdAt},#{updatedAt})
            """)
    int insert(AgentCodeWorkspaceEntity workspace);

    @Update("UPDATE pm_agent_code_workspace SET status='READY',updated_at=#{now} WHERE id=#{id} AND status='PREPARING'")
    int ready(@Param("id") String id, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE pm_agent_code_workspace SET candidate_sha=#{sha},version=version+1,updated_at=#{now}
            WHERE id=#{id} AND version=#{version} AND status='READY'
            """)
    int revise(@Param("id") String id, @Param("version") long version, @Param("sha") String sha, @Param("now") LocalDateTime now);

    @Select("SELECT workspace_id,request_key,request_hash,result_json,created_at FROM pm_agent_code_operation WHERE workspace_id=#{id} AND request_key=#{key}")
    AgentCodeOperationEntity operation(@Param("id") String id, @Param("key") String key);

    @Insert("INSERT INTO pm_agent_code_operation(workspace_id,request_key,request_hash,result_json,created_at) VALUES(#{workspaceId},#{requestKey},#{requestHash},#{resultJson},#{createdAt})")
    int record(AgentCodeOperationEntity operation);
}
