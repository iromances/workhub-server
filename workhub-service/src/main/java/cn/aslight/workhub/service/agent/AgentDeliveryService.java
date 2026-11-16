package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.dao.agent.AgentDeliveryMapper;
import cn.aslight.workhub.model.agent.*;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;

/** 每次推进一个仓库；未知写入只核对已有分支/MR，不能当作未发送后重建。 */
@Service
public class AgentDeliveryService {
    private final AgentDeliveryMapper mapper;
    private final AgentDeliveryStore store;
    private final AgentChangeService changes;
    private final AgentCodeWorkspaceService workspaces;
    private final AgentCodeGit git;
    private final AgentGitLabPolicy policies;
    private final AgentGitLabClient client;
    private final AgentAccessService access;
    private final ObjectMapper json;
    public AgentDeliveryService(AgentDeliveryMapper mapper, AgentDeliveryStore store, AgentChangeService changes, AgentCodeWorkspaceService workspaces,
                                 AgentCodeGit git, AgentGitLabPolicy policies, AgentGitLabClient client, AgentAccessService access, ObjectMapper json) {
        this.mapper = mapper; this.store = store; this.changes = changes; this.workspaces = workspaces; this.git = git;
        this.policies = policies; this.client = client; this.access = access; this.json = json;
    }

    public Map<String, Object> publish(AgentToolContext context, String changeId, String repositoryId) {
        AgentDeliveryEntity record = store.reserve(context, changeId, repositoryId);
        if (Set.of("CONFLICT", "REJECTED").contains(record.status())) return view(record);
        var change = changes.detail(context, changeId);
        var repository = change.manifest().repositories().stream().filter(repo -> repo.repositoryId().equals(repositoryId)).findFirst().orElseThrow();
        var binding = json.readValue(record.bindingJson(), AgentGitLabPolicy.Snapshot.class);
        access.requireScope(context, repository.scope());
        var target = policies.target(binding);
        try {
            var project = client.project(target);
            var baseline = client.branch(target, record.targetBranch());
            boolean baselineMatches = baseline != null && baseline.sha().equals(repository.baseSha());
            if (!baselineMatches && "NEW".equals(record.status())) throw new AgentGitLabClient.Conflict("GitLab 目标分支已偏离清单基线，请重新规划");
            if (repository.changedPaths().isEmpty()) {
                if (!baselineMatches) throw new AgentGitLabClient.Conflict("GitLab 基线提交已变化");
                return view(store.receipt(context, record, "UNCHANGED_CONFIRMED", null, null, null, now()));
            }
            if (record.sourceBranch().equals(project.defaultBranch())) throw new AgentGitLabClient.Conflict("候选分支是默认分支，拒绝写入");
            var source = client.branch(target, record.sourceBranch());
            if (source == null) {
                if (!"NEW".equals(record.status())) {
                    String phase = Set.of("PUSHING", "PUSH_UNKNOWN").contains(record.status()) ? "PUSH_UNKNOWN" : "CONFLICT";
                    return view(store.receipt(context, record, phase, record.mergeRequestIid(), record.mergeRequestUrl(),
                            "远端候选分支尚未确认存在；保留原写入意图，未自动重发", null));
                }
                if (!change.current()) throw new IllegalArgumentException("旧版本仅能核对已发请求，不能创建分支");
                client.requireUnprotected(target, record.sourceBranch());
                var directory = workspaces.deliveryDirectory(context, repository);
                var pending = record;
                record = git.locked(directory, () -> {
                    // 获取本地锁失败时尚未发送，不能误记为未知远端写入。
                    var sending = store.intent(context, pending, "PUSHING");
                    try {
                        git.publish(directory, target, sending.sourceBranch(), sending.resultSha());
                        return sending;
                    } catch (AgentCodeGit.UnknownGitProcess | AgentRunStore.StopRequested ex) {
                        // 仍可能存在本地进程时不能清除外层 TOOL 句柄或宣称已停止。
                        throw ex;
                    } catch (RuntimeException ex) {
                        return store.receipt(context, sending, "PUSH_UNKNOWN", null, null, "Git 推送结果未确认，请核对原候选分支；未重新发送", null);
                    }
                });
                if ("PUSH_UNKNOWN".equals(record.status())) return view(record);
                source = client.branch(target, record.sourceBranch());
                if (source == null) return view(store.receipt(context, record, "PUSH_UNKNOWN", null, null, "推送返回后尚未读取到远端分支，等待核验", null));
            }
            if (source.protectedBranch() || source.defaultBranch() || !record.resultSha().equals(source.sha())) {
                throw new AgentGitLabClient.Conflict("远端候选分支受保护或提交不一致，未覆盖已有内容");
            }
            if (Set.of("NEW", "PUSHING", "PUSH_UNKNOWN").contains(record.status())) record = store.receipt(context, record, "BRANCH_READY", null, null, null, null);
            AgentGitLabClient.MergeRequest mergeRequest;
            if (record.mergeRequestIid() != null) {
                mergeRequest = client.mergeRequest(target, record.mergeRequestIid(), record.sourceBranch(), record.targetBranch());
                if (mergeRequest == null) throw new AgentGitLabClient.Conflict("原 MR 已不可读取，未重新创建");
            } else {
                var matches = client.find(target, record.sourceBranch(), record.targetBranch());
                if (matches.size() > 1) throw new AgentGitLabClient.Conflict("同一候选分支存在多个 MR，不能自动选择");
                mergeRequest = matches.isEmpty() ? null : matches.getFirst();
                if (mergeRequest == null) {
                    if (!"BRANCH_READY".equals(record.status())) return view(store.receipt(context, record, "MR_UNKNOWN", null, null, "原 MR 创建请求尚未查询到结果，未重新发送", null));
                    var currentBase = client.branch(target, record.targetBranch());
                    if (!change.current() || currentBase == null || !repository.baseSha().equals(currentBase.sha())) {
                        return view(store.receipt(context, record, "BRANCH_READY", null, null, "分支已核验，但清单或目标基线已变化，未创建新 MR", null));
                    }
                    record = store.intent(context, record, "MR_CREATING");
                    String summary = change.manifest().plan().summary();
                    String title = "Draft: " + summary.substring(0, Math.min(200, summary.length()));
                    client.create(target, record.sourceBranch(), record.targetBranch(), title, description(record, change));
                    // 使用独立 GET 核对；创建响应本身不替代远端结果。
                    matches = client.find(target, record.sourceBranch(), record.targetBranch());
                    if (matches.size() != 1) return view(store.receipt(context, record, "MR_UNKNOWN", null, null, "MR 创建后尚未取得唯一可核验结果", null));
                    mergeRequest = matches.getFirst();
                }
            }
            String expectedMarker = marker(record);
            String legacyMarker = legacyMarker(record);
            if (!mergeRequest.description().lines().anyMatch(line -> line.equals(expectedMarker) || line.equals(legacyMarker))
                    || !"opened".equals(mergeRequest.state())) {
                throw new AgentGitLabClient.Conflict("MR 关联标记或状态与候选交付不符，未修改原 MR");
            }
            if (mergeRequest.sha() == null) return view(store.receipt(context, record, "MR_UNKNOWN", mergeRequest.iid(), mergeRequest.webUrl(), "MR 的实际提交尚未生成，等待再次核对", null));
            if (!record.resultSha().equals(mergeRequest.sha())) throw new AgentGitLabClient.Conflict("MR 实际提交与清单不一致");
            // 再次读取引用，避免 MR 查询期间候选分支已被人工移动。
            source = client.branch(target, record.sourceBranch());
            if (source == null || !record.resultSha().equals(source.sha()) || source.protectedBranch() || source.defaultBranch()) throw new AgentGitLabClient.Conflict("核验期间远端候选分支已变化");
            return view(store.receipt(context, record, "REMOTE_CONFIRMED", mergeRequest.iid(), mergeRequest.webUrl(),
                    baselineMatches ? null : "远端候选与 MR 已核验，但目标分支基线已变化", now()));
        } catch (AgentGitLabClient.Rejected ex) {
            return view(store.receipt(context, record, "REJECTED", record.mergeRequestIid(), record.mergeRequestUrl(), ex.getMessage(), null));
        } catch (AgentGitLabClient.Conflict ex) {
            String state = Set.of("PUSHING", "PUSH_UNKNOWN").contains(record.status()) ? "PUSH_UNKNOWN"
                    : Set.of("MR_CREATING", "MR_UNKNOWN").contains(record.status()) ? "MR_UNKNOWN" : "CONFLICT";
            return view(store.receipt(context, record, state, record.mergeRequestIid(), record.mergeRequestUrl(), ex.getMessage(), null));
        } catch (AgentGitLabClient.Failure ex) {
            String state = "PUSHING".equals(record.status()) ? "PUSH_UNKNOWN" : "MR_CREATING".equals(record.status()) ? "MR_UNKNOWN" : record.status();
            return view(store.receipt(context, record, state, record.mergeRequestIid(), record.mergeRequestUrl(), ex.getMessage(), null));
        }
    }

    public List<Map<String, Object>> list(AgentToolContext context, String changeId) { changes.detail(context, changeId); return rows(changeId); }
    public List<Map<String, Object>> listForUser(String changeId, String user) { changes.detailForUser(changeId, user); return rows(changeId); }
    private List<Map<String, Object>> rows(String changeId) { return mapper.list(changeId).stream().map(AgentDeliveryService::view).toList(); }
    static boolean confirmed(AgentDeliveryEntity value) { return Set.of("REMOTE_CONFIRMED", "UNCHANGED_CONFIRMED").contains(value.status()) && value.errorMessage() == null && value.verifiedAt() != null; }
    static Map<String, Object> view(AgentDeliveryEntity value) {
        var result = new LinkedHashMap<String, Object>();
        result.put("id", value.id()); result.put("changeSetId", value.changeSetId()); result.put("repositoryId", value.repositoryId());
        result.put("sourceBranch", value.sourceBranch()); result.put("targetBranch", value.targetBranch()); result.put("resultSha", value.resultSha());
        result.put("status", value.status()); result.put("mergeRequestIid", value.mergeRequestIid()); result.put("mergeRequestUrl", value.mergeRequestUrl());
        result.put("errorMessage", value.errorMessage()); result.put("verifiedAt", value.verifiedAt()); result.put("version", value.version());
        result.put("remoteConfirmed", confirmed(value)); result.put("tested", false); result.put("readyForReview", false);
        return result;
    }
    private static String marker(AgentDeliveryEntity value) { return "Delivery-Ref: " + value.id(); }
    // 2026-11-13 前创建的 MR 描述携带旧标记，核验时两者皆认
    private static String legacyMarker(AgentDeliveryEntity value) { return "WorkHub-Delivery: " + value.id(); }
    private static String description(AgentDeliveryEntity value, AgentChangeView change) {
        var plan = change.manifest().plan();
        return marker(value) + "\nManifest: " + change.manifestHash() + "\nCandidate: " + value.resultSha()
                + "\n\n" + plan.summary() + "\n\n影响范围：\n" + plan.impact() + "\n\n验收建议：\n"
                + plan.acceptance() + "\n\n回退建议：\n" + plan.rollback() + "\n\n候选代码，尚未获得发布批准。";
    }
    private static LocalDateTime now() { return LocalDateTime.now(ZoneOffset.UTC); }
}
