package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.model.agent.*;
import org.springframework.stereotype.Service;
import java.util.*;

/** 当前候选相关的全部启用独立测试绑定都是必需项，模型不能自行省略。 */
@Service
public class AgentBuildTestPolicy {
    private final AgentJenkinsPolicy jenkins;
    public AgentBuildTestPolicy(AgentJenkinsPolicy jenkins) { this.jenkins = jenkins; }

    public List<AgentJenkinsPolicy.BindingSnapshot> required(AgentChangeManifest manifest) {
        var required = new ArrayList<AgentJenkinsPolicy.BindingSnapshot>(); var covered = new HashSet<String>();
        for (var job : jenkins.current().bindings()) {
            if (!job.enabled() || !"BUILD_TEST".equals(job.stage())) continue;
            var repositories = manifest.repositories().stream().filter(repo -> repo.scope().businessLineCode().equals(job.scope().businessLineCode()))
                    .map(AgentRepositoryChange::repositoryId).collect(java.util.stream.Collectors.toSet());
            if (Collections.disjoint(repositories,job.commitParameters().keySet())) continue;
            if (!repositories.containsAll(job.commitParameters().keySet()))
                throw new AgentCodePolicy.Unavailable("必需测试绑定需要本候选之外的仓库，请补齐共同候选：" + job.id());
            required.add(jenkins.binding(job.id())); covered.addAll(job.commitParameters().keySet());
        }
        if (!covered.equals(new HashSet<>(manifest.repositories().stream().map(AgentRepositoryChange::repositoryId).toList())))
            throw new AgentCodePolicy.Unavailable("当前候选的全部仓库尚未配置独立测试 Job");
        return required.stream().sorted(Comparator.comparing(value -> value.binding().id())).toList();
    }
    public Map<String,String> commits(AgentJenkinsPolicy.BindingSnapshot binding,AgentChangeManifest manifest) {
        var result = new TreeMap<String,String>();
        for (var repository : manifest.repositories()) if (binding.binding().commitParameters().containsKey(repository.repositoryId()))
            result.put(repository.repositoryId(),repository.resultSha());
        return Collections.unmodifiableMap(result);
    }
}
