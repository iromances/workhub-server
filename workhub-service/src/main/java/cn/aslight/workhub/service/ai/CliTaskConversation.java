package cn.aslight.workhub.service.ai;

import tools.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.OutputStream;

/** 单个进程的协议会话；stdout 为协议，stderr 不能混入业务结果。 */
public interface CliTaskConversation {
    void start(OutputStream input) throws IOException;
    /** 返回可展示的进度；最终产物由协议实现写入平台指定文件。 */
    String accept(JsonNode event) throws IOException;
}
