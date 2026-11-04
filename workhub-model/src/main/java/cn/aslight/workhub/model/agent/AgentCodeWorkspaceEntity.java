package cn.aslight.workhub.model.agent;

import java.time.LocalDateTime;

/** 工作区只保存平台生成的对象库标识；源目录来自受控策略，不接受模型路径。 */
public record AgentCodeWorkspaceEntity(String id, String runId, String repositoryId, String policyHash,
                                       String hostId, String baseBranch, String baseSha, String candidateSha,
                                       String sourceBranch, String status, long version,
                                       LocalDateTime createdAt, LocalDateTime updatedAt) { }
