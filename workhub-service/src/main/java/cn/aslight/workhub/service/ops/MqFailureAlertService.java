package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.dao.ops.OpsMonitorMapper;
import cn.aslight.workhub.model.ops.MqDashboardResponse;
import cn.aslight.workhub.model.ops.OpsMonitorEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/** REQ-172f8541：失败告警站内信已删除（改由数字员工巡检通知）；本服务仅保留扫描与监测状态落库，供巡检读取。 */
@Service
public class MqFailureAlertService {

    private static final Logger log = LoggerFactory.getLogger(MqFailureAlertService.class);
    private static final String MQ = "MQ";

    private final OpsMonitorMapper opsMonitorMapper;
    private final OpsMonitorSchemaInitializer schemaInitializer;
    private final MqMonitorCollector mqMonitorCollector;

    public MqFailureAlertService(OpsMonitorMapper opsMonitorMapper,
                                 OpsMonitorSchemaInitializer schemaInitializer,
                                 MqMonitorCollector mqMonitorCollector) {
        this.opsMonitorMapper = opsMonitorMapper;
        this.schemaInitializer = schemaInitializer;
        this.mqMonitorCollector = mqMonitorCollector;
    }

    public void scanAndAlert() {
        schemaInitializer.ensureInitialized();
        List<OpsMonitorEntity> monitors = opsMonitorMapper.findAll(MQ, null, null, null, true, null, null);
        int failureCount = 0;
        for (OpsMonitorEntity monitor : monitors) {
            try {
                MqDashboardResponse dashboard = mqMonitorCollector.collect(monitor);
                opsMonitorMapper.updateCheckResult(monitor.getId(), dashboard.status(), dashboard.message());
                failureCount += dashboard.messages().size();
            } catch (Exception ex) {
                String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                opsMonitorMapper.updateCheckResult(monitor.getId(), "ERROR", message);
                log.warn("MQ failure alert scan failed. monitorId={}, message={}", monitor.getId(), message, ex);
            }
        }
        log.info("MQ failure alert scan finished. monitorCount={}, failureCandidateCount={}", monitors.size(), failureCount);
    }
}
