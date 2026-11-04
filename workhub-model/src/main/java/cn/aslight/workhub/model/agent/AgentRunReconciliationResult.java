package cn.aslight.workhub.model.agent;

/** 返回已提交核对的结果版本；重试返回原结果，页面另行刷新运行的当前状态。 */
public record AgentRunReconciliationResult(String runId, String status, long version, String eventKey) { }
