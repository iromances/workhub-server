package cn.aslight.workhub.service.ai;

import cn.aslight.workhub.mcp.security.ServerFileContentMasker;
import java.io.PrintWriter;
import java.io.StringWriter;

/** 评估失败详情：保留 cause/suppressed/行号，脱敏后再限制存储大小。 */
public final class AiFailureDiagnostics {
    private static final int MAX_CHARACTERS = 12000;

    private AiFailureDiagnostics() {}

    public static String stackTrace(Throwable failure) {
        var writer = new StringWriter();
        failure.printStackTrace(new PrintWriter(writer));
        String masked = new ServerFileContentMasker().mask(writer.toString());
        if (masked.length() <= MAX_CHARACTERS) return masked;
        String suffix = "\n[堆栈过长，已截断]";
        return masked.substring(0, MAX_CHARACTERS - suffix.length()) + suffix;
    }
}
