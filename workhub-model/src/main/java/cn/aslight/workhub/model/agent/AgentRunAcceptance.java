package cn.aslight.workhub.model.agent;

import java.time.LocalDateTime;
import java.util.List;

/** 当前证据复查；不修改历史运行结论，也不代替发布审核或问题关闭。 */
public record AgentRunAcceptance(String runId, LocalDateTime checkedAt, boolean satisfied, List<Item> items) {
    public record Item(String kind, String status, String message, List<Evidence> evidence, List<Target> targets) {
        public Item(String kind, String status, String message, List<Evidence> evidence) {
            this(kind, status, message, evidence, List.of());
        }
    }
    public record Target(AgentScope scope, String status, String message, List<Evidence> evidence) { }
    public record Evidence(String kind, String id, String contentHash) { }
}
