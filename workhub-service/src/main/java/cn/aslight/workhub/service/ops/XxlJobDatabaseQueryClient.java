package cn.aslight.workhub.service.ops;

import java.util.List;
import java.util.Map;

public interface XxlJobDatabaseQueryClient {

    List<Map<String, Object>> runRows(String businessLineCode, String environmentCode, String sql);
}
