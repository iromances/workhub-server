package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.dao.ops.OpsMonitorMapper;
import cn.aslight.workhub.dao.project.BusinessLineMapper;
import cn.aslight.workhub.model.ops.MqDashboardResponse;
import cn.aslight.workhub.model.ops.OpsMonitorCheckResponse;
import cn.aslight.workhub.model.ops.OpsMonitorEntity;
import cn.aslight.workhub.model.ops.OpsMonitorResponse;
import cn.aslight.workhub.model.ops.OpsMonitorSaveRequest;
import cn.aslight.workhub.model.ops.XxlJobDashboardResponse;
import cn.aslight.workhub.model.ops.XxlJobExecutorResponse;
import cn.aslight.workhub.model.ops.XxlJobLogPageResponse;
import cn.aslight.workhub.model.project.BusinessLineEntity;
import cn.aslight.workhub.service.mcp.McpCryptoService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class OpsMonitorServiceTest {

    @Test
    void agentQueryMustMatchAuthorizedScopeBeforeAnyDatabaseCollection() {
        var mapper = mock(OpsMonitorMapper.class); var collector = mock(XxlJobMonitorCollector.class);
        var service = new OpsMonitorService(mapper,mock(BusinessLineMapper.class),mock(OpsMonitorSchemaInitializer.class),mock(McpCryptoService.class),collector,mock(MqMonitorCollector.class));
        var moved = monitor(9L,"fixture"); moved.setBusinessLineCode("BL000012"); moved.setEnvironmentCode("PROD");
        when(mapper.findById(9L)).thenReturn(moved);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () ->
                service.xxlJobLogsInScope(9L,"BL000012","TEST",LocalDate.of(2026,11,3),LocalDate.of(2026,11,4),1,20,null,null,"FAILED"));
        verifyNoInteractions(collector);
    }

    @Test
    void agentQueryUsesTheSameValidatedMonitorSnapshotThroughoutCollection() {
        var mapper = mock(OpsMonitorMapper.class); var collector = mock(XxlJobMonitorCollector.class);
        var service = new OpsMonitorService(mapper,mock(BusinessLineMapper.class),mock(OpsMonitorSchemaInitializer.class),mock(McpCryptoService.class),collector,mock(MqMonitorCollector.class));
        var valid = monitor(9L,"fixture"); valid.setBusinessLineCode("BL000012"); valid.setEnvironmentCode("TEST");
        when(mapper.findById(9L)).thenReturn(valid);
        var from=LocalDate.of(2026,11,3); var to=LocalDate.of(2026,11,4);
        var response=new XxlJobLogPageResponse(9L,"key","BL000012","TEST","监测","fixture","UP","",LocalDateTime.now(),0,1,20,List.of());
        when(collector.collectLogs(valid,from,to,1,20,null,null,"FAILED")).thenReturn(response);
        assertEquals(response,service.xxlJobLogsInScope(9L,"BL000012","TEST",from,to,1,20,null,null,"FAILED"));
        verify(mapper,times(1)).findById(9L); verify(collector).collectLogs(valid,from,to,1,20,null,null,"FAILED");
    }

    @Test
    void list_shouldReturnBusinessLineNameAndLocalizedLastStatus() {
        OpsMonitorMapper mapper = mock(OpsMonitorMapper.class);
        BusinessLineMapper businessLineMapper = mock(BusinessLineMapper.class);
        OpsMonitorSchemaInitializer schemaInitializer = mock(OpsMonitorSchemaInitializer.class);
        McpCryptoService cryptoService = mock(McpCryptoService.class);
        XxlJobMonitorCollector collector = mock(XxlJobMonitorCollector.class);
        OpsMonitorService service = new OpsMonitorService(mapper, businessLineMapper, schemaInitializer, cryptoService, collector, mock(MqMonitorCollector.class));
        OpsMonitorEntity monitor = monitor(9L, "amp_xxl_job");
        monitor.setBusinessLineCode("BL000001");
        monitor.setLastStatus("ERROR");
        BusinessLineEntity businessLine = new BusinessLineEntity();
        businessLine.setBusinessLineCode("BL000001");
        businessLine.setBusinessLineName("保费分期");
        when(mapper.findAll("XXL_JOB", null, null, null, false, null, null)).thenReturn(List.of(monitor));
        when(businessLineMapper.findByCode("BL000001")).thenReturn(businessLine);

        OpsMonitorResponse response = service.list("XXL_JOB", null, null, null, false).getFirst();

        assertEquals("BL000001", response.businessLineCode());
        assertEquals("保费分期", response.businessLineName());
        assertEquals("ERROR", response.lastStatus());
        assertEquals("异常", response.lastStatusName());
    }

    @Test
    void create_shouldUpdateExistingDefaultXxlJobMonitorWhenKeyAlreadyExists() {
        OpsMonitorMapper mapper = mock(OpsMonitorMapper.class);
        OpsMonitorSchemaInitializer schemaInitializer = mock(OpsMonitorSchemaInitializer.class);
        McpCryptoService cryptoService = mock(McpCryptoService.class);
        XxlJobMonitorCollector collector = mock(XxlJobMonitorCollector.class);
        OpsMonitorService service = new OpsMonitorService(mapper, mock(BusinessLineMapper.class), schemaInitializer, cryptoService, collector, mock(MqMonitorCollector.class));
        OpsMonitorEntity existing = monitor(9L, "old_xxl_job");
        OpsMonitorEntity updated = monitor(9L, "amp_xxl_job");
        when(mapper.findByMonitorKey("支付:PROD:XXL_JOB")).thenReturn(existing, updated);
        when(mapper.findById(9L)).thenReturn(updated);
        when(mapper.update(any())).thenReturn(1);

        OpsMonitorResponse response = service.create(request("amp_xxl_job"));

        verify(mapper).update(any());
        assertEquals(9L, response.id());
        assertEquals("amp_xxl_job", response.xxlJobDatabaseName());
    }

    @Test
    void create_shouldNormalizeFocusedExecutorAppNames() {
        OpsMonitorMapper mapper = mock(OpsMonitorMapper.class);
        OpsMonitorSchemaInitializer schemaInitializer = mock(OpsMonitorSchemaInitializer.class);
        McpCryptoService cryptoService = mock(McpCryptoService.class);
        XxlJobMonitorCollector collector = mock(XxlJobMonitorCollector.class);
        OpsMonitorService service = new OpsMonitorService(mapper, mock(BusinessLineMapper.class), schemaInitializer, cryptoService, collector, mock(MqMonitorCollector.class));
        OpsMonitorEntity saved = monitor(10L, "amp_xxl_job");
        saved.setExecutorAppName("premium-job,premium-settle");
        when(mapper.findByMonitorKey("支付:PROD:XXL_JOB")).thenReturn(null, saved);
        OpsMonitorSaveRequest request = request("amp_xxl_job");
        request.setExecutorAppName(" premium-job, premium-settle,,premium-job ");

        OpsMonitorResponse response = service.create(request);

        ArgumentCaptor<OpsMonitorEntity> captor = ArgumentCaptor.forClass(OpsMonitorEntity.class);
        verify(mapper).insert(captor.capture());
        assertEquals("premium-job,premium-settle", captor.getValue().getExecutorAppName());
        assertEquals("premium-job,premium-settle", response.executorAppName());
    }

    @Test
    void xxlJobDetail_shouldPassPaginationToCollector() {
        OpsMonitorMapper mapper = mock(OpsMonitorMapper.class);
        OpsMonitorSchemaInitializer schemaInitializer = mock(OpsMonitorSchemaInitializer.class);
        McpCryptoService cryptoService = mock(McpCryptoService.class);
        XxlJobMonitorCollector collector = mock(XxlJobMonitorCollector.class);
        OpsMonitorService service = new OpsMonitorService(mapper, mock(BusinessLineMapper.class), schemaInitializer, cryptoService, collector, mock(MqMonitorCollector.class));
        OpsMonitorEntity monitor = monitor(9L, "amp_xxl_job");
        LocalDate startDate = LocalDate.of(2026, 5, 1);
        LocalDate endDate = LocalDate.of(2026, 6, 2);
        when(mapper.findById(9L)).thenReturn(monitor);
        when(collector.collect(eq(monitor), eq(startDate), eq(endDate), eq(3), eq(50), eq("张"), eq("premium"), eq("FAILED"))).thenReturn(dashboard());

        XxlJobDashboardResponse response = service.xxlJobDetail(9L, startDate, endDate, 3, 50, "张", "premium", "FAILED");

        assertEquals(3, response.failedJobPage());
        assertEquals(50, response.failedJobPageSize());
        verify(collector).collect(monitor, startDate, endDate, 3, 50, "张", "premium", "FAILED");
    }

    @Test
    void xxlJobLogs_shouldPassPaginationAndFiltersToLogOnlyCollector() {
        OpsMonitorMapper mapper = mock(OpsMonitorMapper.class);
        OpsMonitorSchemaInitializer schemaInitializer = mock(OpsMonitorSchemaInitializer.class);
        McpCryptoService cryptoService = mock(McpCryptoService.class);
        XxlJobMonitorCollector collector = mock(XxlJobMonitorCollector.class);
        OpsMonitorService service = new OpsMonitorService(mapper, mock(BusinessLineMapper.class), schemaInitializer, cryptoService, collector, mock(MqMonitorCollector.class));
        OpsMonitorEntity monitor = monitor(9L, "amp_xxl_job");
        LocalDate startDate = LocalDate.of(2026, 6, 1);
        LocalDate endDate = LocalDate.of(2026, 6, 9);
        XxlJobLogPageResponse logPage = new XxlJobLogPageResponse(
                9L,
                "支付:PROD:XXL_JOB",
                "支付",
                "PROD",
                "支付 prod XXL-JOB",
                "amp_xxl_job",
                12,
                2,
                10,
                List.of()
        );
        when(mapper.findById(9L)).thenReturn(monitor);
        when(collector.collectLogs(eq(monitor), eq(startDate), eq(endDate), eq(2), eq(10), eq("张"), eq("premium"), eq("ALL"))).thenReturn(logPage);

        XxlJobLogPageResponse response = service.xxlJobLogs(9L, startDate, endDate, 2, 10, "张", "premium", "ALL");

        assertEquals(12, response.total());
        assertEquals(2, response.page());
        assertEquals(10, response.pageSize());
        verify(collector).collectLogs(monitor, startDate, endDate, 2, 10, "张", "premium", "ALL");
        verify(collector, never()).collectSummary(monitor);
    }

    @Test
    void dashboard_shouldReturnLocalMonitorConfigWithoutQueryingXxlJobDatabase() {
        OpsMonitorMapper mapper = mock(OpsMonitorMapper.class);
        OpsMonitorSchemaInitializer schemaInitializer = mock(OpsMonitorSchemaInitializer.class);
        McpCryptoService cryptoService = mock(McpCryptoService.class);
        XxlJobMonitorCollector collector = mock(XxlJobMonitorCollector.class);
        OpsMonitorService service = new OpsMonitorService(mapper, mock(BusinessLineMapper.class), schemaInitializer, cryptoService, collector, mock(MqMonitorCollector.class));
        OpsMonitorEntity monitor = monitor(9L, "amp_xxl_job");
        monitor.setExecutorAppName("premium-job,premium-settle");
        monitor.setLastStatus("ERROR");
        monitor.setLastMessage("上次采集失败");
        monitor.setLastCheckedAt(LocalDateTime.of(2026, 6, 9, 10, 30));
        when(mapper.findAll("XXL_JOB", "支付", "PROD", null, true, null, null)).thenReturn(List.of(monitor));

        List<XxlJobDashboardResponse> dashboards = service.dashboard("支付", "PROD", true);

        assertEquals(1, dashboards.size());
        XxlJobDashboardResponse dashboard = dashboards.getFirst();
        assertEquals(9L, dashboard.id());
        assertEquals("支付:PROD:XXL_JOB", dashboard.monitorKey());
        assertEquals("支付", dashboard.businessLineCode());
        assertEquals("PROD", dashboard.environmentCode());
        assertEquals("支付 PROD XXL-JOB", dashboard.name());
        assertEquals("amp_xxl_job", dashboard.xxlJobDatabaseName());
        assertEquals("ERROR", dashboard.status());
        assertEquals("上次采集失败", dashboard.message());
        assertEquals(LocalDateTime.of(2026, 6, 9, 10, 30), dashboard.checkedAt());
        assertEquals(0, dashboard.jobCount());
        assertEquals(0, dashboard.triggerCount());
        assertEquals(0, dashboard.failedJobCount());
        assertEquals(0, dashboard.failedJobs().size());
        verify(collector, never()).collectSummary(monitor);
    }

    @Test
    void xxlJobExecutors_shouldDelegateToCollectorWithBusinessLineEnvironmentAndDatabase() {
        OpsMonitorMapper mapper = mock(OpsMonitorMapper.class);
        OpsMonitorSchemaInitializer schemaInitializer = mock(OpsMonitorSchemaInitializer.class);
        McpCryptoService cryptoService = mock(McpCryptoService.class);
        XxlJobMonitorCollector collector = mock(XxlJobMonitorCollector.class);
        OpsMonitorService service = new OpsMonitorService(mapper, mock(BusinessLineMapper.class), schemaInitializer, cryptoService, collector, mock(MqMonitorCollector.class));
        when(collector.listExecutors("保费分期", "prod", "amp_xxl_job"))
                .thenReturn(List.of(new XxlJobExecutorResponse("premium-job", "保费分期任务")));

        List<XxlJobExecutorResponse> executors = service.xxlJobExecutors("保费分期", "prod", "amp_xxl_job");

        assertEquals(1, executors.size());
        assertEquals("premium-job", executors.getFirst().appName());
        verify(collector).listExecutors("保费分期", "prod", "amp_xxl_job");
    }

    @Test
    void create_shouldNormalizeMqFiltersAndDefaultMonitorKey() {
        OpsMonitorMapper mapper = mock(OpsMonitorMapper.class);
        OpsMonitorSchemaInitializer schemaInitializer = mock(OpsMonitorSchemaInitializer.class);
        McpCryptoService cryptoService = mock(McpCryptoService.class);
        XxlJobMonitorCollector collector = mock(XxlJobMonitorCollector.class);
        MqMonitorCollector mqCollector = mock(MqMonitorCollector.class);
        OpsMonitorService service = new OpsMonitorService(mapper, mock(BusinessLineMapper.class), schemaInitializer, cryptoService, collector, mqCollector);
        OpsMonitorEntity saved = mqMonitor(10L);
        saved.setMqTopic("pay.order,refund.order");
        saved.setMqConsumerGroup("lease.saps");
        when(mapper.findByMonitorKey("支付:PROD:MQ")).thenReturn(null, saved);
        OpsMonitorSaveRequest request = new OpsMonitorSaveRequest();
        request.setMonitorType("MQ");
        request.setBusinessLineCode("支付");
        request.setEnvironmentCode("prod");
        request.setMqTopic(" pay.order, refund.order ,,pay.order ");
        request.setMqConsumerGroup("lease.saps");
        request.setEnabled(true);

        OpsMonitorResponse response = service.create(request);

        ArgumentCaptor<OpsMonitorEntity> captor = ArgumentCaptor.forClass(OpsMonitorEntity.class);
        verify(mapper).insert(captor.capture());
        OpsMonitorEntity entity = captor.getValue();
        assertEquals("支付:PROD:MQ", entity.getMonitorKey());
        assertEquals("pay.order,refund.order", entity.getMqTopic());
        assertEquals("lease.saps", entity.getMqConsumerGroup());
        assertNull(entity.getXxlJobDatabaseName());
        assertNull(entity.getMqLagThreshold());
        assertEquals("pay.order,refund.order", response.mqTopic());
    }

    @Test
    void create_shouldRejectInvalidMqConfiguration() {
        OpsMonitorMapper mapper = mock(OpsMonitorMapper.class);
        OpsMonitorSchemaInitializer schemaInitializer = mock(OpsMonitorSchemaInitializer.class);
        McpCryptoService cryptoService = mock(McpCryptoService.class);
        XxlJobMonitorCollector collector = mock(XxlJobMonitorCollector.class);
        MqMonitorCollector mqCollector = mock(MqMonitorCollector.class);
        OpsMonitorService service = new OpsMonitorService(mapper, mock(BusinessLineMapper.class), schemaInitializer, cryptoService, collector, mqCollector);
        OpsMonitorSaveRequest request = new OpsMonitorSaveRequest();
        request.setMonitorType("MQ");
        request.setBusinessLineCode("支付");
        request.setEnvironmentCode("PROD");

        request.setMqLagThreshold(0);
        assertThrows(IllegalArgumentException.class, () -> service.create(request));
        request.setMqLagThreshold(30);

        request.setMqTopic("bad exchange");
        assertThrows(IllegalArgumentException.class, () -> service.create(request));
        request.setMqTopic("pay.order");
        request.setMqConsumerGroup("bad queue");
        assertThrows(IllegalArgumentException.class, () -> service.create(request));
        verify(mapper, org.mockito.Mockito.never()).insert(any());
    }

    @Test
    void check_shouldDispatchMqMonitorToMqCollector() {
        OpsMonitorMapper mapper = mock(OpsMonitorMapper.class);
        OpsMonitorSchemaInitializer schemaInitializer = mock(OpsMonitorSchemaInitializer.class);
        McpCryptoService cryptoService = mock(McpCryptoService.class);
        XxlJobMonitorCollector collector = mock(XxlJobMonitorCollector.class);
        MqMonitorCollector mqCollector = mock(MqMonitorCollector.class);
        OpsMonitorService service = new OpsMonitorService(mapper, mock(BusinessLineMapper.class), schemaInitializer, cryptoService, collector, mqCollector);
        OpsMonitorEntity monitor = mqMonitor(9L);
        when(mapper.findById(9L)).thenReturn(monitor);
        OpsMonitorCheckResponse checkResponse = new OpsMonitorCheckResponse(9L, "MQ", "支付:PROD:MQ", "UP",
                "MQ 窗口无失败或积压消息", LocalDateTime.now(), java.util.Map.of());
        when(mqCollector.check(monitor)).thenReturn(checkResponse);

        OpsMonitorCheckResponse response = service.check(9L);

        assertEquals("UP", response.status());
        verify(mqCollector).check(monitor);
        verifyNoInteractions(collector);
        verify(mapper).updateCheckResult(9L, "UP", "MQ 窗口无失败或积压消息");
    }

    @Test
    void mqDetail_shouldRejectNonMqMonitorAndDelegateToCollector() {
        OpsMonitorMapper mapper = mock(OpsMonitorMapper.class);
        OpsMonitorSchemaInitializer schemaInitializer = mock(OpsMonitorSchemaInitializer.class);
        McpCryptoService cryptoService = mock(McpCryptoService.class);
        XxlJobMonitorCollector collector = mock(XxlJobMonitorCollector.class);
        MqMonitorCollector mqCollector = mock(MqMonitorCollector.class);
        OpsMonitorService service = new OpsMonitorService(mapper, mock(BusinessLineMapper.class), schemaInitializer, cryptoService, collector, mqCollector);
        when(mapper.findById(9L)).thenReturn(monitor(9L, "amp_xxl_job"));
        assertThrows(IllegalArgumentException.class, () ->
                service.mqDetail(9L, null, null, 1, 20, "ALL", null, null));

        OpsMonitorEntity mqMonitor = mqMonitor(9L);
        when(mapper.findById(9L)).thenReturn(mqMonitor);
        MqDashboardResponse dashboard = new MqDashboardResponse(9L, "支付:PROD:MQ", "支付", "PROD", "支付 PROD MQ 消息",
                "UP", "MQ 窗口无失败或积压消息", LocalDateTime.now(), 0, 0, 0, 0, 0, 0, 0, 0, List.of(), 0, 1, 20, List.of(),
                null, null, null, null);
        when(mqCollector.collect(mqMonitor, LocalDate.now(), LocalDate.now(), 1, 20, "ALL", null, null)).thenReturn(dashboard);
        assertEquals(dashboard, service.mqDetail(9L, LocalDate.now(), LocalDate.now(), 1, 20, "ALL", null, null));
    }

    private OpsMonitorSaveRequest request(String databaseName) {
        OpsMonitorSaveRequest request = new OpsMonitorSaveRequest();
        request.setMonitorType("XXL_JOB");
        request.setBusinessLineCode("支付");
        request.setEnvironmentCode("PROD");
        request.setXxlJobDatabaseName(databaseName);
        request.setEnabled(true);
        return request;
    }

    private OpsMonitorEntity monitor(Long id, String databaseName) {
        OpsMonitorEntity entity = new OpsMonitorEntity();
        entity.setId(id);
        entity.setMonitorType("XXL_JOB");
        entity.setMonitorKey("支付:PROD:XXL_JOB");
        entity.setBusinessLineCode("支付");
        entity.setEnvironmentCode("PROD");
        entity.setName("支付 PROD XXL-JOB");
        entity.setXxlJobDatabaseName(databaseName);
        entity.setEnabled(true);
        return entity;
    }

    private OpsMonitorEntity mqMonitor(Long id) {
        OpsMonitorEntity entity = new OpsMonitorEntity();
        entity.setId(id);
        entity.setMonitorType("MQ");
        entity.setMonitorKey("支付:PROD:MQ");
        entity.setBusinessLineCode("支付");
        entity.setEnvironmentCode("PROD");
        entity.setName("支付 PROD MQ 消息");
        entity.setEnabled(true);
        return entity;
    }

    private XxlJobDashboardResponse dashboard() {
        return new XxlJobDashboardResponse(
                9L,
                "支付:PROD:XXL_JOB",
                "支付",
                "PROD",
                "支付 prod XXL-JOB",
                "amp_xxl_job",
                "ERROR",
                "XXL-JOB 区间失败日志数：51",
                LocalDateTime.now(),
                1,
                3,
                3,
                0,
                10,
                0,
                9,
                1,
                0,
                0,
                0,
                "2026-06-02",
                51,
                3,
                50,
                List.of(),
                List.of()
        );
    }
}
