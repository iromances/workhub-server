package cn.aslight.workhub.service.system;

/**
 * REQ-9f3c2a84：WorkHub 系统定时任务统一契约。
 * 实现方注册为 Spring Bean 后由 {@link DynamicJobScheduler} 按 sys_job_config 调度；
 * 调度事实以数据库为准，代码默认值仅用于首次种子回写与恢复参照。
 */
public interface WorkhubJob {

    /** 全局唯一任务编码，同时是 sys_job_config.job_code。 */
    String code();

    /** 展示名称。 */
    String name();

    /** 调度类型：CRON 或 FIXED_DELAY。 */
    ScheduleType scheduleType();

    /** 默认调度值：CRON 为 cron 表达式，FIXED_DELAY 为毫秒数（字符串形式）。 */
    String defaultScheduleValue();

    /** 首次种子回写时是否启用；页面在 sys_job_config 上的启停始终优先。 */
    default boolean defaultEnabled() {
        return true;
    }

    /** 手动触发入参说明；无参数任务返回 null。 */
    default String paramDescription() {
        return null;
    }

    /** 执行一次任务；params 为手动触发入参（可空），由实现自行解析。 */
    void run(String params) throws Exception;

    enum ScheduleType {
        CRON,
        FIXED_DELAY
    }
}
