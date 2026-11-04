package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.model.ops.AccountingMonitorConfigEntity;
import java.util.List;
import java.util.Map;

public interface AccountingDatabaseQueryClient {
    /** 固定本次执行的实际目标；后续查询不重新解释可能变化的配置。 */
    Session open(AccountingMonitorConfigEntity config);
    interface Session {
        String targetHash();
        List<Map<String, Object>> runRows(String sql);
    }
}
