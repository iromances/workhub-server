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

    @Update("""
            <script>
            UPDATE pm_agent_task SET specification_json=#{specificationJson},
              <if test="schedule != null">schedule_mode=#{schedule.mode},cron_expression=#{schedule.cronExpression},zone_id=#{schedule.zoneId},
                misfire_policy=#{schedule.misfirePolicy},schedule_enabled=#{schedule.enabled},next_run_at=#{nextRunAt},</if>
              version=version+1,updated_at=#{updatedAt} WHERE id=#{id} AND version=#{version}
            </script>
            """)
    int updateTaskSpecification(@Param("id") String id, @Param("version") long version,
                                @Param("specificationJson") String specificationJson, @Param("schedule") AgentScheduleRequest schedule,
                                @Param("nextRunAt") LocalDateTime nextRunAt, @Param("updatedAt") LocalDateTime updatedAt);

    /** 删除前核对：未结束运行（含排队/执行中/待核对）阻止物理删除。 */
    @Select("SELECT COUNT(*) FROM pm_agent_run WHERE task_id=#{taskId} AND status NOT IN ('COMPLETED','FAILED','CANCELLED','WAITING_INPUT')")
    int countUnfinishedRuns(@Param("taskId") String taskId);

    /** 删除前核对：运行挂接的过程数据与将一并清除的轨迹行数，任一过程数据非零即拒绝删除。 */
    @Select("""
            SELECT (SELECT COUNT(*) FROM pm_agent_event e WHERE e.run_id IN (SELECT id FROM pm_agent_run WHERE task_id=#{taskId})) AS events,
                   (SELECT COUNT(*) FROM pm_agent_artifact a WHERE a.run_id IN (SELECT id FROM pm_agent_run WHERE task_id=#{taskId})) AS artifacts,
                   (SELECT COUNT(*) FROM pm_agent_observation o JOIN pm_agent_run r ON o.run_id=r.id WHERE r.task_id=#{taskId}) AS observations,
                   (SELECT COUNT(*) FROM pm_agent_change_set c JOIN pm_agent_run r2 ON c.run_id=r2.id WHERE r2.task_id=#{taskId}) AS changeSets,
                   (SELECT COUNT(*) FROM pm_agent_code_workspace w JOIN pm_agent_run r3 ON w.run_id=r3.id WHERE r3.task_id=#{taskId}) AS codeWorkspaces,
                   (SELECT COUNT(*) FROM pm_agent_build_test b JOIN pm_agent_run r4 ON b.run_id=r4.id WHERE r4.task_id=#{taskId}) AS buildTests,
                   (SELECT COUNT(*) FROM pm_agent_release_plan p JOIN pm_agent_run r5 ON p.run_id=r5.id WHERE r5.task_id=#{taskId}) AS releasePlans,
                   (SELECT COUNT(*) FROM pm_agent_deployment dp JOIN pm_agent_run r6 ON dp.run_id=r6.id WHERE r6.task_id=#{taskId}) AS deployments,
                   (SELECT COUNT(*) FROM pm_agent_verification v JOIN pm_agent_run r7 ON v.run_id=r7.id WHERE r7.task_id=#{taskId}) AS verifications,
                   (SELECT COUNT(*) FROM pm_agent_inspection_item i JOIN pm_agent_run r8 ON i.assigned_run_id=r8.id WHERE r8.task_id=#{taskId}) AS inspectionItems,
                   (SELECT COUNT(*) FROM pm_autonomy_case_run cr JOIN pm_agent_run r9 ON cr.run_id=r9.id WHERE r9.task_id=#{taskId}) AS autonomyCases
            """)
    TaskRemovalCounts taskRemovalCounts(@Param("taskId") String taskId);

    @Delete("DELETE FROM pm_agent_artifact WHERE run_id IN (SELECT id FROM pm_agent_run WHERE task_id=#{taskId})")
    int deleteArtifactsByTask(@Param("taskId") String taskId);

    @Delete("DELETE FROM pm_agent_event WHERE run_id IN (SELECT id FROM pm_agent_run WHERE task_id=#{taskId})")
    int deleteEventsByTask(@Param("taskId") String taskId);

    @Delete("DELETE FROM pm_agent_run WHERE task_id=#{taskId}")
    int deleteRunsByTask(@Param("taskId") String taskId);

    @Delete("DELETE FROM pm_agent_task WHERE id=#{id} AND version=#{version}")
    int deleteTaskById(@Param("id") String id, @Param("version") long version);

    @Delete("DELETE FROM pm_agent_artifact WHERE run_id=#{runId}")
    int deleteArtifactsByRun(@Param("runId") String runId);

    @Delete("DELETE FROM pm_agent_event WHERE run_id=#{runId}")
    int deleteEventsByRun(@Param("runId") String runId);

    @Delete("DELETE FROM pm_agent_run WHERE id=#{id} AND version=#{version}")
    int deleteRunById(@Param("id") String id, @Param("version") long version);

    /** Agent 删除前核对：未结束运行与启用中周期任务阻止物理删除。 */
    @Select("SELECT COUNT(*) FROM pm_agent_run WHERE agent_id=#{agentId} AND status NOT IN ('COMPLETED','FAILED','CANCELLED','WAITING_INPUT')")
    int countUnfinishedRunsByAgent(@Param("agentId") String agentId);

    @Select("SELECT COUNT(*) FROM pm_agent_run WHERE agent_id=#{agentId}")
    long countRunsByAgent(@Param("agentId") String agentId);

    @Select("SELECT COUNT(*) FROM pm_agent_task WHERE agent_id=#{agentId} AND schedule_enabled=1")
    int countEnabledSchedulesByAgent(@Param("agentId") String agentId);

    /** Agent 删除前核对：全部运行挂接的过程数据，与任务删除同类别同口径。 */
    @Select("""
            SELECT (SELECT COUNT(*) FROM pm_agent_event e WHERE e.run_id IN (SELECT id FROM pm_agent_run WHERE agent_id=#{agentId})) AS events,
                   (SELECT COUNT(*) FROM pm_agent_artifact a WHERE a.run_id IN (SELECT id FROM pm_agent_run WHERE agent_id=#{agentId})) AS artifacts,
                   (SELECT COUNT(*) FROM pm_agent_observation o WHERE o.agent_id=#{agentId}) AS observations,
                   (SELECT COUNT(*) FROM pm_agent_change_set c JOIN pm_agent_run r2 ON c.run_id=r2.id WHERE r2.agent_id=#{agentId}) AS changeSets,
                   (SELECT COUNT(*) FROM pm_agent_code_workspace w JOIN pm_agent_run r3 ON w.run_id=r3.id WHERE r3.agent_id=#{agentId}) AS codeWorkspaces,
                   (SELECT COUNT(*) FROM pm_agent_build_test b JOIN pm_agent_run r4 ON b.run_id=r4.id WHERE r4.agent_id=#{agentId}) AS buildTests,
                   (SELECT COUNT(*) FROM pm_agent_release_plan p JOIN pm_agent_run r5 ON p.run_id=r5.id WHERE r5.agent_id=#{agentId}) AS releasePlans,
                   (SELECT COUNT(*) FROM pm_agent_deployment dp JOIN pm_agent_run r6 ON dp.run_id=r6.id WHERE r6.agent_id=#{agentId}) AS deployments,
                   (SELECT COUNT(*) FROM pm_agent_verification v JOIN pm_agent_run r7 ON v.run_id=r7.id WHERE r7.agent_id=#{agentId}) AS verifications,
                   (SELECT COUNT(*) FROM pm_agent_inspection_item i JOIN pm_agent_run r8 ON i.assigned_run_id=r8.id WHERE r8.agent_id=#{agentId}) AS inspectionItems,
                   (SELECT COUNT(*) FROM pm_autonomy_case_run cr JOIN pm_agent_run r9 ON cr.run_id=r9.id WHERE r9.agent_id=#{agentId}) AS autonomyCases
            """)
    TaskRemovalCounts agentRemovalCounts(@Param("agentId") String agentId);

    @Delete("DELETE FROM pm_agent_artifact WHERE run_id IN (SELECT id FROM pm_agent_run WHERE agent_id=#{agentId})")
    int deleteArtifactsByAgent(@Param("agentId") String agentId);

    @Delete("DELETE FROM pm_agent_event WHERE run_id IN (SELECT id FROM pm_agent_run WHERE agent_id=#{agentId})")
    int deleteEventsByAgent(@Param("agentId") String agentId);

    @Delete("DELETE FROM pm_agent_run WHERE agent_id=#{agentId}")
    int deleteRunsByAgent(@Param("agentId") String agentId);

    @Delete("DELETE FROM pm_agent_task WHERE agent_id=#{agentId}")
    int deleteTasksByAgent(@Param("agentId") String agentId);

    @Delete("DELETE FROM pm_agent_definition WHERE id=#{id} AND version=#{version}")
    int deleteAgentById(@Param("id") String id, @Param("version") long version);

    record TaskRemovalCounts(long events, long artifacts, long observations, long changeSets, long codeWorkspaces,
                             long buildTests, long releasePlans, long deployments, long verifications,
                             long inspectionItems, long autonomyCases) {
        public boolean hasProcessData() {
            return observations + changeSets + codeWorkspaces + buildTests + releasePlans + deployments
                    + verifications + inspectionItems + autonomyCases > 0;
        }
        public String processCategories() {
            var names = new java.util.ArrayList<String>();
            if (observations > 0) names.add("巡检观测");
            if (changeSets > 0) names.add("候选变更");
            if (codeWorkspaces > 0) names.add("代码工作区");
            if (buildTests > 0) names.add("构建测试");
            if (releasePlans > 0) names.add("发布计划");
            if (deployments > 0) names.add("部署回执");
            if (verifications > 0) names.add("业务复查");
            if (inspectionItems > 0) names.add("巡检分配");
            if (autonomyCases > 0) names.add("问题协作");
            return String.join("、", names);
        }
    }

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

    @Select("SELECT result_json FROM pm_agent_verification_call WHERE run_id=#{runId} AND tool_key=#{key} AND tool_code=#{tool}")
    String findVerificationCallResult(@Param("runId") String runId, @Param("key") String key, @Param("tool") String tool);

    @Select("SELECT result_json FROM pm_agent_build_test_call WHERE run_id=#{runId} AND tool_key=#{key} AND tool_code=#{tool}")
    String findBuildTestCallResult(@Param("runId") String runId,@Param("key") String key,@Param("tool") String tool);

    @Select("SELECT " + RUN_COLUMNS + " FROM pm_agent_run WHERE task_id=#{taskId} AND trigger_key=#{key}")
    AgentRunEntity findRunByKey(@Param("taskId") String taskId, @Param("key") String key);

    @Select("SELECT " + RUN_COLUMNS + " FROM pm_agent_run WHERE task_id=#{taskId} ORDER BY created_at DESC,id LIMIT #{limit} OFFSET #{offset}")
    List<AgentRunEntity> listRuns(@Param("taskId") String taskId, @Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM pm_agent_run WHERE task_id=#{taskId}")
    long countRuns(String taskId);

    /** 增量数据窗口基线：本任务最近一次已完成运行（排除当前运行）的开始时间。 */
    @Select("SELECT created_at FROM pm_agent_run WHERE task_id=#{taskId} AND id<>#{runId} AND status='COMPLETED' ORDER BY created_at DESC LIMIT 1")
    LocalDateTime lastCompletedRunStart(@Param("taskId") String taskId, @Param("runId") String runId);

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

    /** 只统计租约仍存活的运行；执行器失联后被恢复为 WAITING_RESOURCE 的残留 owner（租约已过期）不再占用并发额度。 */
    @Select("SELECT COUNT(*) FROM pm_agent_run WHERE owner_token IS NOT NULL AND lease_until>#{now} AND (#{agentId} IS NULL OR agent_id=#{agentId})")
    int countOwnedRuns(@Param("agentId") String agentId, @Param("now") LocalDateTime now);

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
