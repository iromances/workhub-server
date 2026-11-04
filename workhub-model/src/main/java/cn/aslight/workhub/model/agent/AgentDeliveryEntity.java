package cn.aslight.workhub.model.agent;

import java.time.LocalDateTime;

/** 每版清单每仓库只有一次交付记录；绑定快照只有凭据引用，不保存密钥。 */
public record AgentDeliveryEntity(String id, String changeSetId, String runId, String repositoryId,
                                  String bindingJson, String bindingHash, String sourceBranch, String targetBranch,
                                  String resultSha, String status, Long mergeRequestIid, String mergeRequestUrl,
                                  String errorMessage, LocalDateTime verifiedAt, long version,
                                  LocalDateTime createdAt, LocalDateTime updatedAt) { }
