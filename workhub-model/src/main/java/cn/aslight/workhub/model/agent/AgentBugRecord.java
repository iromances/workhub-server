package cn.aslight.workhub.model.agent;

import java.time.LocalDateTime;

/** 缺陷工作项的实际业务线线索；源工作项没有环境字段，不能添加推测的环境。 */
public record AgentBugRecord(Long id, String no, Long projectId, String projectName, String businessLineCode,
                             String title, String status, String priority, String sourceType, String sourceChannel,
                             String description, String acceptanceCriteria, LocalDateTime createdAt, LocalDateTime updatedAt) { }
