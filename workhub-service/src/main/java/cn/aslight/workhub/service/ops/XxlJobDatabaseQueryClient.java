package cn.aslight.workhub.service.ops;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public interface XxlJobDatabaseQueryClient {

    List<Map<String, Object>> runRows(String businessLineCode, String environmentCode, String sql);

    /** 逐条执行 SQL 并按顺序返回行集；实现方可复用同一连接执行整批。 */
    default List<List<Map<String, Object>>> runRowsBatch(String businessLineCode, String environmentCode, List<String> sqls) {
        List<List<Map<String, Object>>> results = new ArrayList<>();
        for (String sql : sqls) {
            results.add(runRows(businessLineCode, environmentCode, sql));
        }
        return results;
    }
}
