package cn.aslight.workhub.job;

import cn.aslight.workhub.service.intake.IntakeAiDevelopmentService;
import cn.aslight.workhub.service.system.WorkhubJob;
import org.springframework.stereotype.Component;

/**
 * 需求 AI 分支开发巡检：轮询 Agent 任务运行状态，自测通过后把开发中需求自动流转为开发完成。
 */
@Component
public class IntakeAiDevelopmentWatchJob implements WorkhubJob {
    private final IntakeAiDevelopmentService aiDevelopmentService;

    public IntakeAiDevelopmentWatchJob(IntakeAiDevelopmentService aiDevelopmentService) {
        this.aiDevelopmentService = aiDevelopmentService;
    }

    @Override
    public String code() {
        return "INTAKE_AI_DEVELOPMENT_WATCH";
    }

    @Override
    public String name() {
        return "需求AI开发自测巡检";
    }

    @Override
    public ScheduleType scheduleType() {
        return ScheduleType.FIXED_DELAY;
    }

    @Override
    public String defaultScheduleValue() {
        return "60000";
    }

    @Override
    public String paramDescription() {
        return "无参数；手动触发即执行一次巡检（幂等）";
    }

    @Override
    public void run(String params) {
        aiDevelopmentService.watchProgress();
    }
}
