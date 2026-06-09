package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.dao.ops.OpsMonitorMapper;
import cn.aslight.workhub.dao.system.UserMapper;
import cn.aslight.workhub.model.ops.OpsMonitorEntity;
import cn.aslight.workhub.model.ops.XxlJobDashboardResponse;
import cn.aslight.workhub.model.ops.XxlJobFailedJobResponse;
import cn.aslight.workhub.model.system.UserOptionResponse;
import cn.aslight.workhub.service.notification.NotificationService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class XxlJobFailureAlertServiceTest {

    @Test
    void scanAndAlert_shouldNotifyBusinessLineMembersWithLogDedupeKey() {
        OpsMonitorMapper monitorMapper = mock(OpsMonitorMapper.class);
        XxlJobMonitorCollector collector = mock(XxlJobMonitorCollector.class);
        OpsMonitorSchemaInitializer schemaInitializer = mock(OpsMonitorSchemaInitializer.class);
        UserMapper userMapper = mock(UserMapper.class);
        NotificationService notificationService = mock(NotificationService.class);
        XxlJobFailureAlertService service = new XxlJobFailureAlertService(monitorMapper, schemaInitializer, collector, userMapper, notificationService);
        OpsMonitorEntity monitor = monitor();
        when(monitorMapper.findAll("XXL_JOB", null, null, null, true)).thenReturn(List.of(monitor));
        when(collector.collect(monitor)).thenReturn(dashboard());
        when(userMapper.findBusinessLineMembers("支付")).thenReturn(List.of(new UserOptionResponse("alice", "Alice", "支付")));

        service.scanAndAlert();

        verify(notificationService).createSystemNotification(
                eq("alice"),
                eq("XXL_JOB_FAILED"),
                contains("支付生产 XXL-JOB 失败"),
                contains("settleJobHandler"),
                eq("支付"),
                eq("prod"),
                eq("XXL_JOB:7:31:9921")
        );
    }

    @Test
    void scanAndAlert_shouldNotifyAdminWhenBusinessLineHasNoMembers() {
        OpsMonitorMapper monitorMapper = mock(OpsMonitorMapper.class);
        XxlJobMonitorCollector collector = mock(XxlJobMonitorCollector.class);
        OpsMonitorSchemaInitializer schemaInitializer = mock(OpsMonitorSchemaInitializer.class);
        UserMapper userMapper = mock(UserMapper.class);
        NotificationService notificationService = mock(NotificationService.class);
        XxlJobFailureAlertService service = new XxlJobFailureAlertService(monitorMapper, schemaInitializer, collector, userMapper, notificationService);
        OpsMonitorEntity monitor = monitor();
        when(monitorMapper.findAll("XXL_JOB", null, null, null, true)).thenReturn(List.of(monitor));
        when(collector.collect(monitor)).thenReturn(dashboard());
        when(userMapper.findBusinessLineMembers("支付")).thenReturn(List.of());

        service.scanAndAlert();

        verify(notificationService).createSystemNotification(
                eq("admin"),
                eq("XXL_JOB_FAILED"),
                contains("支付生产 XXL-JOB 失败"),
                contains("settleJobHandler"),
                eq("支付"),
                eq("prod"),
                eq("XXL_JOB:7:31:9921")
        );
    }

    private OpsMonitorEntity monitor() {
        OpsMonitorEntity entity = new OpsMonitorEntity();
        entity.setId(7L);
        entity.setMonitorType("XXL_JOB");
        entity.setMonitorKey("pay-prod-xxljob");
        entity.setBusinessLineCode("支付");
        entity.setEnvironmentCode("prod");
        entity.setName("支付生产 XXL-JOB");
        entity.setXxlJobDatabaseName("amp_xxl_job");
        entity.setEnabled(true);
        return entity;
    }

    private XxlJobDashboardResponse dashboard() {
        return new XxlJobDashboardResponse(
                7L,
                "pay-prod-xxljob",
                "支付",
                "prod",
                "支付生产 XXL-JOB",
                "amp_xxl_job",
                "ERROR",
                "XXL-JOB 最近失败任务数：1",
                LocalDateTime.now(),
                1,
                3,
                3,
                0,
                4,
                0,
                3,
                1,
                "2026-06-02 13:40:00",
                1,
                List.of(new XxlJobFailedJobResponse(
                        "pay-job-executor",
                        31L,
                        "结算任务",
                        "张三",
                        "settleJobHandler",
                        1,
                        "EXECUTION_FAILED",
                        "执行失败",
                        9921L,
                        "2026-06-02 09:00:00",
                        200,
                        "调度成功",
                        "2026-06-02 09:01:00",
                        500,
                        "timeout"
                ))
        );
    }
}
