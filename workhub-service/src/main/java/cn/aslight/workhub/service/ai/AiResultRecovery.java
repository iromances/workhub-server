package cn.aslight.workhub.service.ai;

import java.util.function.BiConsumer;
import java.util.function.Function;

/** 服务端限定到同一需求的原文读取与保存；不携带到供应商请求。 */
public record AiResultRecovery(Function<String, String> load, BiConsumer<String, String> save, Runnable complete) {
    public AiResultRecovery(Function<String, String> load, BiConsumer<String, String> save) {
        this(load, save, () -> { });
    }
    public record Session(String original, java.util.function.Consumer<String> save) { }
    public Session open(String fingerprint) {
        return new Session(load.apply(fingerprint), text -> save.accept(fingerprint,
                new cn.aslight.workhub.mcp.security.ServerFileContentMasker().mask(text)));
    }
}
