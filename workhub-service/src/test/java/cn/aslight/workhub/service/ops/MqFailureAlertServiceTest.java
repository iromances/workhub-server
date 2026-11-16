package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.dao.ops.OpsMonitorMapper;
import cn.aslight.workhub.model.ops.MqDashboardResponse;
import cn.aslight.workhub.model.ops.MqMessageResponse;
import cn.aslight.workhub.model.ops.OpsMonitorEntity;
import cn.aslight.workhub.model.system.UserOptionResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MqFailureAlertServiceTest {

    @Test
    void scanAndAlert_shouldNotifyBusinessLineMembersWithCompositeDedupeKey() {
        OpsMonitorMapper monitorMapper = mock(OpsMonitorMapper.class);
        MqMonitorCollector collector = mock(MqMonitorCollector.class);
        OpsMonitorSchemaInitializer schemaInitializer = mock(OpsMonitorSchemaInitializer.class);
        MqFailureAlertService service = new MqFailureAlertService(monitorMapper, schemaInitializer, collector);
        OpsMonitorEntity monitor = monitor();
        when(monitorMapper.findAll("MQ", null, null, null, true, null, null)).thenReturn(List.of(monitor));
        when(collector.collect(monitor)).thenReturn(dashboard());

        service.scanAndAlert();

        verify(monitorMapper).updateCheckResult(eq(7L), eq("ERROR"), contains("发送失败"));
    }

    @Test
    void scanAndAlert_shouldNotifyAdminWhenBusinessLineHasNoMembers() {
        OpsMonitorMapper monitorMapper = mock(OpsMonitorMapper.class);
        MqMonitorCollector collector = mock(MqMonitorCollector.class);
        OpsMonitorSchemaInitializer schemaInitializer = mock(OpsMonitorSchemaInitializer.class);
        MqFailureAlertService service = new MqFailureAlertService(monitorMapper, schemaInitializer, collector);
        OpsMonitorEntity monitor = monitor();
        when(monitorMapper.findAll("MQ", null, null, null, true, null, null)).thenReturn(List.of(monitor));
        when(collector.collect(monitor)).thenReturn(dashboard());

        service.scanAndAlert();

    }

    @Test
    void scanAndAlert_shouldRecordErrorWhenCollectionFails() {
        OpsMonitorMapper monitorMapper = mock(OpsMonitorMapper.class);
        MqMonitorCollector collector = mock(MqMonitorCollector.class);
        OpsMonitorSchemaInitializer schemaInitializer = mock(OpsMonitorSchemaInitializer.class);
        MqFailureAlertService service = new MqFailureAlertService(monitorMapper, schemaInitializer, collector);
        OpsMonitorEntity monitor = monitor();
        when(monitorMapper.findAll("MQ", null, null, null, true, null, null)).thenReturn(List.of(monitor));
        when(collector.collect(monitor)).thenThrow(new IllegalArgumentException("未发现包含 message_sender_log 的子应用库"));

        service.scanAndAlert();

        verify(monitorMapper).updateCheckResult(eq(7L), eq("ERROR"), eq("未发现包含 message_sender_log 的子应用库"));
    }

    private OpsMonitorEntity monitor() {
        OpsMonitorEntity entity = new OpsMonitorEntity();
        entity.setId(7L);
        entity.setMonitorType("MQ");
        entity.setMonitorKey("pay-prod-mq");
        entity.setBusinessLineCode("支付");
        entity.setEnvironmentCode("prod");
        entity.setName("支付生产 MQ 消息");
        entity.setEnabled(true);
        return entity;
    }

    private MqDashboardResponse dashboard() {
        return new MqDashboardResponse(
                7L,
                "pay-prod-mq",
                "支付",
                "prod",
                "支付生产 MQ 消息",
                "ERROR",
                "MQ 窗口发送失败 1 条、接收失败 0 条",
                LocalDateTime.now(),
                1,
                10,
                9,
                1,
                0,
                0,
                0,
                0,
                List.of(),
                1,
                1,
                200,
                List.of(new MqMessageResponse(
                        "assets_saps",
                        "SENDER",
                        9921L,
                        "msg-order-timeout",
                        "pay.order",
                        "order.created",
                        "",
                        3,
                        "SEND_FAILED",
                        "发送失败",
                        3,
                        null,
                        null,
                        "2026-06-02 09:00:00",
                        "2026-06-02 09:00:00",
                        "connection refused"
                )),
                null,
                null,
                30,
                null
        );
    }
}
