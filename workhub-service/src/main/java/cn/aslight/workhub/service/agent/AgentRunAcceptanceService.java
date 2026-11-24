package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.dao.agent.*;
import cn.aslight.workhub.model.agent.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.*;
import java.util.*;

/** 只以本次运行的实际领域记录验收产物；不调用 AI、GitLab 或 Jenkins。 */
@Service
public class AgentRunAcceptanceService {
    private final AgentMapper agents;
    private final AgentChangeMapper changes;
    private final AgentDeliveryMapper deliveries;
    // ChangeService 使用 RunStore；延迟解析只为打断现有领域服务依赖环。
    private final ObjectProvider<AgentChangeService> changeService;
    private final AgentAccessService access;
    private final AgentBuildTestEvidence testEvidence;
    private final ObjectMapper json;
    private final Clock clock;
    private final AgentNativeDeliveryService nativeDelivery;

    @Autowired
    public AgentRunAcceptanceService(AgentMapper agents, AgentChangeMapper changes, AgentDeliveryMapper deliveries,
                                     ObjectProvider<AgentChangeService> changeService, AgentBuildTestEvidence testEvidence, AgentAccessService access, ObjectMapper json,
                                     AgentNativeDeliveryService nativeDelivery) {
        this(agents, changes, deliveries, changeService, testEvidence, access, json, Clock.systemUTC(), nativeDelivery);
    }
    AgentRunAcceptanceService(AgentMapper agents, AgentChangeMapper changes, AgentDeliveryMapper deliveries,
                              ObjectProvider<AgentChangeService> changeService, AgentBuildTestEvidence testEvidence, AgentAccessService access, ObjectMapper json, Clock clock,
                              AgentNativeDeliveryService nativeDelivery) {
        this.agents = agents; this.changes = changes; this.deliveries = deliveries; this.changeService = changeService;
        this.access = access; this.json = json; this.clock = clock; this.testEvidence = testEvidence;
        this.nativeDelivery = nativeDelivery;
    }

    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public AgentRunAcceptance preview(String taskId, String runId, String user) {
        var actor = access.actor(user); actor.require("agent:task:view");
        var run = agents.findRun(runId);
        if (run == null || !run.taskId().equals(taskId)) throw new IllegalArgumentException("运行不属于当前任务");
        access.requireAgent(actor, agents.findAgent(run.agentId()));
        return check(run, user, false);
    }

    /** 仅由 RunStore 在运行锁内调用；证据、事件与完成状态共享事务。 */
    AgentRunAcceptance record(AgentRunEntity run) {
        return check(run, run.requestedBy(), true);
    }

    private AgentRunAcceptance check(AgentRunEntity run, String user, boolean persist) {
        var spec = json.readValue(run.specificationJson(), AgentTaskSpecification.class);
        var items = spec.expectedArtifacts().stream().distinct().map(kind -> switch (kind) {
            case "REPORT" -> report(run);
            case "CHANGE_SET" -> code(run, user, persist);
            case "TEST_REPORT" -> tests(run,user,persist);
            case "DEPLOYMENT" -> environment(kind, spec, "尚未完成本目标环境的实际发布凭据验收绑定");
            case "VERIFICATION" -> environment(kind, spec, "尚未完成本目标环境的业务复查及原问题结果验收绑定");
            default -> throw new IllegalArgumentException("不支持的任务产物类型");
        }).toList();
        return new AgentRunAcceptance(run.id(), LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC),
                !items.isEmpty() && items.stream().allMatch(value -> "SATISFIED".equals(value.status())), items);
    }

    private AgentRunAcceptance.Item tests(AgentRunEntity run,String user,boolean persist) {
        try {
            access.actor(user).require("agent:code:use");
            var latest=changes.list(run.id(),0,1);
            if(latest.isEmpty())return item("TEST_REPORT","MISSING","尚未取得当前代码清单及实际构建测试证据");
            var entity=latest.getFirst();
            var context=new AgentToolContext(run.id(),run.taskId(),run.agentId(),run.requestedBy(),run.ownerToken(),
                    json.readValue(run.configurationJson(),AgentConfiguration.class),json.readValue(run.specificationJson(),AgentTaskSpecification.class));
            var view=persist?changeService.getObject().forAcceptance(context,entity.id()):changeService.getObject().detailForUser(entity.id(),user);
            if(!entity.runId().equals(run.id()) || !view.runId().equals(run.id()) || !view.current())return item("TEST_REPORT","BLOCKED","当前代码或问题依据已变化，请重新测试当前候选");
            var evidence=testEvidence.check(entity,view.manifest());
            if(!evidence.satisfied())return item("TEST_REPORT","MISSING","尚未取得全部必需 Job 对当前准确提交的实际构建测试证据："+String.join("；",evidence.missing()));
            return new AgentRunAcceptance.Item("TEST_REPORT","SATISFIED","当前候选的全部必需独立构建测试已实际通过",
                    evidence.artifacts().stream().map(artifact->new AgentRunAcceptance.Evidence("ARTIFACT",artifact.id(),artifact.contentHash())).toList());
        }catch(AccessDeniedException ex){return item("TEST_REPORT","BLOCKED","代码或关联证据的当前权限不足，无法验收");}
        catch(AgentCodePolicy.Unavailable ex){return item("TEST_REPORT","UNAVAILABLE","当前测试策略不可用，无法验收");}
    }

    private AgentRunAcceptance.Item environment(String kind, AgentTaskSpecification spec, String message) {
        try { spec.validateCompletionTargets(); }
        catch (IllegalArgumentException ex) { return item(kind, "BLOCKED", ex.getMessage()); }
        var targets = spec.completionTargets().stream()
                .map(scope -> new AgentRunAcceptance.Target(scope, "UNAVAILABLE", message, List.of())).toList();
        return new AgentRunAcceptance.Item(kind, "UNAVAILABLE", "按明确选择的完成目标逐项核验，实际凭据验收仍待接通", List.of(), targets);
    }

    private AgentRunAcceptance.Item report(AgentRunEntity run) {
        var report = agents.listArtifacts(run.id()).stream().filter(value -> "REPORT".equals(value.kind())
                && "AI_REPORT".equals(value.provenance()) && value.runId().equals(run.id())
                && AgentCodePolicy.hash(value.payloadJson()).equals(value.contentHash())
                && !json.readTree(value.payloadJson()).path("text").asText().isBlank()).reduce((first, last) -> last).orElse(null);
        return report == null ? item("REPORT", "MISSING", "尚未保存有效分析报告")
                : new AgentRunAcceptance.Item("REPORT", "SATISFIED", "已保存 AI 分析报告",
                List.of(new AgentRunAcceptance.Evidence("ARTIFACT", report.id(), report.contentHash())));
    }

    private AgentRunAcceptance.Item code(AgentRunEntity run, String user, boolean persist) {
        try {
            access.actor(user).require("agent:code:use");
            var nativeEvidence = nativeDelivery.check(run);
            if (nativeEvidence != null) return nativeEvidence;
            var latest = changes.list(run.id(), 0, 1);
            if (latest.isEmpty()) return item("CHANGE_SET", "MISSING", "尚未取得本次运行的代码交付清单");
            var entity = latest.getFirst();
            if (!entity.runId().equals(run.id()) || !AgentCodePolicy.hash(entity.manifestJson()).equals(entity.manifestHash())) {
                throw new IllegalStateException("代码交付清单校验失败");
            }
            var context = new AgentToolContext(run.id(), run.taskId(), run.agentId(), run.requestedBy(), run.ownerToken(),
                    json.readValue(run.configurationJson(), AgentConfiguration.class),
                    json.readValue(run.specificationJson(), AgentTaskSpecification.class));
            var view = persist ? changeService.getObject().forAcceptance(context, entity.id())
                    : changeService.getObject().detailForUser(entity.id(), user);
            if (!view.manifest().runId().equals(run.id()) || !view.manifest().taskId().equals(run.taskId())) {
                throw new IllegalStateException("代码清单与运行不一致");
            }
            if (!view.current()) return item("CHANGE_SET", "BLOCKED", "代码、问题方案、缺陷上下文或授权策略已变化，请重新准备当前交付清单");
            var rows = deliveries.list(view.id());
            if (view.blockers().contains("REMOTE_DELIVERY_EVIDENCE_MISSING") || !matches(view, rows)) {
                return item("CHANGE_SET", "MISSING", "尚未取得全部仓库准确提交对应的远端分支及 MR 核验凭据");
            }
            // 引用原始 CODE_DELIVERY 事件中的准确版本；凭据内容只保存 ID 和摘要，不扩散代码或绑定信息。
            var receipts = rows.stream().sorted(Comparator.comparing(AgentDeliveryEntity::repositoryId))
                    .map(row -> new Receipt(row.id(), row.version(), AgentCodePolicy.hash(agents.findEventByKey(run.id(), "delivery:" + row.id() + ":" + row.version()).payloadJson()))).toList();
            String payload = json.writeValueAsString(new CodeEvidence(1, run.id(), run.taskId(), view.id(), view.manifestHash(), receipts));
            String hash = AgentCodePolicy.hash(payload);
            if (persist) {
                String key = "change:" + view.id() + ":" + hash;
                var existing = agents.findArtifact(run.id(), key);
                if (existing == null) {
                    agents.insertArtifact(new AgentArtifactEntity(UUID.randomUUID().toString(), run.id(), key, "CHANGE_SET",
                            "PLATFORM_EXECUTION", payload, hash, LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)));
                } else if (!payload.equals(existing.payloadJson()) || !hash.equals(existing.contentHash())
                        || !"CHANGE_SET".equals(existing.kind()) || !"PLATFORM_EXECUTION".equals(existing.provenance())) {
                    throw new IllegalStateException("已有代码产物与实际凭据不一致");
                }
            }
            return new AgentRunAcceptance.Item("CHANGE_SET", "SATISFIED", "当前清单的全部仓库已交付并核验；实测、发布及问题解决需另行验证",
                    List.of(new AgentRunAcceptance.Evidence("CHANGE_SET", view.id(), view.manifestHash())));
        } catch (AccessDeniedException ex) {
            return item("CHANGE_SET", "BLOCKED", "代码或关联来源的当前权限不足，无法验收");
        } catch (AgentCodePolicy.Unavailable ex) {
            return item("CHANGE_SET", "UNAVAILABLE", "当前代码或远端交付策略不可用，无法验收");
        }
    }

    private boolean matches(AgentChangeView view, List<AgentDeliveryEntity> rows) {
        var repos = view.manifest().repositories();
        if (rows.size() != repos.size()) return false;
        for (var repo : repos) {
            var row = rows.stream().filter(value -> value.repositoryId().equals(repo.repositoryId())).findFirst().orElse(null);
            if (row == null || !row.runId().equals(view.runId()) || !row.changeSetId().equals(view.id())
                    || !AgentDeliveryService.confirmed(row) || !row.resultSha().equals(repo.resultSha())
                    || !row.targetBranch().equals(repo.baseBranch())) return false;
            var receipt = agents.findEventByKey(view.runId(), "delivery:" + row.id() + ":" + row.version());
            String actual = json.writeValueAsString(new AgentToolData(json).sanitize(AgentDeliveryService.view(row)));
            if (receipt == null || !"CODE_DELIVERY".equals(receipt.kind())
                    || !AgentCodePolicy.hash(actual).equals(AgentCodePolicy.hash(receipt.payloadJson()))) return false;
            if (repo.changedPaths().isEmpty()) {
                if (!"UNCHANGED_CONFIRMED".equals(row.status()) || !repo.baseSha().equals(row.resultSha())
                        || !row.sourceBranch().equals(repo.baseBranch())) return false;
            } else if (!"REMOTE_CONFIRMED".equals(row.status()) || row.mergeRequestIid() == null || row.mergeRequestIid() < 1
                    || row.mergeRequestUrl() == null || row.mergeRequestUrl().isBlank()
                    || !row.sourceBranch().equals(repo.sourceBranch() + "-r" + view.revision())) return false;
        }
        return true;
    }
    private static AgentRunAcceptance.Item item(String kind, String status, String message) {
        return new AgentRunAcceptance.Item(kind, status, message, List.of());
    }
    private record Receipt(String id, long version, String contentHash) { }
    private record CodeEvidence(int schemaVersion, String runId, String taskId, String changeSetId, String manifestHash, List<Receipt> receipts) { }
}
