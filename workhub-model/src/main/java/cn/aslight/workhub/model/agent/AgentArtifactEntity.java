package cn.aslight.workhub.model.agent;

import java.time.LocalDateTime;

/** provenance 区分 AI 报告与平台实际执行证据，模型不能自行写入测试/部署成功产物。 */
public record AgentArtifactEntity(String id, String runId, String artifactKey, String kind,
                                   String provenance, String payloadJson, String contentHash, LocalDateTime createdAt) {
}
