package cn.aslight.workhub.service.ai;

import tools.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.OutputStream;

/** 单个进程的协议会话；stdout 为协议，stderr 不能混入业务结果。 */
public interface CliTaskConversation {
    void start(OutputStream input) throws IOException;
    /** 返回可展示的进度；最终产物由协议实现写入平台指定文件。 */
    String accept(JsonNode event) throws IOException;
    /** 原文先持久化；非空原文只允许支持结果恢复的协议处理，不能退回业务重跑。 */
    default void resultRecovery(String original, java.util.function.Consumer<String> save) {
        if (original != null) throw new IllegalArgumentException("当前 CLI 不支持仅恢复结果整理");
    }
    default boolean unlimitedResultMessages() { return false; }
}
