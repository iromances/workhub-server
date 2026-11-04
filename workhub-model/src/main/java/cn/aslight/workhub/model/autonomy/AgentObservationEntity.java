package cn.aslight.workhub.model.autonomy;

import java.time.LocalDateTime;

/** 企业工具实际取得的脱敏证据，来源和范围由服务端固定。 */
public record AgentObservationEntity(String id, String runId, String agentId, String toolKey, String toolCode,
                                      String sourceType, String businessLineCode, String environmentCode,
                                      String payloadJson, String contentHash, LocalDateTime observedAt) { }
