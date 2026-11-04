package cn.aslight.workhub.model.agent;

import java.util.List;

/** 来自受控 Git 对象的候选快照；sourceBranch 只是计划分支名，未证明远端已存在。 */
public record AgentRepositoryChange(String repositoryId, String repositoryName, AgentScope scope,
                                     String workspaceId, long workspaceVersion, String policyHash,
                                     String baseBranch, String baseSha, String sourceBranch, String resultSha,
                                     List<String> changedPaths, String diffHash, String diffPreview, boolean diffTruncated) { }
