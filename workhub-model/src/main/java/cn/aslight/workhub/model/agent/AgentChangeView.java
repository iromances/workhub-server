package cn.aslight.workhub.model.agent;

import java.time.LocalDateTime;
import java.util.List;

public record AgentChangeView(String id, String runId, String taskId, long revision, String manifestHash,
                               LocalDateTime createdAt, boolean current, boolean readyForReview,
                               List<String> blockers, AgentChangeManifest manifest) { }
