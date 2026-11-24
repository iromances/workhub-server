package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.dao.intake.DevelopmentAnalysisMapper;
import cn.aslight.workhub.dao.intake.IntakeMapper;
import cn.aslight.workhub.dao.intake.IntakeSubmitTestReviewMapper;
import cn.aslight.workhub.dao.project.BusinessLineMapper;
import cn.aslight.workhub.model.intake.DevelopmentAnalysisDraft;
import cn.aslight.workhub.model.intake.IntakeRecordEntity;
import cn.aslight.workhub.model.intake.IntakeStageActionRequest;
import cn.aslight.workhub.model.intake.IntakeStructuredData;
import cn.aslight.workhub.model.intake.IntakeSubmitTestConfirmRequest;
import cn.aslight.workhub.model.intake.IntakeSubmitTestReviewEntity;
import cn.aslight.workhub.model.intake.IntakeSubmitTestReviewResponse;
import cn.aslight.workhub.model.project.BusinessLineEntity;
import cn.aslight.workhub.service.agent.AgentJenkinsClient;
import cn.aslight.workhub.service.ai.AiGatewayClient;
import cn.aslight.workhub.service.ai.AiGatewayRequest;
import cn.aslight.workhub.service.ai.AiGatewayResult;
import cn.aslight.workhub.service.system.SysConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 提测 AI 审核与提测执行：审核不通过停留开发中，人工确认可强制提测；
 * 确认提测后按需创建禅道测试单并触发所选测试环境的 Jenkins 构建，全部成功才推进测试中。
 */
@Service
public class IntakeSubmitTestReviewService {

    public static final String CONFIG_GROUP = "intake.submitTest";
    public static final String CONFIG_KEY_JENKINS_BASE_URL = "jenkinsBaseUrl";
    public static final String CONFIG_KEY_JENKINS_USER = "jenkinsUserName";
    public static final String CONFIG_KEY_JENKINS_TOKEN = "jenkinsApiToken";
    public static final String CONFIG_KEY_JENKINS_JOB_PATH_PREFIX = "jenkinsJobPath.";
    public static final String CONFIG_KEY_JENKINS_JOB_PREFIX_PREFIX = "jenkinsJobPrefix.";

    private static final Logger log = LoggerFactory.getLogger(IntakeSubmitTestReviewService.class);
    private static final String USE_CASE_CODE = "intake.submit-test.review";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_PASSED = "PASSED";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_SKIPPED = "SKIPPED";
    private static final String OUTPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "conclusion": { "type": "string", "enum": ["PASS", "PASS_WITH_NOTES", "FAIL"] },
                "summary": { "type": ["string", "null"] },
                "findings": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "title": { "type": "string" },
                      "severity": { "type": "string", "enum": ["HIGH", "MEDIUM", "LOW"] },
                      "description": { "type": ["string", "null"] },
                      "evidence": { "type": ["string", "null"] }
                    },
                    "required": ["title", "severity"],
                    "additionalProperties": false
                  }
                }
              },
              "required": ["conclusion", "findings"],
              "additionalProperties": false
            }
            """;

    private final IntakeMapper intakeMapper;
    private final IntakeSubmitTestReviewMapper reviewMapper;
    private final BusinessLineMapper businessLineMapper;
    private final DevelopmentAnalysisMapper developmentAnalysisMapper;
    private final AiGatewayClient aiGatewayClient;
    private final ZentaoClient zentaoClient;
    private final AgentJenkinsClient jenkinsClient;
    private final SysConfigService sysConfigService;
    private final ObjectMapper objectMapper;
    private final org.springframework.core.task.TaskExecutor taskExecutor;
    private final IntakeService intakeService;
    private IntakeTestDeploymentService testDeployments;

    @org.springframework.beans.factory.annotation.Autowired
    public void setTestDeployments(IntakeTestDeploymentService service) { this.testDeployments = service; }

    public IntakeSubmitTestReviewService(IntakeMapper intakeMapper,
                                         IntakeSubmitTestReviewMapper reviewMapper,
                                         BusinessLineMapper businessLineMapper,
                                         DevelopmentAnalysisMapper developmentAnalysisMapper,
                                         AiGatewayClient aiGatewayClient,
                                         ZentaoClient zentaoClient,
                                         AgentJenkinsClient jenkinsClient,
                                         SysConfigService sysConfigService,
                                         ObjectMapper objectMapper,
                                         @Qualifier("workhubTaskExecutor") org.springframework.core.task.TaskExecutor taskExecutor,
                                         IntakeService intakeService) {
        this.intakeMapper = intakeMapper;
        this.reviewMapper = reviewMapper;
        this.businessLineMapper = businessLineMapper;
        this.developmentAnalysisMapper = developmentAnalysisMapper;
        this.aiGatewayClient = aiGatewayClient;
        this.zentaoClient = zentaoClient;
        this.jenkinsClient = jenkinsClient;
        this.sysConfigService = sysConfigService;
        this.objectMapper = objectMapper;
        this.taskExecutor = taskExecutor;
        this.intakeService = intakeService;
    }

    public IntakeSubmitTestReviewResponse detail(Long intakeId) {
        IntakeSubmitTestReviewEntity entity = reviewMapper.findByIntakeId(intakeId);
        return entity == null ? null : toResponse(entity);
    }

    /**
     * 触发提测 AI 审核（异步）；已有未决审核时直接返回当前状态。
     */
    public IntakeSubmitTestReviewResponse startReview(Long intakeId, String operatorUserName) {
        IntakeRecordEntity intake = requireExisting(intakeId);
        requireDevelopmentRequirement(intake);
        requireCanSubmitTesting(intake);
        IntakeSubmitTestReviewEntity existing = reviewMapper.findByIntakeId(intakeId);
        if (existing != null && (STATUS_PENDING.equals(existing.getReviewStatus()) || STATUS_RUNNING.equals(existing.getReviewStatus()))) {
            return toResponse(existing);
        }
        IntakeSubmitTestReviewEntity entity = existing == null ? new IntakeSubmitTestReviewEntity() : existing;
        entity.setIntakeId(intakeId);
        entity.setReviewStatus(STATUS_PENDING);
        entity.setReviewMessage(null);
        entity.setFindingsJson(null);
        entity.setConclusion(null);
        entity.setSummary(null);
        entity.setHumanConfirmedBy(null);
        entity.setHumanConfirmedAt(null);
        entity.setCreatedBy(existing == null ? operatorUserName : entity.getCreatedBy());
        entity.setUpdatedBy(operatorUserName);
        if (existing == null) {
            reviewMapper.insert(entity);
        } else {
            reviewMapper.updateByIntakeId(entity);
        }
        taskExecutor.execute(() -> executeReview(intakeId, operatorUserName));
        return detail(intakeId);
    }

    /**
     * 确认提测：AI 审核、禅道测试单与 Jenkins 构建均可选（skipAiReview/createZentaoTestTask/triggerJenkins，
     * 缺省为先审后提、创建测试单并触发构建）；
     * 测试环境（environmentCode）与测试负责人（testOwnerUserName）始终必填，
     * 环境须为业务线支持的非生产环境，触发构建时还须与所选测试发布绑定环境一致；
     * 所选外部动作全部成功才推进阶段到测试中，任一失败明确报错并保留原开发阶段。
     */
    @Transactional
    public IntakeSubmitTestReviewResponse confirmAndSubmit(Long intakeId,
                                                           IntakeSubmitTestConfirmRequest request,
                                                           String operatorUserName) {
        IntakeRecordEntity intake = requireExisting(intakeId);
        requireDevelopmentRequirement(intake);
        requireCanSubmitTesting(intake);
        boolean triggerJenkins = !Boolean.FALSE.equals(request.getTriggerJenkins());
        String environment = request.getEnvironmentCode() == null || request.getEnvironmentCode().isBlank()
                ? null : request.getEnvironmentCode().trim().toUpperCase();
        if (environment == null) {
            throw new IllegalArgumentException("请选择测试环境后再确认提测");
        }
        requireSupportedEnvironment(intake, environment);
        boolean skipAiReview = Boolean.TRUE.equals(request.getSkipAiReview());
        IntakeSubmitTestReviewEntity entity = reviewMapper.findByIntakeId(intakeId);
        if (skipAiReview) {
            if (entity == null) {
                entity = new IntakeSubmitTestReviewEntity();
                entity.setIntakeId(intakeId);
                entity.setReviewStatus(STATUS_SKIPPED);
                entity.setReviewMessage("人工跳过 AI 提测审核");
                entity.setCreatedBy(operatorUserName);
                entity.setUpdatedBy(operatorUserName);
                reviewMapper.insert(entity);
            }
        } else {
            if (entity == null || STATUS_PENDING.equals(entity.getReviewStatus()) || STATUS_RUNNING.equals(entity.getReviewStatus())) {
                throw new IllegalArgumentException("提测 AI 审核尚未完成，请先等待审核结果");
            }
            if (STATUS_FAILED.equals(entity.getReviewStatus())) {
                if (!request.isForceConfirm()) {
                    throw new IllegalArgumentException("AI 审核不通过：" + firstNonBlank(entity.getSummary(), "存在待处理缺陷")
                            + "。请处理缺陷后重新审核，或人工确认强制提测，或勾选跳过 AI 审核直接提测。");
                }
                entity.setHumanConfirmedBy(operatorUserName);
                entity.setHumanConfirmedAt(java.time.LocalDateTime.now());
            }
        }

        // 禅道测试单 owner = 提测时选择的测试负责人（2026-11-10 起不再默认研发负责人）
        String testOwnerUserName = trimToNull(request.getTestOwnerUserName());
        if (testOwnerUserName == null) {
            throw new IllegalArgumentException("请选择测试负责人后再确认提测");
        }
        if (triggerJenkins) {
            var binding = testDeployments.validateRequest(intakeId, request.getTestDeployment());
            String bindingEnvironment = binding.environmentCode() == null ? null : binding.environmentCode().trim().toUpperCase();
            if (!environment.equals(bindingEnvironment))
                throw new IllegalArgumentException("测试环境与所选测试发布目标不符");
            if (trimToNull(request.getJenkinsJobName()) != null && !binding.jobName().equals(request.getJenkinsJobName()))
                throw new IllegalArgumentException("Jenkins Job 与测试绑定不符");
        }
        boolean createZentaoTestTask = !Boolean.FALSE.equals(request.getCreateZentaoTestTask());
        if (createZentaoTestTask) {
            ZentaoClient.ZentaoTestTask testTask = zentaoClient.createTestTask(
                    "测试单：" + firstNonBlank(intake.getRequirementName(), intake.getApprovalTitle(), "需求-" + intakeId),
                    testOwnerUserName,
                    intake.getBusinessLineCode(),
                    intake.getDevelopmentBranchName(),
                    intake.getZentaoUrl());
            entity.setZentaoTestTaskNo(testTask.taskNo());
            entity.setZentaoTestTaskUrl(testTask.url());
        } else {
            // 回滚后重提可能残留上次回执，不提单时显式清空
            entity.setZentaoTestTaskNo(null);
            entity.setZentaoTestTaskUrl(null);
        }

        // 提测确认环境无论是否触发构建均记录回执；构建号与构建地址仅触发构建时重置
        entity.setJenkinsEnvironmentCode(environment);
        if (triggerJenkins) {
            testDeployments.submit(intakeId, request.getTestDeployment(), operatorUserName);
            entity.setJenkinsBuildNumber(null);
            entity.setJenkinsBuildUrl(null);
            entity.setJenkinsBuildStatus("PENDING");
        }
        entity.setUpdatedBy(operatorUserName);
        reviewMapper.updateByIntakeId(entity);

        IntakeStageActionRequest stageAction = new IntakeStageActionRequest();
        stageAction.setAction("SUBMIT_TESTING");
        stageAction.setActualEffort(trimToNull(request.getActualEffort()));
        stageAction.setActualCompletedTime(trimToNull(request.getActualCompletedTime()));
        stageAction.setOccurredAt(trimToNull(request.getOccurredAt()));
        intakeService.advanceStage(intakeId, stageAction, operatorUserName);
        return detail(intakeId);
    }

    void executeReview(Long intakeId, String operatorUserName) {
        IntakeSubmitTestReviewEntity entity = reviewMapper.findByIntakeId(intakeId);
        if (entity == null || !STATUS_PENDING.equals(entity.getReviewStatus())) {
            return;
        }
        entity.setReviewStatus(STATUS_RUNNING);
        entity.setUpdatedBy(operatorUserName);
        // 只回写审核结果列：跳过审核确认提测可能与审核并发，避免旧实体覆盖禅道/Jenkins 回执
        reviewMapper.updateReviewOutcomeByIntakeId(entity);
        try {
            IntakeRecordEntity intake = requireExisting(intakeId);
            DevelopmentAnalysisDraft draft = readDraft(intakeId);
            AiGatewayResult result = aiGatewayClient.executeStructured(AiGatewayRequest.withVariables(
                    USE_CASE_CODE,
                    buildVariables(intake, draft),
                    null,
                    List.of(),
                    List.of(),
                    OUTPUT_SCHEMA
            ));
            if (!result.succeeded()) {
                throw new IllegalArgumentException(result.failureSummary());
            }
            ReviewResult review = objectMapper.readValue(result.output(), ReviewResult.class);
            boolean failed = "FAIL".equals(review.conclusion());
            entity.setReviewStatus(failed ? STATUS_FAILED : STATUS_PASSED);
            entity.setConclusion(review.conclusion());
            entity.setSummary(trimToNull(review.summary()));
            entity.setFindingsJson(writeFindings(review.findings()));
            entity.setReviewMessage(failed ? "AI 审核不通过，请处理缺陷或人工确认强制提测" : "AI 审核通过，可确认提测");
        } catch (Exception ex) {
            String message = ex.getMessage() == null || ex.getMessage().isBlank()
                    ? ex.getClass().getSimpleName() : ex.getMessage();
            log.warn("Submit test review failed for intake {}", intakeId, ex);
            entity.setReviewStatus(STATUS_FAILED);
            entity.setReviewMessage("提测 AI 审核执行失败：" + (message.length() > 200 ? message.substring(0, 200) : message));
        }
        entity.setUpdatedBy(operatorUserName);
        reviewMapper.updateReviewOutcomeByIntakeId(entity);
    }

    private Map<String, Object> buildVariables(IntakeRecordEntity intake, DevelopmentAnalysisDraft draft) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("developmentBranch", firstNonBlank(intake.getDevelopmentBranchName(), "未设置研发分支"));
        variables.put("diffSummary", "（平台暂未接入研发分支 diff 自动摘要，请基于任务拆解与验证证据审核）");
        variables.put("verificationEvidence", buildVerificationEvidence(intake, draft));
        variables.put("intakeInfo", "- 需求 ID：" + intake.getId()
                + "\n- 需求名称：" + firstNonBlank(intake.getRequirementName(), intake.getApprovalTitle(), "未命名")
                + "\n- 审批编号：" + firstNonBlank(intake.getApprovalCode(), "无")
                + "\n- 研发分支：" + firstNonBlank(intake.getDevelopmentBranchName(), "未设置"));
        variables.put("workItems", draft == null || draft.workItems() == null || draft.workItems().isEmpty()
                ? "（无已确认任务拆解）"
                : draft.workItems().stream()
                        .map(item -> "- " + item.title()
                                + (item.estimatedEffort() == null ? "" : "（" + item.estimatedEffort() + "）"))
                        .reduce(new StringBuilder(), StringBuilder::append, StringBuilder::append)
                        .toString());
        return variables;
    }

    private String buildVerificationEvidence(IntakeRecordEntity intake, DevelopmentAnalysisDraft draft) {
        StringBuilder builder = new StringBuilder();
        builder.append("- 研发负责人：").append(firstNonBlank(intake.getDevelopmentOwnerUserName(), "未指派")).append('\n')
                .append("- 任务拆解：").append(draft == null || draft.workItems() == null ? 0 : draft.workItems().size()).append(" 项").append('\n');
        if (draft != null && draft.sddDocuments() != null && draft.sddDocuments().hasAny()) {
            builder.append("- SDD 三件套已随评估确认发布").append('\n');
        }
        builder.append("（分支 diff 与构建报告的自动取证后续接入，当前以任务拆解一致性为审核依据）");
        return builder.toString();
    }

    private DevelopmentAnalysisDraft readDraft(Long intakeId) {
        var analysis = developmentAnalysisMapper.findLatestByIntakeId(intakeId);
        if (analysis == null || analysis.getDraftJson() == null || analysis.getDraftJson().isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(analysis.getDraftJson(), DevelopmentAnalysisDraft.class);
        } catch (Exception ex) {
            return null;
        }
    }

    private String writeFindings(List<ReviewFinding> findings) {
        try {
            return findings == null || findings.isEmpty() ? "[]" : objectMapper.writeValueAsString(findings);
        } catch (Exception ex) {
            return "[]";
        }
    }

    private IntakeSubmitTestReviewResponse toResponse(IntakeSubmitTestReviewEntity entity) {
        List<IntakeSubmitTestReviewResponse.SubmitTestFinding> findings = new ArrayList<>();
        if (entity.getFindingsJson() != null && !entity.getFindingsJson().isBlank()) {
            try {
                for (ReviewFinding finding : objectMapper.readValue(entity.getFindingsJson(), ReviewFinding[].class)) {
                    findings.add(new IntakeSubmitTestReviewResponse.SubmitTestFinding(
                            finding.title(), finding.severity(), finding.description(), finding.evidence()));
                }
            } catch (Exception ignored) {
                // 缺陷清单解析失败时保留空清单，原始 JSON 仍在库中
            }
        }
        return new IntakeSubmitTestReviewResponse(
                entity.getIntakeId(),
                entity.getReviewStatus(),
                entity.getReviewMessage(),
                entity.getConclusion(),
                entity.getSummary(),
                findings,
                entity.getHumanConfirmedBy(),
                entity.getHumanConfirmedAt(),
                entity.getZentaoTestTaskNo(),
                entity.getZentaoTestTaskUrl(),
                entity.getJenkinsEnvironmentCode(),
                entity.getJenkinsBuildNumber(),
                entity.getJenkinsBuildUrl(),
                entity.getJenkinsBuildStatus(),
                entity.getUpdatedAt()
        );
    }

    private IntakeRecordEntity requireExisting(Long intakeId) {
        IntakeRecordEntity intake = intakeMapper.findById(intakeId);
        if (intake == null) {
            throw new IllegalArgumentException("需求不存在：" + intakeId);
        }
        return intake;
    }

    private void requireDevelopmentRequirement(IntakeRecordEntity intake) {
        if (!"研发需求".equals(trimToNull(intake.getRequirementType()))) {
            throw new IllegalArgumentException("仅研发需求支持提测 AI 审核");
        }
    }

    private void requireCanSubmitTesting(IntakeRecordEntity intake) {
        IntakeDemandStatusRules.resolveNextStatus(intake.getDemandStatus(), intake.getRequirementType(),
                IntakeDemandStatusRules.ACTION_SUBMIT_TESTING);
    }

    /** 提测环境须为业务线已支持的非生产环境；业务线未配置支持环境时回退仅允许 TEST。 */
    private void requireSupportedEnvironment(IntakeRecordEntity intake, String environment) {
        if ("PROD".equals(environment)) {
            throw new IllegalArgumentException("提测不允许选择生产环境");
        }
        BusinessLineEntity line = businessLineMapper.findByCode(intake.getBusinessLineCode());
        java.util.Set<String> supported = new java.util.LinkedHashSet<>();
        if (line != null && trimToNull(line.getSupportedEnvironments()) != null) {
            for (String value : line.getSupportedEnvironments().split(",")) {
                String env = trimToNull(value);
                if (env != null) {
                    supported.add(env.toUpperCase());
                }
            }
        }
        supported.remove("PROD");
        if (supported.isEmpty()) {
            supported.add("TEST");
        }
        if (!supported.contains(environment)) {
            throw new IllegalArgumentException("测试环境须为业务线支持的环境：" + String.join("、", supported));
        }
    }

    private String requireConfig(String key, String message) {
        String value = sysConfigService.findPlainValue(CONFIG_GROUP, key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (trimToNull(value) != null) {
                return trimToNull(value);
            }
        }
        return null;
    }

    private record ReviewResult(String conclusion, String summary, List<ReviewFinding> findings) {
    }

    private record ReviewFinding(String title, String severity, String description, String evidence) {
    }

    private record JenkinsSubmission(String state, String jobName, Long buildNumber, String buildUrl) {
    }
}
