package cn.aslight.workhub.service.ai;

/** 已选择协议的进程与逐次会话；不包含任何持久化密钥。 */
public record CliTaskLaunch(ProcessBuilder builder, CliTaskConversation conversation) { }
