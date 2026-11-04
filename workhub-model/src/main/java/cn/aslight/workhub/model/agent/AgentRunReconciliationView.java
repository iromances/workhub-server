package cn.aslight.workhub.model.agent;

import java.time.LocalDateTime;
import java.util.List;

public record AgentRunReconciliationView(String runId, long version, String snapshotHash,
        LocalDateTime leaseUntil, boolean canReconcile, List<String> blockedReasons,
        List<String> resumeBlockedReasons, boolean cancelRequested, List<Call> calls) {
    public record Call(String kind, String key, String toolCode, Boolean readOnly,
            String host, Long pid, String startInstant, String phase, String processState,
            String receiptSource, String receiptKey, boolean needsConfirmation, String requestPayloadJson) { }
}
