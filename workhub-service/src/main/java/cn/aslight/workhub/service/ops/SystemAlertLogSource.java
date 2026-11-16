package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.model.ops.ElkSystemAlertLog;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

public interface SystemAlertLogSource {
    /** ES毫秒级date字段对range边界按包含方向取整；该偏差内的边界文档视为区间内，超出仍按越界失败。 */
    long BOUNDARY_TOLERANCE_MS = 1L;

    SyncResult readErrors(String indexPattern, List<String> serviceNames, String environmentCode, Instant fromInclusive,
                          Consumer<ElkSystemAlertLog> consumer);

    default ErrorPage readErrorPage(String indexPattern, List<String> services, String environment, Instant from, Instant to, Position position) {
        throw new IllegalStateException("日志来源尚未提供持久分页协议");
    }
    record Position(String pitId, String searchAfterJson, String targetHash) { }
    record ErrorPage(List<ElkSystemAlertLog> events, Position next, boolean complete, boolean contentTruncated,
                     ElasticsearchSystemAlertClient.QueryReceipt execution) { }
    class RestartRequired extends IllegalStateException {
        public RestartRequired(String message) { super(message); }
    }
    record SyncResult(int fetchedCount, Instant latestOccurredAt, boolean complete) {
        public SyncResult(int fetchedCount, Instant latestOccurredAt) { this(fetchedCount, latestOccurredAt, false); }
    }
}
