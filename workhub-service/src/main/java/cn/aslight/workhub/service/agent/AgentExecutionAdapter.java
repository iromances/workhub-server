package cn.aslight.workhub.service.agent;

/** 外部 AI 的执行适配。API 与原生 CLI 各自保留适合的执行方式。 */
public interface AgentExecutionAdapter {
    String channelType();
    void execute(String runId, String owner);
}
