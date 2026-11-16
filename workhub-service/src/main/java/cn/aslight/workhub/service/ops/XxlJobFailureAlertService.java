package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.dao.ops.OpsMonitorMapper;
import cn.aslight.workhub.model.ops.OpsMonitorEntity;
import cn.aslight.workhub.model.ops.XxlJobDashboardResponse;
import cn.aslight.workhub.model.ops.XxlJobFailedJobResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/** REQ-172f8541：失败告警站内信已删除（改由数字员工巡检通知）；本服务仅保留扫描与监测状态落库，供巡检读取。 */
@Service
public class XxlJobFailureAlertService {

    private static final Logger log = LoggerFactory.getLogger(XxlJobFailureAlertService.class);
    private static final String XXL_JOB = "XXL_JOB";

    private final OpsMonitorMapper opsMonitorMapper;
    private final OpsMonitorSchemaInitializer schemaInitializer;
    private final XxlJobMonitorCollector xxlJobMonitorCollector;

    public XxlJobFailureAlertService(OpsMonitorMapper opsMonitorMapper,
                                     OpsMonitorSchemaInitializer schemaInitializer,
                                     XxlJobMonitorCollector xxlJobMonitorCollector) {
        this.opsMonitorMapper = opsMonitorMapper;
        this.schemaInitializer = schemaInitializer;
        this.xxlJobMonitorCollector = xxlJobMonitorCollector;
    }

    public void scanAndAlert() {
        schemaInitializer.ensureInitialized();
        List<OpsMonitorEntity> monitors = opsMonitorMapper.findAll(XXL_JOB, null, null, null, true, null, null);
        int failureCount = 0;
        for (OpsMonitorEntity monitor : monitors) {
            try {
                XxlJobDashboardResponse dashboard = xxlJobMonitorCollector.collect(monitor);
                opsMonitorMapper.updateCheckResult(monitor.getId(), dashboard.status(), dashboard.message());
                failureCount += dashboard.failedJobs().size();
            } catch (Exception ex) {
                String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                opsMonitorMapper.updateCheckResult(monitor.getId(), "ERROR", message);
                log.warn("XXL-JOB failure alert scan failed. monitorId={}, message={}", monitor.getId(), message, ex);
            }
        }
        log.info("XXL-JOB failure alert scan finished. monitorCount={}, failureCandidateCount={}", monitors.size(), failureCount);
    }
}
