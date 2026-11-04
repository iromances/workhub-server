package cn.aslight.workhub.dao.agent;

import cn.aslight.workhub.dao.ops.AccountingRunMapper;
import cn.aslight.workhub.model.agent.AgentBugRecord;
import cn.aslight.workhub.model.ops.AccountingResultEntity;
import cn.aslight.workhub.model.ops.AccountingRunEntity;
import cn.aslight.workhub.model.ops.SystemAlertEventResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

/** Agent 读取现有监测历史；范围在 SQL 中限定，续页不依赖会被新记录移动的 offset。 */
@Mapper
public interface AgentMonitorReadMapper {
    String BUG_COLUMNS = "w.id,w.work_item_no AS no,w.project_id AS projectId,p.project_name AS projectName,"
            + "p.business_line_code AS businessLineCode,w.title,w.status,w.priority,w.source_type AS sourceType,"
            + "w.source_channel AS sourceChannel,";

    @Select("SELECT " + BUG_COLUMNS + """
            NULL AS description,NULL AS acceptanceCriteria,w.created_at AS createdAt,w.updated_at AS updatedAt
            FROM pm_work_item w JOIN pm_project p ON p.id=w.project_id
            WHERE p.business_line_code=#{line} AND w.work_item_type='缺陷'
              AND (#{status} IS NULL OR w.status=#{status}) AND (#{beforeId}=0 OR w.id<#{beforeId})
            ORDER BY w.id DESC LIMIT #{limit}
            """)
    List<AgentBugRecord> bugs(@Param("line") String line, @Param("status") String status,
            @Param("beforeId") long beforeId, @Param("limit") int limit);

    @Select("SELECT " + BUG_COLUMNS + """
            w.description,w.acceptance_criteria AS acceptanceCriteria,w.created_at AS createdAt,w.updated_at AS updatedAt
            FROM pm_work_item w JOIN pm_project p ON p.id=w.project_id
            WHERE p.business_line_code=#{line} AND w.work_item_type='缺陷' AND w.id=#{id}
            """)
    AgentBugRecord bug(@Param("line") String line, @Param("id") long id);

    @Select("SELECT " + AccountingRunMapper.RUN_COLUMNS + """
            FROM ops_account_reconcile_run r
            LEFT JOIN ops_account_monitor_config c ON c.id = r.monitor_config_id
            LEFT JOIN ops_account_execution_evidence e ON e.run_id=r.id
            LEFT JOIN pm_business_line bl ON bl.business_line_code=COALESCE(e.business_line_code,c.business_line_code)
            WHERE COALESCE(e.business_line_code,c.business_line_code)=#{line} AND COALESCE(e.environment_code,c.environment_code)=#{environment}
              AND (#{configId} IS NULL OR r.monitor_config_id=#{configId})
              AND (#{beforeId}=0 OR r.id < #{beforeId})
            ORDER BY r.id DESC LIMIT #{limit}
            """)
    List<AccountingRunEntity> accountingRuns(@Param("line") String line, @Param("environment") String environment,
            @Param("configId") Long configId, @Param("beforeId") long beforeId, @Param("limit") int limit);

    @Select("""
            SELECT id,run_id AS runId,rule_code AS ruleCode,rule_name AS ruleName,category,granularity,severity,
              status,anomaly_count AS anomalyCount,difference_amount AS differenceAmount,sample_json AS sampleJson,
              message,duration_ms AS durationMs,created_at AS createdAt
            FROM ops_account_reconcile_result
            WHERE run_id=#{runId} AND id>#{afterId}
            ORDER BY id ASC LIMIT #{limit}
            """)
    List<AccountingResultEntity> accountingResults(@Param("runId") long runId, @Param("afterId") long afterId, @Param("limit") int limit);

    @Select("SELECT COALESCE(MAX(id),0) FROM ops_system_alert_event WHERE business_line_code=#{line} AND environment_code=#{environment}")
    long alertUpper(@Param("line") String line, @Param("environment") String environment);
    @Select("""
            SELECT id,business_line_code,environment_code,subsystem_name,service_name,log_level AS level,event_category,
              title,message,error_type,stack_trace,trace_id,request_id,occurred_at,source_type
            FROM ops_system_alert_event
            WHERE business_line_code=#{line} AND environment_code=#{environment} AND id>#{after} AND id<=#{upper}
            ORDER BY id LIMIT #{limit}
            """)
    List<SystemAlertEventResponse> scanAlerts(@Param("line") String line, @Param("environment") String environment,
            @Param("after") long after, @Param("upper") long upper, @Param("limit") int limit);

    String ACCOUNT_SCOPE = " FROM ops_account_reconcile_run r LEFT JOIN ops_account_monitor_config c ON c.id=r.monitor_config_id "
            + "LEFT JOIN ops_account_execution_evidence e ON e.run_id=r.id "
            + "LEFT JOIN pm_business_line bl ON bl.business_line_code=COALESCE(e.business_line_code,c.business_line_code) "
            + "WHERE COALESCE(e.business_line_code,c.business_line_code)=#{line} AND COALESCE(e.environment_code,c.environment_code)=#{environment}";
    @Select("SELECT COALESCE(MAX(r.id),0)" + ACCOUNT_SCOPE)
    long accountingUpper(@Param("line") String line, @Param("environment") String environment);
    @Select("SELECT " + AccountingRunMapper.RUN_COLUMNS + ACCOUNT_SCOPE
            + " AND r.finished_at IS NOT NULL AND r.id>#{after} AND r.id<=#{upper} ORDER BY r.id LIMIT #{limit}")
    List<AccountingRunEntity> scanAccounting(@Param("line") String line, @Param("environment") String environment,
            @Param("after") long after, @Param("upper") long upper, @Param("limit") int limit);

    String BUG_SCOPE = " FROM pm_work_item w JOIN pm_project p ON p.id=w.project_id WHERE p.business_line_code=#{line} AND w.work_item_type='缺陷'";
    @Select("SELECT COALESCE(MAX(w.id),0)" + BUG_SCOPE)
    long bugUpper(String line);
    @Select("SELECT " + BUG_COLUMNS + " w.description,w.acceptance_criteria AS acceptanceCriteria,w.created_at AS createdAt,w.updated_at AS updatedAt"
            + BUG_SCOPE + " AND w.id>#{after} AND w.id<=#{upper} ORDER BY w.id LIMIT #{limit}")
    List<AgentBugRecord> scanBugs(@Param("line") String line, @Param("after") long after, @Param("upper") long upper, @Param("limit") int limit);
}
