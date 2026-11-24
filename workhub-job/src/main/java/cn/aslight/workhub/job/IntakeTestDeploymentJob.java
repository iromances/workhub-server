package cn.aslight.workhub.job;

import cn.aslight.workhub.service.intake.IntakeTestDeploymentService;
import cn.aslight.workhub.service.system.WorkhubJob;
import org.springframework.stereotype.Component;

/** 按数据库调度配置处理已提交的测试发布并跟踪 Jenkins 回执。 */
@Component
public class IntakeTestDeploymentJob implements WorkhubJob {
    private final IntakeTestDeploymentService service;
    public IntakeTestDeploymentJob(IntakeTestDeploymentService service) { this.service = service; }
    public String code() { return "INTAKE_TEST_DEPLOYMENT"; }
    public String name() { return "需求测试发布巡检"; }
    public ScheduleType scheduleType() { return ScheduleType.FIXED_DELAY; }
    public String defaultScheduleValue() { return "10000"; }
    public void run(String params) { service.watch(); }
}
