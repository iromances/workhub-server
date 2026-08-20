package cn.aslight.workhub.model.ops;

import java.time.LocalDateTime;

public class SystemAlertSyncStateEntity {
    private Long subsystemId;
    private LocalDateTime lastOccurredAt;
    private String lastStatus;
    private String lastMessage;
    private LocalDateTime lastSyncedAt;

    public Long getSubsystemId() { return subsystemId; }
    public void setSubsystemId(Long subsystemId) { this.subsystemId = subsystemId; }
    public LocalDateTime getLastOccurredAt() { return lastOccurredAt; }
    public void setLastOccurredAt(LocalDateTime lastOccurredAt) { this.lastOccurredAt = lastOccurredAt; }
    public String getLastStatus() { return lastStatus; }
    public void setLastStatus(String lastStatus) { this.lastStatus = lastStatus; }
    public String getLastMessage() { return lastMessage; }
    public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }
    public LocalDateTime getLastSyncedAt() { return lastSyncedAt; }
    public void setLastSyncedAt(LocalDateTime lastSyncedAt) { this.lastSyncedAt = lastSyncedAt; }
}
