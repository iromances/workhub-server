package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.dao.agent.AgentMapper;
import cn.aslight.workhub.dao.intake.*;
import cn.aslight.workhub.dao.project.BusinessLineMapper;
import cn.aslight.workhub.model.agent.*;
import cn.aslight.workhub.model.intake.DevelopmentAnalysisDraft;
import cn.aslight.workhub.service.system.SysConfigService;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Instant;
import java.util.*;

/** 原生 CLI 交付声明由平台独立回读 GitLab；声明本身不构成完成凭据。 */
@Service
public class AgentNativeDeliveryService {
    private final IntakeAiDevelopmentMapper developments;
    private final IntakeMapper intakes;
    private final DevelopmentAnalysisMapper analyses;
    private final BusinessLineMapper businessLines;
    private final SysConfigService configs;
    private final AgentGitLabClient gitlab;
    private final AgentMapper agents;
    private final AgentAccessService access;
    private final ObjectMapper json;

    public AgentNativeDeliveryService(IntakeAiDevelopmentMapper developments, IntakeMapper intakes,
                                      DevelopmentAnalysisMapper analyses, BusinessLineMapper businessLines,
                                      SysConfigService configs, AgentGitLabClient gitlab, AgentMapper agents,
                                      AgentAccessService access, ObjectMapper json) {
        this.developments = developments; this.intakes = intakes; this.analyses = analyses;
        this.businessLines = businessLines; this.configs = configs; this.gitlab = gitlab;
        this.agents = agents; this.access = access; this.json = json;
    }

    /** 在 CLI 已退出后、落最终回执前调用；不持有数据库事务，不推送或创建 MR。 */
    public Verification verify(AgentToolContext context, JsonNode declarations) {
        try {
            access.requireRunAuthorization(context);
            access.actor(context.requestedBy()).require("agent:code:use");
            Target target = target(context);
            if (target == null) return missing(context, "当前任务没有需求研发分支交付目标");
            List<Delivery> deliveries = declarations(declarations, target);
            String token = configs.requirePlainValue("gitlab.global", "accessToken");
            for (var delivery : deliveries) {
                var project = gitlab.resolve(URI.create(target.baseUrl()), delivery.projectPath(), token);
                var branch = gitlab.branch(project, delivery.branch());
                if (branch == null || !branch.sha().equals(delivery.commit())) {
                    return missing(context, "远端研发分支提交与 CLI 交付声明不符：" + delivery.projectPath());
                }
            }
            // 网络查询后再次核对授权和需求，不能把途中变更的目标登记为已满足。
            access.requireRunAuthorization(context);
            if (!target.equals(target(context))) return missing(context, "交付核验期间需求或任务拆解已改变");
            return new Verification(context.runId(), context.taskId(), "SATISFIED", target.hash(),
                    Instant.now().toString(), deliveries, "已独立核验全部目标仓库研发分支的准确提交；不代表测试发布或联调通过");
        } catch (RuntimeException ex) {
            // 不把 HTTP/配置异常中的凭据或响应正文传入产物。
            return missing(context, "原生代码交付未核验，请检查目标、权限及 GitLab 可用性（" + ex.getClass().getSimpleName() + "）");
        }
    }

    /** 验收阶段只读本次 CLI 对应的平台回执，不发网络请求。普通任务返回 null，沿用旧清单。 */
    public AgentRunAcceptance.Item check(AgentRunEntity run) {
        if (run.providerBindingJson() == null
                || !"CLI".equals(json.readTree(run.providerBindingJson()).path("channelType").asText())) return null;
        var context = new AgentToolContext(run.id(), run.taskId(), run.agentId(), run.requestedBy(), run.ownerToken(),
                json.readValue(run.configurationJson(), AgentConfiguration.class),
                json.readValue(run.specificationJson(), AgentTaskSpecification.class));
        try {
            Target target = target(context);
            if (target == null) return null;
            var result = agents.listEvents(run.id()).reversed().stream()
                    .filter(event -> "CLI_RESULT".equals(event.kind()) && event.eventKey().endsWith(":result"))
                    .findFirst().orElse(null);
            if (result == null) return item("MISSING", "尚未取得原生 CLI 的最终交付结果");
            var finalResult = json.readTree(result.payloadJson());
            if (finalResult.path("exitCode").asInt(-1) != 0 || !finalResult.path("processTreeStopped").asBoolean()
                    || !"RESULT".equals(finalResult.path("result").path("type").asText())) {
                return item("MISSING", "原生 CLI 尚未成功结束本次交付");
            }
            var event = agents.findEventByKey(run.id(), result.eventKey().replaceFirst(":result$", ":delivery"));
            if (event == null || !"NATIVE_CODE_DELIVERY".equals(event.kind())) {
                return item("MISSING", "尚未取得平台核验的原生 CLI 交付凭据；请补充 deliveries 后重新核验");
            }
            var receipt = json.readValue(event.payloadJson(), Verification.class);
            if (!run.id().equals(receipt.runId()) || !run.taskId().equals(receipt.taskId())) {
                return item("BLOCKED", "原生交付凭据不属于本次运行");
            }
            if (!"SATISFIED".equals(receipt.status())) return item("MISSING", receipt.message());
            if (!target.hash().equals(receipt.targetHash())) return item("BLOCKED", "需求研发分支或任务拆解已变化，请重新核验交付");
            List<Delivery> declared = declarations(finalResult.path("result").path("deliveries"), target);
            if (!declared.equals(receipt.deliveries()) || receipt.verifiedAt() == null) return item("BLOCKED", "原生交付声明与平台凭据不一致");
            return new AgentRunAcceptance.Item("CHANGE_SET", "SATISFIED", receipt.message(),
                    List.of(new AgentRunAcceptance.Evidence("EVENT", event.id(), AgentCodePolicy.hash(event.payloadJson()))));
        } catch (RuntimeException ex) {
            return item("BLOCKED", "原生交付目标或凭据当前不可确认，请检查需求与权限");
        }
    }

    private Target target(AgentToolContext context) {
        var development = developments.findByAgentTaskId(context.taskId());
        if (development == null) return null;
        if (!context.agentId().equals(development.getAgentId())) throw new IllegalArgumentException("需求数字员工不符");
        var intake = intakes.findById(development.getIntakeId());
        if (intake == null || !Objects.equals(development.getDevelopmentBranch(), intake.getDevelopmentBranchName())) {
            throw new IllegalArgumentException("需求研发分支已变化");
        }
        String branch = development.getDevelopmentBranch();
        AgentCodePolicy.branch(branch);
        var scope = context.specification().scopes().stream()
                .filter(value -> value.businessLineCode().equals(intake.getBusinessLineCode())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("需求业务线不属于本任务"));
        access.requireScope(context, scope);
        var businessLine = businessLines.findByCode(scope.businessLineCode());
        if (businessLine == null || Boolean.FALSE.equals(businessLine.getEnabled())
                || businessLine.getGitlabGroupName() == null) throw new IllegalArgumentException("业务线 GitLab 组不可用");
        var analysis = analyses.findLatestByIntakeId(development.getIntakeId());
        if (analysis == null) throw new IllegalArgumentException("缺少任务拆解");
        var draft = json.readValue(analysis.getDraftJson(), DevelopmentAnalysisDraft.class);
        var projects = new TreeSet<String>();
        if (draft.workItems() == null || draft.workItems().isEmpty()) throw new IllegalArgumentException("缺少任务项");
        for (var item : draft.workItems()) {
            if (item.systemTags() == null || item.systemTags().isEmpty()) throw new IllegalArgumentException("任务项没有明确系统标签");
            for (String tag : item.systemTags()) {
                if (tag == null || !tag.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,199}")) throw new IllegalArgumentException("系统标签尚未映射准确仓库");
                projects.add(businessLine.getGitlabGroupName() + "/" + tag);
            }
        }
        if (projects.size() > 20) throw new IllegalArgumentException("一次交付最多二十个仓库");
        String baseUrl = configs.requirePlainValue("gitlab.global", "webApiUrl");
        String hash = AgentCodePolicy.hash(json.writeValueAsString(List.of(development.getIntakeId(), context.taskId(),
                scope, branch, baseUrl, projects, analysis.getDraftJson())));
        return new Target(baseUrl, branch, List.copyOf(projects), hash);
    }

    private List<Delivery> declarations(JsonNode values, Target target) {
        if (!values.isArray() || values.size() != target.projects().size()) throw new IllegalArgumentException("交付清单未覆盖全部系统");
        var deliveries = new TreeMap<String, Delivery>();
        for (var value : values) {
            String project = value.path("projectPath").asText();
            String branch = value.path("branch").asText();
            String commit = AgentCodeGit.sha(value.path("commit").asText());
            if (!target.projects().contains(project) || !target.branch().equals(branch)
                    || deliveries.put(project, new Delivery(project, branch, commit)) != null) {
                throw new IllegalArgumentException("交付仓库或研发分支不符或重复");
            }
        }
        return List.copyOf(deliveries.values());
    }

    private static Verification missing(AgentToolContext context, String message) {
        return new Verification(context.runId(), context.taskId(), "MISSING", null, null, List.of(), message);
    }
    private static AgentRunAcceptance.Item item(String status, String message) {
        return new AgentRunAcceptance.Item("CHANGE_SET", status, message, List.of());
    }
    private record Target(String baseUrl, String branch, List<String> projects, String hash) { }
    public record Delivery(String projectPath, String branch, String commit) { }
    public record Verification(String runId, String taskId, String status, String targetHash, String verifiedAt,
                               List<Delivery> deliveries, String message) { }
}
