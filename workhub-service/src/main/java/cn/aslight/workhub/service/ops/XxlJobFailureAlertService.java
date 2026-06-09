package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.dao.ops.OpsMonitorMapper;
import cn.aslight.workhub.dao.system.UserMapper;
import cn.aslight.workhub.model.ops.OpsMonitorEntity;
import cn.aslight.workhub.model.ops.XxlJobDashboardResponse;
import cn.aslight.workhub.model.ops.XxlJobFailedJobResponse;
import cn.aslight.workhub.model.system.UserOptionResponse;
import cn.aslight.workhub.service.notification.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class XxlJobFailureAlertService {

    private static final Logger log = LoggerFactory.getLogger(XxlJobFailureAlertService.class);
    private static final String XXL_JOB = "XXL_JOB";

    private final OpsMonitorMapper opsMonitorMapper;
    private final OpsMonitorSchemaInitializer schemaInitializer;
    private final XxlJobMonitorCollector xxlJobMonitorCollector;
    private final UserMapper userMapper;
    private final NotificationService notificationService;

    public XxlJobFailureAlertService(OpsMonitorMapper opsMonitorMapper,
                                     OpsMonitorSchemaInitializer schemaInitializer,
                                     XxlJobMonitorCollector xxlJobMonitorCollector,
                                     UserMapper userMapper,
                                     NotificationService notificationService) {
        this.opsMonitorMapper = opsMonitorMapper;
        this.schemaInitializer = schemaInitializer;
        this.xxlJobMonitorCollector = xxlJobMonitorCollector;
        this.userMapper = userMapper;
        this.notificationService = notificationService;
    }

    public void scanAndAlert() {
        schemaInitializer.ensureInitialized();
        List<OpsMonitorEntity> monitors = opsMonitorMapper.findAll(XXL_JOB, null, null, null, true);
        int alertCount = 0;
        for (OpsMonitorEntity monitor : monitors) {
            try {
                XxlJobDashboardResponse dashboard = xxlJobMonitorCollector.collect(monitor);
                opsMonitorMapper.updateCheckResult(monitor.getId(), dashboard.status(), dashboard.message());
                for (XxlJobFailedJobResponse failedJob : dashboard.failedJobs()) {
                    alertFailedJob(monitor, failedJob);
                    alertCount++;
                }
            } catch (Exception ex) {
                String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                opsMonitorMapper.updateCheckResult(monitor.getId(), "ERROR", message);
                log.warn("XXL-JOB failure alert scan failed. monitorId={}, message={}", monitor.getId(), message, ex);
            }
        }
        log.info("XXL-JOB failure alert scan finished. monitorCount={}, alertCandidateCount={}", monitors.size(), alertCount);
    }

    private void alertFailedJob(OpsMonitorEntity monitor, XxlJobFailedJobResponse failedJob) {
        String title = monitor.getName() + " 失败";
        String content = """
                业务线：%s
                环境：%s
                数据库：%s
                执行器：%s
                Job ID：%s
                任务描述：%s
                JobHandler：%s
                Log ID：%s
                触发时间：%s
                处理时间：%s
                失败摘要：%s
                """.formatted(
                monitor.getBusinessLineCode(),
                monitor.getEnvironmentCode(),
                monitor.getXxlJobDatabaseName(),
                failedJob.executorAppName(),
                failedJob.jobId(),
                failedJob.jobDesc(),
                failedJob.executorHandler(),
                failedJob.logId(),
                failedJob.triggerTime(),
                failedJob.handleTime(),
                failedJob.handleMsg()
        );
        String dedupeKey = "XXL_JOB:" + monitor.getId() + ":" + failedJob.jobId() + ":" + failedJob.logId();
        for (String recipient : recipients(monitor.getBusinessLineCode())) {
            notificationService.createSystemNotification(
                    recipient,
                    "XXL_JOB_FAILED",
                    title,
                    content,
                    monitor.getBusinessLineCode(),
                    monitor.getEnvironmentCode(),
                    dedupeKey
            );
        }
    }

    private List<String> recipients(String businessLineCode) {
        List<String> recipients = userMapper.findBusinessLineMembers(businessLineCode).stream()
                .map(UserOptionResponse::userName)
                .filter(userName -> userName != null && !userName.isBlank())
                .distinct()
                .toList();
        return recipients.isEmpty() ? List.of("admin") : recipients;
    }
}
