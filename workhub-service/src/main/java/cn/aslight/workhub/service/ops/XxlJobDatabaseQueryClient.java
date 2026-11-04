package cn.aslight.workhub.service.ops;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public interface XxlJobDatabaseQueryClient {

    List<Map<String, Object>> runRows(String businessLineCode, String environmentCode, String sql);

    /** 只有能固定实际目标和校验完整回执的实现才可提供验证凭据。 */
    default VerifiedBatch runVerifiedBatch(String businessLineCode, String environmentCode, List<String> sqls) {
        throw new IllegalStateException("当前数据库执行端未提供固定目标和完整查询凭据");
    }

    record VerifiedBatch(String targetHash, java.time.Instant startedAt, java.time.Instant finishedAt,
                         List<List<Map<String, Object>>> rows) { }

    /** 逐条执行 SQL 并按顺序返回行集；实现方可复用同一连接执行整批。 */
    default List<List<Map<String, Object>>> runRowsBatch(String businessLineCode, String environmentCode, List<String> sqls) {
        List<List<Map<String, Object>>> results = new ArrayList<>();
        for (String sql : sqls) {
            results.add(runRows(businessLineCode, environmentCode, sql));
        }
        return results;
    }
}
