package cn.aslight.workhub.integration.wecom;

/**
 * 企微群机器人 markdown 消息发送结果；errcode/errmsg 为企微原始响应，sent 仅表示 errcode=0。
 */
public record WecomRobotSendResult(boolean sent, int errcode, String errmsg) {
}
