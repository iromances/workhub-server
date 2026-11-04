package cn.aslight.workhub.dao.agent;

import cn.aslight.workhub.model.agent.*;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

/** 所有状态变更使用版本或持有者条件；锁仅覆盖数据库事务，不跨外部调用。 */
@Mapper
public interface AgentMapper {
    String AGENT_COLUMNS = "id, name, configuration_json, enabled, owner_name, version, created_at, updated_at";
    String TASK_COLUMNS = "id, agent_id, request_key, request_hash, specification_json, requested_by, schedule_mode, cron_expression, "
            + "zone_id, misfire_policy, schedule_enabled, next_run_at, version, created_at, updated_at";
    String RUN_COLUMNS = "id, task_id, agent_id, trigger_key, requested_by, configuration_json, specification_json, provider_binding_json, "
            + "status, step_count, elapsed_millis, cancel_requested, owner_token, lease_until, external_handle, "
            + "result_text, error_message, version, created_at, updated_at";

    @Select("SELECT " + AGENT_COLUMNS + " FROM pm_agent_definition WHERE id=#{id}")
    AgentDefinitionEntity findAgent(String id);

    @Select("SELECT " + AGENT_COLUMNS + " FROM pm_agent_definition WHERE id=#{id} FOR UPDATE")
    AgentDefinitionEntity lockAgent(String id);

    @Select("SELECT " + AGENT_COLUMNS + " FROM pm_agent_definition WHERE (#{owner} IS NULL OR owner_name=#{owner}) ORDER BY created_at DESC,id LIMIT #{limit} OFFSET #{offset}")
    List<AgentDefinitionEntity> listAgents(@Param("owner") String owner, @Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM pm_agent_definition WHERE (#{owner} IS NULL OR owner_name=#{owner})")
    long countAgents(String owner);

    @Insert("""
            INSERT INTO pm_agent_definition(id,name,configuration_json,enabled,owner_name,version,created_at,updated_at)
            VALUES (#{id},#{name},#{configurationJson},#{enabled},#{ownerName},#{version},#{createdAt},#{updatedAt})
            """)
    int insertAgent(AgentDefinitionEntity entity);

    @Update("""
            UPDATE pm_agent_definition SET name=#{name},configuration_json=#{configurationJson},version=version+1,updated_at=#{updatedAt}
            WHERE id=#{id} AND version=#{version}
            """)
    int updateAgent(AgentDefinitionEntity entity);

    @Update("UPDATE pm_agent_definition SET enabled=#{enabled},version=version+1,updated_at=#{now} WHERE id=#{id} AND version=#{version}")
    int setEnabled(@Param("id") String id, @Param("version") long version, @Param("enabled") boolean enabled, @Param("now") LocalDateTime now);

    @Select("SELECT " + TASK_COLUMNS + " FROM pm_agent_task WHERE id=#{id}")
    AgentTaskEntity findTask(String id);

    @Select("SELECT " + TASK_COLUMNS + " FROM pm_agent_task WHERE id=#{id} FOR UPDATE")
    AgentTaskEntity lockTask(String id);

    @Select("SELECT " + TASK_COLUMNS + " FROM pm_agent_task WHERE agent_id=#{agentId} AND request_key=#{key}")
    AgentTaskEntity findTaskByKey(@Param("agentId") String agentId, @Param("key") String key);

    @Select("SELECT " + TASK_COLUMNS + " FROM pm_agent_task WHERE agent_id=#{agentId} ORDER BY created_at DESC,id LIMIT #{limit} OFFSET #{offset}")
    List<AgentTaskEntity> listTasks(@Param("agentId") String agentId, @Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM pm_agent_task WHERE agent_id=#{agentId}")
    long countTasks(String agentId);

    @Insert("""
            INSERT INTO pm_agent_task(id,agent_id,request_key,request_hash,specification_json,requested_by,schedule_mode,cron_expression,
              zone_id,misfire_policy,schedule_enabled,next_run_at,version,created_at,updated_at)
            VALUES (#{id},#{agentId},#{requestKey},#{requestHash},#{specificationJson},#{requestedBy},#{scheduleMode},#{cronExpression},
              #{zoneId},#{misfirePolicy},#{scheduleEnabled},#{nextRunAt},#{version},#{createdAt},#{updatedAt})
            """)
    int insertTask(AgentTaskEntity entity);

    @Update("""
            UPDATE pm_agent_task SET schedule_mode=#{scheduleMode},cron_expression=#{cronExpression},zone_id=#{zoneId},
              misfire_policy=#{misfirePolicy},schedule_enabled=#{scheduleEnabled},next_run_at=#{nextRunAt},
              version=version+1,updated_at=#{updatedAt} WHERE id=#{id} AND version=#{version}
            """)
    int updateSchedule(AgentTaskEntity entity);

    @Select("""
            SELECT t.id FROM pm_agent_task t JOIN pm_agent_definition a ON a.id=t.agent_id
            WHERE t.schedule_enabled=1 AND t.next_run_at <= #{now} AND a.enabled=1
            ORDER BY t.next_run_at,t.id LIMIT 100
            """)
    List<String> findDueTaskIds(LocalDateTime now);

    @Update("UPDATE pm_agent_task SET next_run_at=#{next},version=version+1,updated_at=#{now} WHERE id=#{id} AND version=#{version}")
    int advanceSchedule(@Param("id") String id, @Param("version") long version, @Param("next") LocalDateTime next, @Param("now") LocalDateTime now);

    @Select("SELECT " + RUN_COLUMNS + " FROM pm_agent_run WHERE id=#{id}")
    AgentRunEntity findRun(String id);

    @Select("SELECT " + RUN_COLUMNS + " FROM pm_agent_run WHERE id=#{id} FOR UPDATE")
    AgentRunEntity lockRun(String id);

    /** 巡检调用结果与其业务变更同事务；恢复只按原运行及调用键读取，不能跨调用猜测。 */
    @Select("SELECT result_json FROM pm_agent_inspection_call WHERE run_id=#{runId} AND tool_key=#{key}")
    String findInspectionCallResult(@Param("runId") String runId, @Param("key") String key);

    @Select("SELECT " + RUN_COLUMNS + " FROM pm_agent_run WHERE task_id=#{taskId} AND trigger_key=#{key}")
    AgentRunEntity findRunByKey(@Param("taskId") String taskId, @Param("key") String key);

    @Select("SELECT " + RUN_COLUMNS + " FROM pm_agent_run WHERE task_id=#{taskId} ORDER BY created_at DESC,id LIMIT #{limit} OFFSET #{offset}")
    List<AgentRunEntity> listRuns(@Param("taskId") String taskId, @Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM pm_agent_run WHERE task_id=#{taskId}")
    long countRuns(String taskId);

    @Select("SELECT COUNT(*) FROM pm_agent_run WHERE agent_id=#{agentId} AND status IN ('QUEUED','RUNNING','WAITING_RESOURCE','CANCELLING')")
    int countPendingRuns(String agentId);

    @Insert("""
            INSERT INTO pm_agent_run(id,task_id,agent_id,trigger_key,requested_by,configuration_json,specification_json,provider_binding_json,
              status,step_count,elapsed_millis,cancel_requested,version,created_at,updated_at)
            VALUES (#{id},#{taskId},#{agentId},#{triggerKey},#{requestedBy},#{configurationJson},#{specificationJson},#{providerBindingJson},
              #{status},#{stepCount},#{elapsedMillis},#{cancelRequested},#{version},#{createdAt},#{updatedAt})
            """)
    int insertRun(AgentRunEntity entity);

    @Update("""
            UPDATE pm_agent_run SET cancel_requested=1,
              status=CASE WHEN owner_token IS NOT NULL OR external_handle IS NOT NULL THEN 'CANCELLING' ELSE 'CANCELLED' END,
              version=version+1,updated_at=#{now}
            WHERE id=#{id} AND version=#{version} AND status NOT IN ('COMPLETED','FAILED','CANCELLED')
            """)
    int requestCancel(@Param("id") String id, @Param("version") long version, @Param("now") LocalDateTime now);

    @Select("SELECT id FROM pm_agent_run WHERE agent_id=#{agentId} AND status NOT IN ('COMPLETED','FAILED','CANCELLED') ORDER BY id")
    List<String> findActiveRunIds(String agentId);

    @Update("""
            UPDATE pm_agent_run SET status=CASE WHEN status='WAITING_INPUT' THEN 'QUEUED' ELSE status END,
              version=version+1,updated_at=#{now} WHERE id=#{id} AND version=#{version}
              AND status IN ('QUEUED','RUNNING','WAITING_INPUT','WAITING_RESOURCE') AND cancel_requested=0
            """)
    int acceptMessage(@Param("id") String id, @Param("version") long version, @Param("now") LocalDateTime now);

    @Select("SELECT id FROM pm_agent_dispatch_lock WHERE id='DISPATCH' FOR UPDATE")
    String lockDispatcher();

    @Select("SELECT id FROM pm_agent_run WHERE status='QUEUED' AND owner_token IS NULL AND external_handle IS NULL AND cancel_requested=0 ORDER BY created_at,id LIMIT 100")
    List<String> findQueuedRunIds();

    @Select("SELECT COUNT(*) FROM pm_agent_run WHERE owner_token IS NOT NULL AND (#{agentId} IS NULL OR agent_id=#{agentId})")
    int countOwnedRuns(String agentId);

    @Update("""
            UPDATE pm_agent_run SET status='RUNNING',owner_token=#{owner},lease_until=#{lease},version=version+1,updated_at=#{now}
            WHERE id=#{id} AND status='QUEUED' AND owner_token IS NULL AND external_handle IS NULL AND cancel_requested=0
            """)
    int claimRun(@Param("id") String id, @Param("owner") String owner, @Param("lease") LocalDateTime lease, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE pm_agent_run SET lease_until=#{lease},elapsed_millis=#{elapsed},updated_at=#{now}
            WHERE id=#{id} AND owner_token=#{owner} AND lease_until>#{now} AND status IN ('RUNNING','CANCELLING')
              AND elapsed_millis<=#{elapsed}
            """)
    int heartbeat(@Param("id") String id, @Param("owner") String owner, @Param("lease") LocalDateTime lease,
                  @Param("elapsed") long elapsed, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE pm_agent_run SET step_count=step_count+1,version=version+1,updated_at=#{now}
            WHERE id=#{id} AND owner_token=#{owner} AND cancel_requested=0 AND status='RUNNING' AND lease_until>#{now}
            """)
    int incrementStep(@Param("id") String id, @Param("owner") String owner, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE pm_agent_run SET external_handle=#{handle},version=version+1,updated_at=#{now}
            WHERE id=#{id} AND owner_token=#{owner} AND status IN ('RUNNING','CANCELLING') AND lease_until>#{now}
            """)
    int setExternalHandle(@Param("id") String id, @Param("owner") String owner, @Param("handle") String handle, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE pm_agent_run SET status=#{status},result_text=#{result},error_message=#{error},owner_token=NULL,
              lease_until=NULL,version=version+1,updated_at=#{now}
            WHERE id=#{id} AND owner_token=#{owner} AND external_handle IS NULL AND lease_until>#{now}
            """)
    int finishRun(@Param("id") String id, @Param("owner") String owner, @Param("status") String status,
                  @Param("result") String result, @Param("error") String error, @Param("now") LocalDateTime now);

    @Select("SELECT id FROM pm_agent_run WHERE owner_token IS NOT NULL AND lease_until<=#{now} AND status IN ('RUNNING','CANCELLING','WAITING_RESOURCE') ORDER BY updated_at,id LIMIT 100")
    List<String> findExpiredRunIds(LocalDateTime now);

    @Update("""
            UPDATE pm_agent_run SET status=#{status},owner_token=#{owner},lease_until=#{lease},external_handle=#{handle},error_message=#{error},
              version=version+1,updated_at=#{now} WHERE id=#{id} AND version=#{version}
            """)
    int recoverRun(@Param("id") String id, @Param("version") long version, @Param("status") String status,
                   @Param("owner") String owner, @Param("lease") LocalDateTime lease,
                   @Param("handle") String handle, @Param("error") String error, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE pm_agent_run SET status=#{status},cancel_requested=#{cancelled},owner_token=NULL,lease_until=NULL,
              external_handle=NULL,error_message=#{message},version=version+1,updated_at=#{now}
            WHERE id=#{id} AND version=#{version} AND owner_token IS NOT NULL AND external_handle IS NOT NULL
              AND lease_until<=#{now} AND status IN ('RUNNING','CANCELLING','WAITING_RESOURCE')
            """)
    int reconcileRun(@Param("id") String id, @Param("version") long version, @Param("status") String status,
                     @Param("cancelled") boolean cancelled, @Param("message") String message, @Param("now") LocalDateTime now);

    @Select("SELECT id,run_id,event_key,ordinal,kind,payload_json,actor,created_at FROM pm_agent_event WHERE run_id=#{runId} ORDER BY ordinal LIMIT 5000")
    List<AgentEventEntity> listEvents(String runId);

    @Select("SELECT id,run_id,event_key,ordinal,kind,payload_json,actor,created_at FROM pm_agent_event WHERE run_id=#{runId} AND event_key=#{key}")
    AgentEventEntity findEventByKey(@Param("runId") String runId, @Param("key") String key);

    @Select("SELECT COALESCE(MAX(ordinal),0)+1 FROM pm_agent_event WHERE run_id=#{runId}")
    long nextEventOrdinal(String runId);

    @Insert("""
            INSERT INTO pm_agent_event(id,run_id,event_key,ordinal,kind,payload_json,actor,created_at)
            VALUES (#{id},#{runId},#{eventKey},#{ordinal},#{kind},#{payloadJson},#{actor},#{createdAt})
            """)
    int insertEvent(AgentEventEntity event);

    @Select("SELECT id,run_id,artifact_key,kind,provenance,payload_json,content_hash,created_at FROM pm_agent_artifact WHERE run_id=#{runId} ORDER BY created_at,id LIMIT 500")
    List<AgentArtifactEntity> listArtifacts(String runId);

    @Select("SELECT id,run_id,artifact_key,kind,provenance,payload_json,content_hash,created_at FROM pm_agent_artifact WHERE run_id=#{runId} AND artifact_key=#{key}")
    AgentArtifactEntity findArtifact(@Param("runId") String runId, @Param("key") String key);

    @Insert("""
            INSERT INTO pm_agent_artifact(id,run_id,artifact_key,kind,provenance,payload_json,content_hash,created_at)
            VALUES (#{id},#{runId},#{artifactKey},#{kind},#{provenance},#{payloadJson},#{contentHash},#{createdAt})
            """)
    int insertArtifact(AgentArtifactEntity artifact);

    @Insert("""
            INSERT INTO pm_agent_audit(id,object_type,object_id,action,actor,payload_json,created_at)
            VALUES (#{id},#{type},#{objectId},#{action},#{actor},#{payload},#{now})
            """)
    int insertAudit(@Param("id") String id, @Param("type") String type, @Param("objectId") String objectId,
                    @Param("action") String action, @Param("actor") String actor, @Param("payload") String payload,
                    @Param("now") LocalDateTime now);
}
