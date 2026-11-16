package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.dao.agent.AgentIssueMapper;
import cn.aslight.workhub.dao.agent.AgentMapper;
import cn.aslight.workhub.model.agent.AgentIssueEntity;
import cn.aslight.workhub.model.agent.AgentIssueResponse;
import cn.aslight.workhub.model.agent.AgentIssueReviewRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.time.ZoneOffset;
import java.util.Set;

/**
 * REQ-4d361388 工单审核与两段式发版：
 * 审核通过→合并 MR+触发 Jenkins TEST→TESTING；人工确认 TEST 通过→发 PROD→FIXED；拒绝→REJECTED 回开放。
 */
@Service
public class AgentIssueReviewService {
    private final AgentIssueMapper issues;
    private final AgentMapper mapper;
    private final AgentGitLabPolicy gitLabPolicies;
    private final AgentGitLabClient gitLab;
    private final AgentFixService.AgentNotifier notifier;
    private final ObjectMapper json;

    public AgentIssueReviewService(AgentIssueMapper issues, AgentMapper mapper,
                                   AgentGitLabPolicy gitLabPolicies, AgentGitLabClient gitLab,
                                   AgentFixService.AgentNotifier notifier, ObjectMapper json) {
        this.issues = issues;
        this.mapper = mapper;
        this.gitLabPolicies = gitLabPolicies;
        this.gitLab = gitLab;
        this.notifier = notifier;
        this.json = json;
    }

    @Transactional
    public AgentIssueResponse review(String issueId, AgentIssueReviewRequest request, String user) {
        var issue = issues.lock(issueId);
        if (issue == null) throw new IllegalArgumentException("工单不存在");
        if (!"PENDING_REVIEW".equals(issue.status()))
            throw new IllegalArgumentException("工单当前状态不能审核: " + issue.status());
        var fix = json.readValue(issue.fixJson(), AgentIssueResponse.Fix.class);
        if (fix == null || fix.mrUrl() == null || fix.mrUrl().isBlank())
            throw new IllegalArgumentException("工单缺少 MR，不能审核");
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        var review = new AgentIssueResponse.Review(user, request.decision(),
                request.note() == null ? "" : request.note(), now);
        String next = "APPROVE".equals(request.decision()) ? "TESTING" : "REJECTED";
        // APPROVE 时：合并 MR + 触发 Jenkins TEST（工单层面记录回执，部署执行复用 AgentJenkinsClient 链路由后续接线）
        String deployJson = issue.deployJson();
        if ("APPROVE".equals(request.decision())) {
            deployJson = json.writeValueAsString(new AgentIssueResponse.Deploy("PENDING", null, now, null, null, null));
        }
        int changed = issues.updateWorkflow(issue.id(), issue.version(), next,
                issue.fixJson(), json.writeValueAsString(review), deployJson, now);
        if (changed != 1) throw new IllegalStateException("工单状态已变化，请刷新");
        mapper.insertAudit(java.util.UUID.randomUUID().toString(), "ISSUE", issue.id(),
                "REVIEW_" + request.decision(), user, json.writeValueAsString(review), now);
        notifier.notifyIssues(List.of(issue), next, user);
        return toResponse(issues.find(issueId));
    }

    /** 人工确认 TEST 通过→发 PROD→FIXED；TEST 失败→回 REJECTED。 */
    @Transactional
    public AgentIssueResponse deployDecision(String issueId, String outcome, String user) {
        var issue = issues.lock(issueId);
        if (issue == null) throw new IllegalArgumentException("工单不存在");
        if (!"TESTING".equals(issue.status()))
            throw new IllegalArgumentException("工单当前状态不能确认部署: " + issue.status());
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        var deploy = json.readValue(issue.deployJson(), AgentIssueResponse.Deploy.class);
        String next;
        String updated;
        if ("PASS".equals(outcome)) {
            next = "FIXED";
            updated = json.writeValueAsString(new AgentIssueResponse.Deploy(
                    deploy == null ? "SUCCESS" : deploy.testStatus(), deploy == null ? null : deploy.testReceipt(),
                    deploy == null ? now : deploy.testedAt(), "SUCCESS", null, now));
        } else if ("FAIL".equals(outcome)) {
            next = "REJECTED";
            updated = json.writeValueAsString(new AgentIssueResponse.Deploy(
                    "FAILED", null, now, null, null, null));
        } else {
            throw new IllegalArgumentException("部署结论必须为 PASS 或 FAIL");
        }
        int changed = issues.updateWorkflow(issue.id(), issue.version(), next,
                issue.fixJson(), issue.reviewJson(), updated, now);
        if (changed != 1) throw new IllegalStateException("工单状态已变化，请刷新");
        mapper.insertAudit(java.util.UUID.randomUUID().toString(), "ISSUE", issue.id(),
                "DEPLOY_" + outcome, user, updated, now);
        notifier.notifyIssues(List.of(issue), next, user);
        return toResponse(issues.find(issueId));
    }

    private AgentIssueResponse toResponse(AgentIssueEntity entity) {
        return new AgentIssueResponse(entity.id(), entity.taskId(), entity.agentId(), entity.businessLineCode(),
                entity.environmentCode(), entity.domain(), entity.signature(), entity.title(), entity.severity(),
                entity.status(), json.readValue(entity.analysisJson(), AgentIssueResponse.Analysis.class),
                json.readValue(entity.evidenceJson(), AgentIssueResponse.Evidence.class),
                json.readValue(entity.fixJson(), AgentIssueResponse.Fix.class),
                json.readValue(entity.reviewJson(), AgentIssueResponse.Review.class),
                json.readValue(entity.deployJson(), AgentIssueResponse.Deploy.class),
                entity.firstRunId(), entity.lastRunId(), entity.firstSeenAt(), entity.lastSeenAt(),
                entity.occurrenceCount(), entity.unseenRounds(), entity.reopenCount(), entity.version(),
                entity.createdAt(), entity.updatedAt());
    }
}
