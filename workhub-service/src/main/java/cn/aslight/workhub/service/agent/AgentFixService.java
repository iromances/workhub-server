package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.dao.agent.AgentIssueMapper;
import cn.aslight.workhub.dao.agent.AgentMapper;
import cn.aslight.workhub.model.agent.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * REQ-4d361388 修复管线：勾选工单 → 独立 clone 涉及仓库建 fix 分支 → 组装修复任务走 CLI 运行 →
 * 提交推送 + 草稿 MR → 工单转 PENDING_REVIEW。审核通过 → 合并 + Jenkins TEST（AgentIssueReleaseService）。
 */
@Service
public class AgentFixService {
    /** 修复任务的目标/验收由本服务组装，职责复用工单五要素。 */
    private static final String FIX_INSTRUCTIONS = "你是企业的代码修复工程师。依据给定问题的代码位置、根因与修复方向，在仓库中完成代码修复。"
            + "只做修复所需的最小修改，不重构无关代码；不改公共接口签名，不改配置文件密钥；改完后自检语法与依赖一致性。"
            + "完成后输出结论：修改了哪些文件、每处修改与根因的对应关系、建议的验证步骤。";
    private static final String FIX_ACCEPTANCE = "在 fix 分支上完成选中问题的最小修复并提交；输出修改文件清单与验证建议；不得虚构未做的修改。";

    private final AgentIssueMapper issues;
    private final AgentMapper mapper;
    private final AgentWorkbenchService workbench;
    private final AgentGitLabPolicy gitLabPolicies;
    private final AgentGitLabClient gitLab;
    private final AgentNotifier notifier;
    private final ObjectMapper json;

    public AgentFixService(AgentIssueMapper issues, AgentMapper mapper, AgentWorkbenchService workbench,
                           AgentGitLabPolicy gitLabPolicies, AgentGitLabClient gitLab, AgentNotifier notifier, ObjectMapper json) {
        this.issues = issues;
        this.mapper = mapper;
        this.workbench = workbench;
        this.gitLabPolicies = gitLabPolicies;
        this.gitLab = gitLab;
        this.notifier = notifier;
        this.json = json;
    }

    /** 勾选工单触发修复：前置校验→独立 clone 建分支→组装修复任务并立即运行。 */
    @Transactional
    public List<AgentIssueResponse> fix(AgentIssueFixRequest request, String user) {
        var targets = new ArrayList<AgentIssueEntity>();
        for (String id : request.issueIds()) {
            var issue = issues.lock(id);
            if (issue == null) throw new IllegalArgumentException("工单不存在: " + id);
            if (!Set.of("OPEN", "ANALYZED", "REJECTED").contains(issue.status()))
                throw new IllegalArgumentException("工单当前状态不能发起修复: " + issue.status());
            var analysis = json.readValue(issue.analysisJson(), AgentIssueResponse.Analysis.class);
            if (analysis == null || analysis.repos() == null || analysis.repos().isEmpty())
                throw new IllegalArgumentException("工单缺少仓库定位，不能自动修复: " + issue.title());
            targets.add(issue);
        }
        // 汇总涉及的仓库并核验 GitLab 绑定可用
        var repositories = new ArrayList<String>();
        for (var issue : targets) {
            var analysis = json.readValue(issue.analysisJson(), AgentIssueResponse.Analysis.class);
            for (var repo : analysis.repos()) {
                if (!repositories.contains(repo.repository())) repositories.add(repo.repository());
            }
        }
        for (String repository : repositories) {
            var snapshot = gitLabPolicies.binding(repository);
            gitLabPolicies.requireCurrent(snapshot);
            gitLab.project(gitLabPolicies.target(snapshot));
        }
        String branch = "fix/" + targets.getFirst().id().substring(0, 8) + "-" + targets.size() + "-" + System.currentTimeMillis() % 100000;
        // 状态推进到 FIXING
        for (var issue : targets) {
            transition(issue, "FIXING", null);
        }
        // 组装修复任务（一次性运行，全部工单五要素作为补充资料）
        var agent = mapper.findAgent(targets.getFirst().agentId());
        if (agent == null) throw new IllegalArgumentException("数字员工不存在");
        var context = new StringBuilder();
        for (var issue : targets) {
            var analysis = json.readValue(issue.analysisJson(), AgentIssueResponse.Analysis.class);
            context.append("【").append(issue.severity()).append("】").append(issue.title()).append('\n');
            context.append("代码位置：").append(analysis.codeLocation()).append('\n');
            context.append("根因：").append(analysis.rootCause()).append('\n');
            context.append("影响与验证：").append(analysis.impactVerification()).append('\n');
            context.append("修复方向：").append(analysis.fixDirection()).append('\n');
            context.append("仓库：").append(analysis.repos()).append('\n');
            context.append("证据：").append(issue.evidenceJson()).append('\n');
        }
        var specification = new AgentTaskSpecification(
                "修复 " + targets.size() + " 个巡检问题：" + targets.getFirst().title(),
                context.toString(),
                FIX_ACCEPTANCE, List.of("CHANGE_SET"),
                List.of(new AgentScope(targets.getFirst().businessLineCode(), targets.getFirst().environmentCode())));
        var runRequest = new AgentTaskCreateRequest("fix-" + UUID.randomUUID(),
                specification, new AgentScheduleRequest(null, "MANUAL", null, "Asia/Shanghai", "RUN_ONCE", false), true);
        var task = workbench.createTask(agent.id(), runRequest, user);
        // fix_json 记录分支与修复运行关联
        var fix = new AgentIssueResponse.Fix(branch, List.of(), null, null, null, task.id());
        for (var issue : targets) {
            issues.updateWorkflow(issue.id(), issue.version() + 1, "FIXING",
                    json.writeValueAsString(fix), issue.reviewJson(), issue.deployJson(), LocalDateTime.now(ZoneOffset.UTC));
        }
        notifier.notifyIssues(targets, "FIXING", user);
        return targets.stream().map(this::toResponse).toList();
    }

    AgentIssueResponse toResponse(AgentIssueEntity entity) {
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

    private void transition(AgentIssueEntity issue, String status, String fixJson) {
        int changed = issues.updateWorkflow(issue.id(), issue.version(), status,
                fixJson != null ? fixJson : issue.fixJson(), issue.reviewJson(), issue.deployJson(),
                LocalDateTime.now(ZoneOffset.UTC));
        if (changed != 1) throw new IllegalStateException("工单状态已变化，请刷新: " + issue.id());
    }

    /** 工单状态站内信（复用 AgentNotifier 的通知通道）。 */
    @Service
    static class AgentNotifier {
        private final cn.aslight.workhub.dao.system.UserMapper users;
        private final cn.aslight.workhub.service.notification.NotificationService notifications;
        AgentNotifier(cn.aslight.workhub.dao.system.UserMapper users,
                      cn.aslight.workhub.service.notification.NotificationService notifications) {
            this.users = users;
            this.notifications = notifications;
        }
        void notifyIssues(List<AgentIssueEntity> issues, String event, String actor) {
            for (var issue : issues) {
                var members = users.findBusinessLineMembers(issue.businessLineCode());
                for (var member : members) {
                    notifications.createSystemNotification(member.userName(), "AGENT_ISSUE",
                            "[工单" + event + "] " + issue.title(), "工单 " + issue.id() + " 状态：" + event,
                            issue.businessLineCode(), issue.environmentCode(), "AGENT_ISSUE:" + issue.id() + ":" + event + ":" + System.currentTimeMillis());
                }
            }
        }
    }
}
