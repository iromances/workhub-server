package cn.aslight.workhub.dao.agent;

import cn.aslight.workhub.model.agent.AgentDeliveryEntity;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AgentDeliveryMapper {
    String COLUMNS = "id,change_set_id,run_id,repository_id,binding_json,binding_hash,source_branch,target_branch,result_sha,status,merge_request_iid,merge_request_url,error_message,verified_at,version,created_at,updated_at";
    @Select("SELECT " + COLUMNS + " FROM pm_agent_delivery WHERE id=#{id}")
    AgentDeliveryEntity find(String id);
    @Select("SELECT " + COLUMNS + " FROM pm_agent_delivery WHERE change_set_id=#{changeSetId} AND repository_id=#{repositoryId}")
    AgentDeliveryEntity byRepository(@Param("changeSetId") String changeSetId, @Param("repositoryId") String repositoryId);
    @Select("SELECT " + COLUMNS + " FROM pm_agent_delivery WHERE change_set_id=#{changeSetId} ORDER BY repository_id")
    List<AgentDeliveryEntity> list(String changeSetId);
    @Select("SELECT COUNT(*) FROM pm_agent_delivery WHERE run_id=#{runId} AND repository_id=#{repositoryId} AND status IN ('PUSHING','PUSH_UNKNOWN','MR_CREATING','MR_UNKNOWN')")
    long unresolved(@Param("runId") String runId, @Param("repositoryId") String repositoryId);
    @Insert("""
            INSERT INTO pm_agent_delivery(id,change_set_id,run_id,repository_id,binding_json,binding_hash,source_branch,target_branch,result_sha,status,version,created_at,updated_at)
            VALUES(#{id},#{changeSetId},#{runId},#{repositoryId},#{bindingJson},#{bindingHash},#{sourceBranch},#{targetBranch},#{resultSha},#{status},#{version},#{createdAt},#{updatedAt})
            """)
    int insert(AgentDeliveryEntity value);
    @Update("""
            UPDATE pm_agent_delivery SET status=#{status},merge_request_iid=#{iid},merge_request_url=#{url},error_message=#{error},
                verified_at=#{verifiedAt},version=version+1,updated_at=#{now}
            WHERE id=#{id} AND version=#{version}
            """)
    int update(@Param("id") String id, @Param("version") long version, @Param("status") String status,
               @Param("iid") Long iid, @Param("url") String url, @Param("error") String error,
               @Param("verifiedAt") LocalDateTime verifiedAt, @Param("now") LocalDateTime now);
}
