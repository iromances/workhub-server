package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.dao.intake.DevelopmentAnalysisMapper;
import cn.aslight.workhub.dao.intake.IntakeAiDevelopmentMapper;
import cn.aslight.workhub.dao.intake.IntakeMapper;
import cn.aslight.workhub.model.agent.AgentScope;
import cn.aslight.workhub.model.agent.AgentScheduleRequest;
import cn.aslight.workhub.model.agent.AgentTaskCreateRequest;
import cn.aslight.workhub.model.agent.AgentTaskResponse;
import cn.aslight.workhub.model.agent.AgentTaskSpecification;
import cn.aslight.workhub.model.intake.DevelopmentAnalysisDraft;
import cn.aslight.workhub.model.intake.DevelopmentWorkItemDraft;
import cn.aslight.workhub.model.intake.IntakeAiDevelopmentEntity;
import cn.aslight.workhub.model.intake.IntakeAiDevelopmentResponse;
import cn.aslight.workhub.model.intake.IntakeRecordEntity;
import cn.aslight.workhub.model.intake.IntakeStructuredData;
import cn.aslight.workhub.service.agent.AgentWorkbenchService;
import cn.aslight.workhub.service.system.SysConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.ZoneId;
import java.util.List;

/**
 * 设计完成后的 AI 分支开发编排：按系统配置将需求开发任务交给平台 Agent 执行。
 * 设计完成时可经 aiDevelopmentEnabled 选择人工开发，之后在开发中经 {@link #retry} 手动首次启动。
 * 配置（系统配置组 intake.aiDevelopment）：agentId 必填；environmentCode 默认 TEST。
 */
@Service
public class IntakeAiDevelopmentService {

    public static final String CONFIG_GROUP = "intake.aiDevelopment";
    public static final String CONFIG_KEY_AGENT_ID = "agentId";
    public static final String CONFIG_KEY_ENVIRONMENT_CODE = "environmentCode";

    private static final Logger log = LoggerFactory.getLogger(IntakeAiDevelopmentService.class);
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_SUCCEEDED = "SUCCEEDED";
    private static final String STATUS_FAILED = "FAILED";
    /** Agent 任务已完成且自测通过（区别于 SUCCEEDED=Agent 已启动执行中）。 */
    private static final String STATUS_COMPLETED = "COMPLETED";

    private final IntakeMapper intakeMapper;
    private final IntakeAiDevelopmentMapper aiDevelopmentMapper;
    private final DevelopmentAnalysisMapper developmentAnalysisMapper;
    private final cn.aslight.workhub.dao.intake.IntakeHistoryMapper intakeHistoryMapper;
    private final AgentWorkbenchService agentWorkbenchService;
    private final SysConfigService sysConfigService;
    private final ObjectMapper objectMapper;
    private final TaskExecutor taskExecutor;
    private final TransactionTemplate transaction;

    public IntakeAiDevelopmentService(IntakeMapper intakeMapper,
                                      IntakeAiDevelopmentMapper aiDevelopmentMapper,
                                      DevelopmentAnalysisMapper developmentAnalysisMapper,
                                      cn.aslight.workhub.dao.intake.IntakeHistoryMapper intakeHistoryMapper,
                                      AgentWorkbenchService agentWorkbenchService,
                                      SysConfigService sysConfigService,
                                      ObjectMapper objectMapper,
                                      @Qualifier("workhubTaskExecutor") TaskExecutor taskExecutor,
                                      PlatformTransactionManager transactionManager) {
        this.intakeMapper = intakeMapper;
        this.aiDevelopmentMapper = aiDevelopmentMapper;
        this.developmentAnalysisMapper = developmentAnalysisMapper;
        this.intakeHistoryMapper = intakeHistoryMapper;
        this.agentWorkbenchService = agentWorkbenchService;
        this.sysConfigService = sysConfigService;
        this.objectMapper = objectMapper;
        this.taskExecutor = taskExecutor;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** 设计完成已提交，另开事务保存执行状态，提交后再交给后台线程。 */
    public void triggerAfterDesignConfirmed(Long intakeId, String operatorUserName) {
        transaction.executeWithoutResult(status -> {
            IntakeAiDevelopmentEntity entity = aiDevelopmentMapper.lockByIntakeId(intakeId);
            if (entity == null) {
                entity = new IntakeAiDevelopmentEntity();
                entity.setIntakeId(intakeId);
                entity.setRequestKey("intake-dev-" + intakeId);
                entity.setExecutionStatus(STATUS_PENDING);
                entity.setCreatedBy(operatorUserName);
                entity.setUpdatedBy(operatorUserName);
                aiDevelopmentMapper.insert(entity);
            } else {
                entity.setDevelopmentBranch(null);
                entity.setAgentId(null);
                entity.setAgentTaskId(null);
                resetPending(entity, operatorUserName);
            }
        });
        submit(intakeId, operatorUserName);
    }

    public IntakeAiDevelopmentResponse detail(Long intakeId) {
        IntakeAiDevelopmentEntity entity = aiDevelopmentMapper.findByIntakeId(intakeId);
        return entity == null ? null : toResponse(entity);
    }

    public void validateBeforeDesignConfirmed(Long intakeId, String user) {
        var intake = intakeMapper.findById(intakeId);
        if (intake == null) throw new IllegalArgumentException("需求不存在");
        String agentId = firstNonBlank(trimToNull(intake.getAiAgentId()),
                trimToNull(sysConfigService.findPlainValue(CONFIG_GROUP, CONFIG_KEY_AGENT_ID)));
        if (agentId == null) throw new IllegalArgumentException("还没有指定负责开发的数字员工，请先选择数字员工再确认设计完成");
        String environment = firstNonBlank(trimToNull(sysConfigService.findPlainValue(CONFIG_GROUP, CONFIG_KEY_ENVIRONMENT_CODE)), "TEST");
        agentWorkbenchService.requireAssignable(agentId, List.of(new AgentScope(requireBusinessLineCode(intake), environment)), user);
    }

    /**
     * 开发中直接重试或手动启动：失败或待执行且无关联任务时重试；
     * 无执行记录（设计完成时选择人工开发）时视为首次启动，创建记录并启动。
     */
    public IntakeAiDevelopmentResponse retry(Long intakeId, String operatorUserName) {
        boolean firstStart = transaction.execute(status -> {
            requireDevelopmentIntake(intakeId);
            IntakeAiDevelopmentEntity entity = aiDevelopmentMapper.lockByIntakeId(intakeId);
            if (entity == null) {
                return true;
            }
            if (STATUS_FAILED.equals(entity.getExecutionStatus())) {
                resetPending(entity, operatorUserName);
            } else if (!STATUS_PENDING.equals(entity.getExecutionStatus()) || entity.getAgentTaskId() != null) {
                throw new IllegalArgumentException("仅失败或尚未创建任务的待执行记录允许重试");
            }
            return false;
        });
        if (firstStart) {
            try {
                triggerAfterDesignConfirmed(intakeId, operatorUserName);
            } catch (DuplicateKeyException ex) {
                // 并发双击时另一请求已创建记录（intake_id 唯一键），直接返回当前状态
                log.info("AI development first start raced for intake {}, returning current state", intakeId);
            }
        } else {
            submit(intakeId, operatorUserName);
        }
        return detail(intakeId);
    }

    private void resetPending(IntakeAiDevelopmentEntity entity, String operatorUserName) {
        entity.setExecutionStatus(STATUS_PENDING);
        entity.setExecutionMessage(null);
        entity.setUpdatedBy(operatorUserName);
        aiDevelopmentMapper.updateByIntakeId(entity);
    }

    private void submit(Long intakeId, String operatorUserName) {
        try {
            taskExecutor.execute(() -> start(intakeId, operatorUserName));
        } catch (TaskRejectedException ex) {
            markFailed(intakeId, operatorUserName, "后台任务队列暂不可用，请重试", ex);
        }
    }

    private void start(Long intakeId, String operatorUserName) {
        try {
            transaction.executeWithoutResult(status -> startLocked(intakeId, operatorUserName));
        } catch (Exception ex) {
            String message = ex.getMessage() == null || ex.getMessage().isBlank()
                    ? ex.getClass().getSimpleName() : ex.getMessage();
            markFailed(intakeId, operatorUserName, message, ex);
        }
    }

    private void markFailed(Long intakeId, String operatorUserName, String message, Exception ex) {
        log.warn("AI development start failed for intake {}", intakeId, ex);
        String summary = message.length() > 500 ? message.substring(0, 500) : message;
        transaction.executeWithoutResult(status -> aiDevelopmentMapper.failPending(intakeId, summary, operatorUserName));
    }

    private IntakeRecordEntity requireDevelopmentIntake(Long intakeId) {
        IntakeRecordEntity intake = intakeMapper.findById(intakeId);
        if (intake == null) {
            throw new IllegalArgumentException("需求不存在：" + intakeId);
        }
        if (!IntakeDemandStatusRules.IN_DEVELOPMENT.equals(IntakeDemandStatusRules.resolve(intake, readStructuredDataOrNull(intake)))) {
            throw new IllegalArgumentException("仅开发中的需求可以启动 AI 开发");
        }
        return intake;
    }

    private void startLocked(Long intakeId, String operatorUserName) {
        IntakeAiDevelopmentEntity entity = aiDevelopmentMapper.lockByIntakeId(intakeId);
        if (entity == null || !STATUS_PENDING.equals(entity.getExecutionStatus())) {
            return;
        }
        IntakeRecordEntity intake = requireDevelopmentIntake(intakeId);
        String branch = trimToNull(intake.getDevelopmentBranchName());
        if (branch == null) {
            throw new IllegalArgumentException("需求未设置研发分支，无法启动 AI 开发");
        }
        DevelopmentAnalysisDraft draft = readConfirmedDraft(intakeId);
        if (draft == null || draft.workItems() == null || draft.workItems().isEmpty()) {
            throw new IllegalArgumentException("需求没有已确认的任务拆解，无法启动 AI 开发");
        }
        // 需求录入时选择的数字员工优先；未选择回退系统配置 intake.aiDevelopment.agentId
        String agentId = firstNonBlank(
                trimToNull(intake.getAiAgentId()),
                trimToNull(sysConfigService.findPlainValue(CONFIG_GROUP, CONFIG_KEY_AGENT_ID)));
        log.info("AI development start preconditions. intakeId={}, branch={}, agentId={}, fromIntake={}",
                intakeId, branch, agentId, trimToNull(intake.getAiAgentId()) != null);
        if (agentId == null) {
            throw new IllegalArgumentException("未选择 AI 开发数字员工，且系统未配置默认（intake.aiDevelopment.agentId）");
        }
        String environmentCode = firstNonBlank(
                trimToNull(sysConfigService.findPlainValue(CONFIG_GROUP, CONFIG_KEY_ENVIRONMENT_CODE)), "TEST");

        AgentTaskSpecification specification = new AgentTaskSpecification(
                "按已确认任务拆解在研发分支 " + branch + " 完成需求开发",
                buildContext(intake, draft, branch),
                "全部任务项完成开发并自测通过，代码交付到研发分支 " + branch,
                List.of("CHANGE_SET"),
                List.of(new AgentScope(requireBusinessLineCode(intake), environmentCode))
        );
        AgentTaskCreateRequest request = new AgentTaskCreateRequest(
                entity.getRequestKey(),
                specification,
                new AgentScheduleRequest(null, "MANUAL", null, ZoneId.systemDefault().getId(), "SKIP", false),
                true
        );
        AgentTaskResponse task = agentWorkbenchService.createTask(agentId, request, operatorUserName);

        entity.setDevelopmentBranch(branch);
        entity.setAgentId(agentId);
        entity.setAgentTaskId(task.id());
        entity.setExecutionStatus(STATUS_SUCCEEDED);
        entity.setExecutionMessage("已创建并启动 Agent 开发任务");
        entity.setUpdatedBy(operatorUserName);
        aiDevelopmentMapper.updateByIntakeId(entity);
        log.info("AI development agent task started. intakeId={}, agentTaskId={}, branch={}", intakeId, task.id(), branch);
    }

    private String buildContext(IntakeRecordEntity intake, DevelopmentAnalysisDraft draft, String branch) {
        StringBuilder builder = new StringBuilder();
        builder.append("需求 ID：").append(intake.getId()).append('\n')
                .append("需求名称：").append(firstNonBlank(intake.getRequirementName(), intake.getApprovalTitle())).append('\n')
                .append("研发分支：").append(branch).append('\n')
                .append("研发规范：").append("SDD".equalsIgnoreCase(trimToNull(intake.getDevelopmentStandard())) ? "SDD" : "常规").append('\n');
        if (draft.sddDocuments() != null && draft.sddDocuments().sddTasks() != null) {
            builder.append("\n已确认 SDD tasks：\n").append(draft.sddDocuments().sddTasks()).append('\n');
        }
        builder.append("\n已确认任务拆解：\n");
        for (DevelopmentWorkItemDraft item : draft.workItems()) {
            builder.append("- ").append(item.title());
            if (item.estimatedEffort() != null) {
                builder.append("（").append(item.estimatedEffort()).append("）");
            }
            builder.append('\n');
        }
        return builder.toString();
    }

    private DevelopmentAnalysisDraft readConfirmedDraft(Long intakeId) {
        var analysis = developmentAnalysisMapper.findLatestByIntakeId(intakeId);
        if (analysis == null || analysis.getDraftJson() == null || analysis.getDraftJson().isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(analysis.getDraftJson(), DevelopmentAnalysisDraft.class);
        } catch (Exception ex) {
            throw new IllegalStateException("任务拆解草稿解析失败", ex);
        }
    }

    private String requireBusinessLineCode(IntakeRecordEntity intake) {
        String code = trimToNull(intake.getBusinessLineCode());
        if (code == null) {
            throw new IllegalArgumentException("需求未关联业务线，无法确定 Agent 授权范围");
        }
        return code;
    }

    private IntakeAiDevelopmentResponse toResponse(IntakeAiDevelopmentEntity entity) {
        return new IntakeAiDevelopmentResponse(
                entity.getIntakeId(),
                entity.getDevelopmentBranch(),
                entity.getAgentId(),
                entity.getAgentTaskId(),
                entity.getExecutionStatus(),
                entity.getExecutionMessage(),
                entity.getUpdatedAt()
        );
    }

    /**
     * 定时巡检（WorkhubJob 调用）：按 Agent 任务最新运行状态回写——
     * 运行 COMPLETED 视为自测通过：executionStatus=COMPLETED，需求处于开发中时自动流转为开发完成；
     * 运行 FAILED/CANCELLED：executionStatus=FAILED，需求停留开发中等待人工重试。幂等，单条失败不中断整批。
     */
    public void watchProgress() {
        java.util.List<java.util.Map<String, Object>> targets;
        try {
            targets = aiDevelopmentMapper.findWatchTargets();
        } catch (Exception ex) {
            log.warn("AI development watch query failed", ex);
            return;
        }
        for (java.util.Map<String, Object> target : targets) {
            try {
                watchOne(target);
            } catch (Exception ex) {
                log.warn("AI development watch failed for intake {}", target.get("intakeId"), ex);
            }
        }
    }

    private void watchOne(java.util.Map<String, Object> target) {
        Long intakeId = ((Number) target.get("intakeId")).longValue();
        Object runStatusValue = target.get("latestRunStatus");
        String runStatus = runStatusValue == null ? null : String.valueOf(runStatusValue);
        if (runStatus == null) return;
        IntakeAiDevelopmentEntity entity = aiDevelopmentMapper.findByIntakeId(intakeId);
        if (entity == null || !java.util.Objects.equals(entity.getAgentTaskId(), target.get("agentTaskId"))) return;
        boolean recovered = STATUS_FAILED.equals(entity.getExecutionStatus())
                && !"FAILED".equals(runStatus) && !"CANCELLED".equals(runStatus);
        if (!STATUS_SUCCEEDED.equals(entity.getExecutionStatus()) && !recovered) {
            return; // 状态已被人工重试等改变，交由下一轮
        }
        entity.setUpdatedBy("system");
        if (!"COMPLETED".equals(runStatus) && !"FAILED".equals(runStatus) && !"CANCELLED".equals(runStatus)) {
            if (recovered) {
                entity.setExecutionStatus(STATUS_SUCCEEDED);
                entity.setExecutionMessage("Agent 任务已重试，等待执行完成");
                aiDevelopmentMapper.updateByIntakeId(entity);
            }
            return;
        }
        if ("COMPLETED".equals(runStatus)) {
            entity.setExecutionStatus(STATUS_COMPLETED);
            entity.setExecutionMessage("AI 自测完成，代码已交付研发分支 " + entity.getDevelopmentBranch());
            aiDevelopmentMapper.updateByIntakeId(entity);
            advanceOnSelfTestPassed(intakeId, entity.getDevelopmentBranch());
        } else {
            entity.setExecutionStatus(STATUS_FAILED);
            entity.setExecutionMessage("CANCELLED".equals(runStatus) ? "Agent 任务已取消" : "Agent 任务执行失败");
            aiDevelopmentMapper.updateByIntakeId(entity);
            insertHistory(intakeId, "UPDATE", "AI 自测失败",
                    "Agent 开发任务未通过自测（" + runStatus + "），需求停留「开发中」，可在 AI 分支开发区块重试。", "system");
        }
    }

    /** 自测通过后：需求当前为开发中时自动流转为开发完成并记历史；其他状态不动。 */
    private void advanceOnSelfTestPassed(Long intakeId, String branch) {
        IntakeRecordEntity intake = intakeMapper.findById(intakeId);
        if (intake == null) {
            return;
        }
        IntakeStructuredData structuredData = readStructuredDataOrNull(intake);
        String currentStatus = IntakeDemandStatusRules.resolve(intake, structuredData);
        if (!IntakeDemandStatusRules.IN_DEVELOPMENT.equals(currentStatus)) {
            return;
        }
        intakeMapper.updateManagementFields(
                intakeId,
                intake.getStructuredDataJson(),
                intake.getDevelopmentOwnerUserName(),
                IntakeDemandStatusRules.DEVELOPMENT_COMPLETED
        );
        insertHistory(intakeId, "UPDATE", "AI 自测完成",
                "AI 已在研发分支 " + branch + " 完成开发与自测，阶段自动流转：开发中 → 开发完成。", "system");
    }

    private IntakeStructuredData readStructuredDataOrNull(IntakeRecordEntity intake) {
        String json = intake.getStructuredDataJson();
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, IntakeStructuredData.class);
        } catch (Exception ex) {
            return null;
        }
    }

    private void insertHistory(Long intakeId, String actionType, String actionSummary, String detailText, String operatorUserName) {
        cn.aslight.workhub.model.intake.IntakeHistoryEntity history = new cn.aslight.workhub.model.intake.IntakeHistoryEntity();
        history.setIntakeId(intakeId);
        history.setActionType(actionType);
        history.setActionSummary(actionSummary);
        history.setDetailText(detailText);
        history.setOperatorUserName(trimToNull(operatorUserName) == null ? "system" : operatorUserName.trim());
        intakeHistoryMapper.insert(history);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String firstNonBlank(String first, String second) {
        return trimToNull(first) != null ? trimToNull(first) : trimToNull(second);
    }
}
