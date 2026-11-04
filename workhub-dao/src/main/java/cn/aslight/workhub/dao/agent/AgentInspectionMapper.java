package cn.aslight.workhub.dao.agent;

import cn.aslight.workhub.model.agent.*;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AgentInspectionMapper {
    String STREAM = "id,task_id,source_type,business_line_code,environment_code,after_id,upper_id,sweep_count,updated_at";
    String ITEM = "id,stream_id,source_id,content_hash,payload_json,status,assigned_run_id,observation_id,created_at,updated_at";

    @Insert("""
            INSERT INTO pm_agent_inspection_stream(id,task_id,source_type,business_line_code,environment_code,updated_at)
            VALUES(#{id},#{taskId},#{sourceType},#{businessLineCode},#{environmentCode},#{updatedAt})
            ON DUPLICATE KEY UPDATE id=id
            """)
    int ensureStream(AgentInspectionStream stream);
    @Select("SELECT " + STREAM + " FROM pm_agent_inspection_stream WHERE id=#{id} FOR UPDATE")
    AgentInspectionStream lockStream(String id);
    @Select("SELECT " + STREAM + " FROM pm_agent_inspection_stream WHERE id=#{id}")
    AgentInspectionStream stream(String id);
    @Update("UPDATE pm_agent_inspection_stream SET after_id=#{after},upper_id=#{upper},sweep_count=sweep_count+#{completed},updated_at=#{now} WHERE id=#{id}")
    int advance(@Param("id") String id, @Param("after") long after, @Param("upper") long upper,
                @Param("completed") int completed, @Param("now") LocalDateTime now);
    @Insert("""
            INSERT INTO pm_agent_inspection_item(stream_id,source_id,content_hash,payload_json,created_at,updated_at)
            VALUES(#{streamId},#{sourceId},#{contentHash},#{payloadJson},#{createdAt},#{updatedAt})
            ON DUPLICATE KEY UPDATE id=id
            """)
    int ingest(AgentInspectionItem item);
    @Select("SELECT " + ITEM + " FROM pm_agent_inspection_item WHERE id=#{id}")
    AgentInspectionItem item(long id);
    @Select("SELECT " + ITEM + " FROM pm_agent_inspection_item WHERE stream_id=#{streamId} AND status='PENDING' ORDER BY id LIMIT 1")
    AgentInspectionItem next(String streamId);
    @Select("SELECT " + ITEM + " FROM pm_agent_inspection_item WHERE stream_id=#{streamId} AND status='NEEDS_INPUT' AND id>#{after} ORDER BY id LIMIT #{limit}")
    List<AgentInspectionItem> pending(@Param("streamId") String streamId, @Param("after") long after, @Param("limit") int limit);
    @Select("SELECT COUNT(*) FROM pm_agent_inspection_item WHERE stream_id=#{streamId} AND status='NEEDS_INPUT'")
    long pendingCount(String streamId);
    @Update("UPDATE pm_agent_inspection_item SET status='PENDING',assigned_run_id=#{runId},observation_id=#{observationId},updated_at=#{now} WHERE id=#{id}")
    int assign(@Param("id") long id, @Param("runId") String runId, @Param("observationId") String observationId, @Param("now") LocalDateTime now);
    @Update("UPDATE pm_agent_inspection_item SET status=#{status},updated_at=#{now} WHERE id=#{id}")
    int acknowledge(@Param("id") long id, @Param("status") String status, @Param("now") LocalDateTime now);
    @Insert("""
            INSERT INTO pm_agent_inspection_analysis(id,item_id,run_id,observation_id,outcome,evidence_pointer,reason,case_id,created_at)
            VALUES(#{id},#{itemId},#{runId},#{observationId},#{outcome},#{evidencePointer},#{reason},#{caseId},#{createdAt})
            """)
    int insertAnalysis(AgentInspectionAnalysis analysis);
    @Select("SELECT id,item_id,run_id,observation_id,outcome,evidence_pointer,reason,case_id,created_at FROM pm_agent_inspection_analysis WHERE item_id=#{id} ORDER BY created_at DESC,id DESC LIMIT 1")
    AgentInspectionAnalysis latestAnalysis(long id);
    @Select("SELECT run_id,tool_key,request_hash,result_json,created_at FROM pm_agent_inspection_call WHERE run_id=#{runId} AND tool_key=#{key}")
    AgentInspectionCall call(@Param("runId") String runId, @Param("key") String key);
    @Insert("INSERT INTO pm_agent_inspection_call(run_id,tool_key,request_hash,result_json,created_at) VALUES(#{runId},#{toolKey},#{requestHash},#{resultJson},#{createdAt})")
    int insertCall(AgentInspectionCall call);
}
