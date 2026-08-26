package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.model.ops.ElkSystemAlertLog;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

public interface SystemAlertLogSource {
    SyncResult readErrors(String indexPattern, List<String> serviceNames, String environmentCode, Instant fromInclusive,
                          Consumer<ElkSystemAlertLog> consumer);

    record SyncResult(int fetchedCount, Instant latestOccurredAt) {
    }
}
