package cn.aslight.workhub.model.agent;

import java.util.List;

public record AgentChangeManifest(int schemaVersion, String runId, String taskId, AgentTaskSpecification task,
                                  AgentChangePlan plan, CaseReference caseReference,
                                  List<AgentRepositoryChange> repositories, List<String> dependencyOrder) {
    public record CaseReference(String caseId, long caseVersion, String proposalId, String proposalHash) { }
}
