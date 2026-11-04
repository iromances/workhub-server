package cn.aslight.workhub.dao.agent;

import cn.aslight.workhub.model.agent.AgentChangeSetEntity;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface AgentChangeMapper {
    String COLUMNS = "id,run_id,task_id,revision,request_key,request_hash,manifest_hash,manifest_json,created_at";
    @Select("SELECT " + COLUMNS + " FROM pm_agent_change_set WHERE id=#{id}")
    AgentChangeSetEntity find(String id);
    @Select("SELECT " + COLUMNS + " FROM pm_agent_change_set WHERE run_id=#{runId} AND request_key=#{key}")
    AgentChangeSetEntity byRequest(@Param("runId") String runId, @Param("key") String key);
    @Select("SELECT " + COLUMNS + " FROM pm_agent_change_set WHERE run_id=#{runId} ORDER BY revision DESC LIMIT #{limit} OFFSET #{offset}")
    List<AgentChangeSetEntity> list(@Param("runId") String runId, @Param("offset") int offset, @Param("limit") int limit);
    @Select("SELECT COUNT(*) FROM pm_agent_change_set WHERE run_id=#{runId}")
    long count(String runId);
    @Select("SELECT COALESCE(MAX(revision),0) FROM pm_agent_change_set WHERE run_id=#{runId}")
    long lastRevision(String runId);
    @Insert("""
            INSERT INTO pm_agent_change_set(id,run_id,task_id,revision,request_key,request_hash,manifest_hash,manifest_json,created_at)
            VALUES(#{id},#{runId},#{taskId},#{revision},#{requestKey},#{requestHash},#{manifestHash},#{manifestJson},#{createdAt})
            """)
    int insert(AgentChangeSetEntity value);
}
