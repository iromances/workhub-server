package cn.aslight.workhub.job;

import cn.aslight.workhub.service.ops.FunctionCallSqlService;
import cn.aslight.workhub.service.system.WorkhubJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** REQ-b4d7e6f2：Function Call 执行审计清理（保留 365 天），调度值以 sys_job_config 为准。 */
@Component
public class FuncCallAuditCleanupJob implements WorkhubJob {

    private static final Logger log = LoggerFactory.getLogger(FuncCallAuditCleanupJob.class);

    private final FunctionCallSqlService functionCallSqlService;

    public FuncCallAuditCleanupJob(FunctionCallSqlService functionCallSqlService) {
        this.functionCallSqlService = functionCallSqlService;
    }

    @Override
    public String code() {
        return "OPS_FUNC_CALL_AUDIT_CLEANUP";
    }

    @Override
    public String name() {
        return "FunctionCall审计清理";
    }

    @Override
    public ScheduleType scheduleType() {
        return ScheduleType.CRON;
    }

    @Override
    public String defaultScheduleValue() {
        return "0 0 4 * * *";
    }

    @Override
    public String paramDescription() {
        return "无参数；删除超过 365 天的 func_call_sql_audit_log 记录";
    }

    @Override
    public void run(String params) {
        int removed = functionCallSqlService.cleanupExpiredAudits();
        if (removed > 0) {
            log.info("Cleaned {} function call audit logs", removed);
        }
    }
}
