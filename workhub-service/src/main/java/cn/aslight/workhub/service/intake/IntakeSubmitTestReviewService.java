package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.dao.intake.DevelopmentAnalysisMapper;
import cn.aslight.workhub.dao.intake.IntakeMapper;
import cn.aslight.workhub.dao.intake.IntakeSubmitTestReviewMapper;
import cn.aslight.workhub.model.intake.DevelopmentAnalysisDraft;
import cn.aslight.workhub.model.intake.IntakeRecordEntity;
import cn.aslight.workhub.model.intake.IntakeStageActionRequest;
import cn.aslight.workhub.model.intake.IntakeStructuredData;
import cn.aslight.workhub.model.intake.IntakeSubmitTestConfirmRequest;
import cn.aslight.workhub.model.intake.IntakeSubmitTestReviewEntity;
import cn.aslight.workhub.model.intake.IntakeSubmitTestReviewResponse;
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
 * 确认提测后创建禅道测试单并触发所选测试环境的 Jenkins 构建，全部成功才推进测试中。
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
    private final DevelopmentAnalysisMapper developmentAnalysisMapper;
    private final AiGatewayClient aiGatewayClient;
    private final ZentaoClient zentaoClient;
    private final AgentJenkinsClient jenkinsClient;
    private final SysConfigService sysConfigService;
    private final ObjectMapper objectMapper;
    private final org.springframework.core.task.TaskExecutor taskExecutor;
    private final IntakeService intakeService;

    public IntakeSubmitTestReviewService(IntakeMapper intakeMapper,
                                         IntakeSubmitTestReviewMapper reviewMapper,
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
        requireInDevelopment(intake);
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
     * 确认提测：AI 审核与 Jenkins 构建均可选（skipAiReview/triggerJenkins，缺省为先审后提并触发构建）；
     * 禅道测试单始终创建且 owner 取必选的测试负责人（testOwnerUserName），
     * 所选外部动作全部成功才推进阶段到测试中，任一失败明确报错并停留开发中。
     */
    @Transactional
    public IntakeSubmitTestReviewResponse confirmAndSubmit(Long intakeId,
                                                           IntakeSubmitTestConfirmRequest request,
                                                           String operatorUserName) {
        IntakeRecordEntity intake = requireExisting(intakeId);
        requireDevelopmentRequirement(intake);
        requireInDevelopment(intake);
        boolean triggerJenkins = !Boolean.FALSE.equals(request.getTriggerJenkins());
        String environment = null;
        if (triggerJenkins) {
            environment = request.getEnvironmentCode() == null || request.getEnvironmentCode().isBlank()
                    ? null : request.getEnvironmentCode().trim().toUpperCase();
            if (environment == null) {
                throw new IllegalArgumentException("请选择测试环境后再确认提测");
            }
        }
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
        ZentaoClient.ZentaoTestTask testTask = zentaoClient.createTestTask(
                "测试单：" + firstNonBlank(intake.getRequirementName(), intake.getApprovalTitle(), "需求-" + intakeId),
                testOwnerUserName,
                intake.getBusinessLineCode(),
                intake.getDevelopmentBranchName(),
                intake.getZentaoUrl());
        entity.setZentaoTestTaskNo(testTask.taskNo());
        entity.setZentaoTestTaskUrl(testTask.url());

        if (triggerJenkins) {
            JenkinsSubmission submission = triggerJenkins(environment, trimToNull(request.getJenkinsJobName()), readDraft(intakeId));
            entity.setJenkinsEnvironmentCode(environment);
            entity.setJenkinsBuildNumber(submission.buildNumber());
            entity.setJenkinsBuildUrl(submission.buildUrl());
            entity.setJenkinsBuildStatus(submission.state());
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

    private JenkinsSubmission triggerJenkins(String environmentCode, String preferredJobName, DevelopmentAnalysisDraft draft) {
        String baseUrl = requireConfig(CONFIG_KEY_JENKINS_BASE_URL, "Jenkins 地址未配置（intake.submitTest.jenkinsBaseUrl）");
        String userName = requireConfig(CONFIG_KEY_JENKINS_USER, "Jenkins 用户未配置（intake.submitTest.jenkinsUserName）");
        String apiToken = requireConfig(CONFIG_KEY_JENKINS_TOKEN, "Jenkins API Token 未配置（intake.submitTest.jenkinsApiToken）");
        String jobName = resolveJobName(environmentCode, preferredJobName, draft);
        List<String> segments = new ArrayList<>();
        segments.add(jobName);
        AgentJenkinsClient.Target target = new AgentJenkinsClient.Target(
                URI.create(baseUrl.endsWith("/") ? baseUrl : baseUrl + "/"), segments, userName, apiToken);
        long queueId = jenkinsClient.submit(target, Map.of());
        AgentJenkinsClient.QueueState queue = jenkinsClient.queue(target, queueId, Map.of());
        if ("STARTED".equals(queue.state()) && queue.buildNumber() != null) {
            return new JenkinsSubmission("STARTED", jobName, queue.buildNumber(),
                    target.jobUri(queue.buildNumber() + "/").toString());
        }
        return new JenkinsSubmission(queue.state(), jobName, null,
                target.instanceUri("queue/item/" + queueId + "/").toString());
    }

    /**
     * Job 名解析：显式指定优先；否则按环境前缀（jenkinsJobPrefix.{ENV}）+ 首个任务系统拼接。
     */
    private String resolveJobName(String environmentCode, String preferredJobName, DevelopmentAnalysisDraft draft) {
        if (preferredJobName != null) {
            return preferredJobName;
        }
        String prefix = trimToNull(sysConfigService.findPlainValue(CONFIG_GROUP, CONFIG_KEY_JENKINS_JOB_PREFIX_PREFIX + environmentCode));
        String systemTag = draft == null || draft.workItems() == null || draft.workItems().isEmpty()
                ? null
                : draft.workItems().stream()
                        .map(item -> item.systemTags() == null ? null : item.systemTags().isEmpty() ? null : item.systemTags().getFirst())
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null);
        if (prefix != null && systemTag != null) {
            return prefix + "-" + systemTag;
        }
        throw new IllegalArgumentException("无法确定 Jenkins Job：请显式选择 Job，或配置 intake.submitTest.jenkinsJobPrefix."
                + environmentCode + " 并确保任务拆解包含涉及系统");
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

    private void requireInDevelopment(IntakeRecordEntity intake) {
        if (!"开发中".equals(trimToNull(intake.getDemandStatus()))) {
            throw new IllegalArgumentException("仅开发中需求允许提交测试");
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
