package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.model.ops.AccountingMonitorConfigEntity;

import java.util.List;
import java.util.Map;

public interface AccountingDatabaseQueryClient {

    List<Map<String, Object>> runRows(AccountingMonitorConfigEntity config, String sql);
}
