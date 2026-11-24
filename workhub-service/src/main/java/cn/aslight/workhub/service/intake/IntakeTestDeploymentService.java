package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.dao.intake.*;
import cn.aslight.workhub.dao.project.BusinessLineMapper;
import cn.aslight.workhub.model.intake.*;
import cn.aslight.workhub.model.intake.IntakeTestDeployment.*;
import cn.aslight.workhub.service.agent.*;
import cn.aslight.workhub.service.system.SysConfigService;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.*;

/** 提测只操作已登记的测试 Job；合测只写自有测试分支，生产和研发分支均不修改。 */
@Service
public class IntakeTestDeploymentService {
    private final IntakeTestDeploymentMapper deployments;
    private final IntakeMapper intakes;
    private final DevelopmentAnalysisMapper analyses;
    private final BusinessLineMapper lines;
    private final SysConfigService configs;
    private final AgentJenkinsPolicy policies;
    private final AgentJenkinsClient jenkins;
    private final AgentGitLabClient gitlab;
    private final ObjectMapper json;

    public IntakeTestDeploymentService(IntakeTestDeploymentMapper deployments, IntakeMapper intakes,
            DevelopmentAnalysisMapper analyses, BusinessLineMapper lines, SysConfigService configs,
            AgentJenkinsPolicy policies, AgentJenkinsClient jenkins, AgentGitLabClient gitlab, ObjectMapper json) {
        this.deployments=deployments; this.intakes=intakes; this.analyses=analyses; this.lines=lines;
        this.configs=configs; this.policies=policies; this.jenkins=jenkins; this.gitlab=gitlab; this.json=json;
    }

    /** 返回本需求已配置的测试目标，未配置不猜测 Job。 */
    public List<Binding> bindings(Long intakeId) {
        var intake = requireIntake(intakeId);
        String raw = configs.findPlainValue("intake.testDeploy", "bindings");
        if (raw == null || raw.isBlank()) return List.of();
        var systems = systems(intakeId);
        return Arrays.stream(json.readValue(raw, Binding[].class)).filter(Binding::enabled)
                .filter(b -> Objects.equals(b.businessLineCode(), intake.getBusinessLineCode()))
                .filter(b -> systems.contains(b.systemName())).peek(IntakeTestDeploymentService::validateBinding).toList();
    }

    /** 校验测试环境、精确绑定和来源需求；在禅道等外部写入之前调用。 */
    public Binding validateRequest(Long intakeId, Request request) {
        if (request == null || request.bindingId() == null) throw new IllegalArgumentException("请选择已配置的测试发布目标");
        var binding = bindings(intakeId).stream().filter(b -> b.id().equals(request.bindingId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("需求没有该测试发布绑定，未触发构建"));
        var primary = requireIntake(intakeId);
        var ids = sourceIds(intakeId, request);
        for (Long id : ids) {
            var source = requireIntake(id);
            if (!Objects.equals(primary.getBusinessLineCode(), source.getBusinessLineCode()) || !systems(id).contains(binding.systemName()))
                throw new IllegalArgumentException("合测需求必须属于同一业务线且涉及所选系统");
            if (!Set.of("开发完成", "测试中", "开发中").contains(source.getDemandStatus()))
                throw new IllegalArgumentException("仅开发中、开发完成或测试中的需求可发布测试");
            requireDevelopmentBranch(source.getDevelopmentBranchName());
        }
        if (request.previousId() != null && !request.previousId().isBlank()) {
            var previous = requirePrevious(intakeId, request.previousId());
            var plan = plan(previous);
            if (!plan.binding().equals(binding) || !plan.sources().stream().map(Source::intakeId).toList().equals(ids))
                throw new IllegalArgumentException("重新发布须沿用原批次的测试目标和需求，新增合测请新建批次");
            if (previous.activeKey() != null) throw new IllegalArgumentException("原发布仍在进行或结果待核实，不能重复发布");
        }
        validateJob(binding, target(binding));
        return binding;
    }

    /** 持久化发布意图后由任务调度执行；数据库活动键覆盖所有应用实例。 */
    @Transactional
    public View submit(Long intakeId, Request request, String actor) {
        Binding binding = validateRequest(intakeId, request);
        var repository = repository(binding);
        List<Source> sources = new ArrayList<>();
        for (Long id : sourceIds(intakeId, request)) {
            var intake = requireIntake(id);
            var branch = gitlab.branch(repository, intake.getDevelopmentBranchName());
            if (branch == null) throw new IllegalArgumentException("远程研发分支不存在：" + intake.getDevelopmentBranchName());
            sources.add(new Source(id, intake.getRequirementName(), branch.name(), branch.sha()));
        }
        String id = UUID.randomUUID().toString();
        String branch = sources.getFirst().branch();
        String base = null;
        if (sources.size() > 1) {
            if (request.previousId() != null && !request.previousId().isBlank()) {
                var previous = plan(requirePrevious(intakeId, request.previousId()));
                branch = previous.branch(); base = previous.baseSha();
            } else {
                var master = gitlab.branch(repository, "master");
                if (master == null) throw new IllegalArgumentException("缺少生产基线 master，未新建测试分支");
                base = master.sha(); branch = "test/workhub-" + id.replace("-", "");
            }
            AgentGitLabClient.requireTestBranch(branch);
        }
        var plan = new Plan(binding, List.copyOf(sources), branch, base, request.previousId(),
                sources.size() == 1 ? sources.getFirst().sha() : null);
        var row = new Row(id, intakeId, binding.instanceId()+":"+binding.jobName(), json.writeValueAsString(plan),
                "PENDING", null, null, null, null, "等待准备测试发布", actor, null, null);
        try { deployments.insert(row); }
        catch (DuplicateKeyException ex) { throw new IllegalArgumentException("该测试系统已有发布进行中或待核实，未重复触发"); }
        return view(deployments.find(id));
    }

    public List<View> history(Long intakeId) {
        requireIntake(intakeId);
        return deployments.history(intakeId).stream().map(this::view).toList();
    }

    /** 只执行已提交意图，提交前落 SUBMITTING；中断或超时不自动重新触发。 */
    public void watch() {
        deployments.markInterrupted();
        for (var row : deployments.pending()) {
            try {
                if ("PENDING".equals(row.status())) {
                    if (deployments.claim(row.id()) == 1) prepare(deployments.find(row.id()));
                } else observe(row);
            } catch (RuntimeException ex) {
                var current = deployments.find(row.id());
                boolean preparing = "PREPARING".equals(current.status());
                save(current, plan(current), preparing ? "FAILED" : "UNKNOWN", current.queueId(),
                        current.buildNumber(), current.actualSha(), safeMessage(ex), preparing);
            }
        }
    }

    private void prepare(Row row) {
        var plan = plan(row);
        var binding = plan.binding();
        requireCurrent(row, plan);
        var repository = repository(binding);
        for (var source : plan.sources()) {
            var current = gitlab.branch(repository, source.branch());
            if (current == null || !source.sha().equals(current.sha())) throw new IllegalArgumentException("研发分支已变化，请重新提交测试发布");
        }
        if (plan.sources().size() > 1) {
            var testBranch = gitlab.branch(repository, plan.branch());
            if (plan.previousId() == null || plan.previousId().isBlank()) {
                if (testBranch != null) throw new IllegalArgumentException("新测试分支已存在，未覆盖");
                gitlab.createTestBranch(repository, plan.branch(), plan.baseSha());
            } else if (testBranch == null) {
                throw new IllegalArgumentException("原测试分支已删除，请人工确认后新建批次");
            }
            for (var source : plan.sources()) gitlab.mergeIntoTestBranch(repository, source.branch(), plan.branch(), source.sha());
            plan = new Plan(binding, plan.sources(), plan.branch(), plan.baseSha(), plan.previousId(),
                    gitlab.branch(repository, plan.branch()).sha());
        }
        var target = target(binding);
        validateJob(binding, target);
        requireCurrent(row, plan);
        row = save(row, plan, "SUBMITTING", null, null, null, "正在提交测试构建", false);
        try {
            long queue = jenkins.submit(target, IntakeTestDeployment.parameters(plan));
            save(row, plan, "QUEUED", queue, null, null, "已提交 Jenkins，等待测试构建", false);
        } catch (AgentJenkinsClient.SubmissionRejected ex) {
            save(row, plan, "FAILED", null, null, null, safeMessage(ex), true);
        }
    }

    private void observe(Row row) {
        var plan = plan(row);
        requireCurrent(row, plan);
        var target = target(plan.binding());
        var parameters = IntakeTestDeployment.parameters(plan);
        Long number = row.buildNumber();
        if (number == null) {
            var queue = jenkins.queue(target, row.queueId(), parameters);
            if ("EXPIRED".equals(queue.state())) number = jenkins.buildForQueue(target, row.queueId());
            else {
                if (!queue.parametersMatch()) throw new IllegalStateException("Jenkins 队列参数与测试发布不一致");
                if ("CANCELLED".equals(queue.state())) {
                    save(row, plan, "ABORTED", row.queueId(), null, null, "测试构建已取消", true); return;
                }
                number = queue.buildNumber();
                if (number == null) return;
            }
            if (number == null) throw new IllegalStateException("队列已过期且未定位到原构建，请人工核对");
        }
        var build = jenkins.build(target, number, parameters);
        if (!build.parametersMatch() || !Objects.equals(row.queueId(), build.queueId()))
            throw new IllegalStateException("Jenkins 构建参数或队列不一致，请人工核对");
        if ("RUNNING".equals(build.state())) {
            save(row, plan, "RUNNING", row.queueId(), number, null, "正在构建并发布测试环境", false); return;
        }
        if (!"FINISHED".equals(build.state())) throw new IllegalStateException("构建状态尚不能确认");
        if (!"SUCCESS".equals(build.result())) {
            save(row, plan, "FAILED", row.queueId(), number, null,
                    "Jenkins " + build.result() + "；请查看构建日志（含脚本平台和部署步骤）", true); return;
        }
        String actual = jenkins.repositoryRevision(target, number, plan.binding().projectPath());
        boolean verified = Objects.equals(plan.expectedSha(), actual) && actual != null;
        save(row, plan, verified ? "SUCCESS" : "VERSION_MISMATCH", row.queueId(), number, actual,
                verified ? "Jenkins 流水线成功且业务代码版本一致；应用健康和业务测试须另行验证"
                        : "Jenkins 成功，但业务构建版本与预期不一致或无法核实，不能视为部署验收通过", true);
    }

    private Row save(Row row, Plan plan, String status, Long queue, Long number, String sha, String message, boolean terminal) {
        String base = plan.binding().baseUrl();
        String url = number != null ? base + "job/" + plan.binding().jobName() + "/" + number + "/"
                : queue != null ? base + "queue/item/" + queue + "/" : null;
        var next = new Row(row.id(), row.intakeId(), terminal ? null : row.activeKey(), json.writeValueAsString(plan),
                status, queue, number, url, sha, message, row.requestedBy(), row.createdAt(), row.updatedAt());
        deployments.update(next); deployments.updateReview(next); return next;
    }

    private void requireCurrent(Row row, Plan plan) {
        if (!bindings(row.intakeId()).contains(plan.binding())) throw new IllegalArgumentException("测试绑定已变化，停止原发布");
        for (var source : plan.sources()) {
            var intake = requireIntake(source.intakeId());
            if (!source.branch().equals(intake.getDevelopmentBranchName())
                    || !plan.binding().businessLineCode().equals(intake.getBusinessLineCode()))
                throw new IllegalArgumentException("需求归属或开发分支已变化，请核对原测试发布");
        }
    }

    static void validateBinding(Binding b) {
        String env = b.environmentCode() == null ? "" : b.environmentCode().trim().toUpperCase();
        String prefix = env.toLowerCase() + "-";
        if (env.isEmpty() || "PROD".equals(env) || b.jobName() == null || !b.jobName().startsWith(prefix)
            || !b.jobName().matches(prefix + "[A-Za-z0-9_-]+") || b.deployTarget() == null || !b.deployTarget().startsWith(prefix)
            || b.serverAddress() == null || b.serverAddress().isBlank() || b.publicBranch() == null || b.publicBranch().isBlank())
            throw new IllegalArgumentException("提测只允许明确登记的非生产测试 Job 和测试目标，禁止生产发布");
    }

    private void validateJob(Binding binding, AgentJenkinsClient.Target target) {
        var job = jenkins.testJobDefinition(target);
        if (!binding.jobName().equals(job.path("fullName").asText()) || !job.path("buildable").asBoolean())
            throw new IllegalArgumentException("测试 Job 不可构建或身份不符");
        var names = new HashSet<String>(); boolean targetConfirmed = false;
        for (String section : List.of("actions", "property")) for (var property : job.path(section))
            for (var parameter : property.path("parameterDefinitions")) {
                String name = parameter.path("name").asText(); names.add(name);
                if ("deploy_test_env".equals(name)) {
                    var choices = parameter.path("choices");
                    targetConfirmed = choices.isArray() && choices.size() == 1 && binding.deployTarget().equals(choices.get(0).asText());
                }
            }
        if (!targetConfirmed || !names.containsAll(List.of("deploy_test_env", "project_branch", "thctay_public_branch")))
            throw new IllegalArgumentException("Job 参数或唯一测试目标无法核实，未触发构建");
    }

    private AgentJenkinsClient.Target target(Binding binding) {
        validateBinding(binding);
        var instance = policies.current().instances().stream().filter(i -> i.enabled() && i.id().equals(binding.instanceId()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("系统集成 Jenkins 实例未启用"));
        if (!URI.create(instance.baseUrl().replaceAll("/+$", "") + "/").equals(URI.create(binding.baseUrl())))
            throw new IllegalArgumentException("Jenkins 实例地址已变化，请重新核实测试目标");
        return new AgentJenkinsClient.Target(URI.create(instance.baseUrl()), List.of(binding.jobName()), instance.userName(),
                configs.requireSecretValue(instance.credentialGroup(), instance.tokenKey()));
    }

    private AgentGitLabClient.Target repository(Binding binding) {
        var line = lines.findByCode(binding.businessLineCode());
        if (line == null || !Boolean.TRUE.equals(line.getEnabled()) ||
                !Objects.equals(line.getGitlabGroupName()+"/"+binding.systemName(), binding.projectPath()))
            throw new IllegalArgumentException("测试绑定仓库与业务线不符");
        return gitlab.resolve(URI.create(configs.requirePlainValue("gitlab.global", "webApiUrl")),
                binding.projectPath(), configs.requirePlainValue("gitlab.global", "accessToken"));
    }
    private IntakeRecordEntity requireIntake(Long id) {
        var intake = id == null ? null : intakes.findById(id);
        if (intake == null) throw new IllegalArgumentException("需求不存在");
        return intake;
    }
    private Set<String> systems(Long id) {
        var analysis = analyses.findLatestByIntakeId(id);
        if (analysis == null || analysis.getDraftJson() == null) return Set.of();
        var draft = json.readValue(analysis.getDraftJson(), DevelopmentAnalysisDraft.class);
        var result = new HashSet<String>();
        if (draft.workItems() != null) for (var item : draft.workItems()) if (item.systemTags() != null) result.addAll(item.systemTags());
        return result;
    }
    private List<Long> sourceIds(Long intakeId, Request request) {
        var ids = new TreeSet<Long>(); ids.add(intakeId);
        if (request.intakeIds() != null) {
            if (request.intakeIds().size() > 20 || request.intakeIds().stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("一次合测最多20个需求");
            ids.addAll(request.intakeIds());
        }
        if (ids.size() > 20) throw new IllegalArgumentException("一次合测最多20个需求");
        return List.copyOf(ids);
    }
    private Row requirePrevious(Long intakeId, String id) {
        var row = deployments.find(id);
        if (row == null || !Objects.equals(row.intakeId(), intakeId)) throw new IllegalArgumentException("原测试批次不属于此需求");
        return row;
    }
    static void requireDevelopmentBranch(String branch) {
        if (branch == null || !branch.matches("[A-Za-z0-9][A-Za-z0-9_./-]{0,199}") || branch.contains("..")
                || Set.of("master", "main", "prod", "production").contains(branch) || branch.startsWith("test/") || branch.startsWith("prod/"))
            throw new IllegalArgumentException("需求必须使用明确的开发分支，不能直接发布生产基线或测试分支");
    }
    private Plan plan(Row row) { return json.readValue(row.planJson(), Plan.class); }
    private View view(Row row) { return new View(row.id(), row.intakeId(), plan(row), row.status(), row.buildNumber(),
            row.buildUrl(), row.actualSha(), row.message(), row.requestedBy(), row.createdAt()); }
    private String safeMessage(RuntimeException ex) {
        return ex.getMessage() != null && (ex instanceof IllegalArgumentException || ex instanceof AgentGitLabClient.Failure || ex instanceof AgentJenkinsClient.ClientFailure)
                ? ex.getMessage().substring(0, Math.min(ex.getMessage().length(), 1800))
                : "测试发布处理失败，请核对配置与日志（" + ex.getClass().getSimpleName() + "）";
    }
}
