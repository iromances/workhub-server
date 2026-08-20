package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.dao.ops.OpsMonitorMapper;
import cn.aslight.workhub.dao.project.BusinessLineMapper;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsMonitorServiceTest {

    @Test
    void list_shouldReturnBusinessLineNameAndLocalizedLastStatus() {
        OpsMonitorMapper mapper = mock(OpsMonitorMapper.class);
        BusinessLineMapper businessLineMapper = mock(BusinessLineMapper.class);
        OpsMonitorSchemaInitializer schemaInitializer = mock(OpsMonitorSchemaInitializer.class);
        McpCryptoService cryptoService = mock(McpCryptoService.class);
        XxlJobMonitorCollector collector = mock(XxlJobMonitorCollector.class);
        OpsMonitorService service = new OpsMonitorService(mapper, businessLineMapper, schemaInitializer, cryptoService, collector);
        OpsMonitorEntity monitor = monitor(9L, "amp_xxl_job");
        monitor.setBusinessLineCode("BL000001");
        monitor.setLastStatus("ERROR");
        BusinessLineEntity businessLine = new BusinessLineEntity();
        businessLine.setBusinessLineCode("BL000001");
        businessLine.setBusinessLineName("保费分期");
        when(mapper.findAll("XXL_JOB", null, null, null, false)).thenReturn(List.of(monitor));
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
        OpsMonitorService service = new OpsMonitorService(mapper, mock(BusinessLineMapper.class), schemaInitializer, cryptoService, collector);
        OpsMonitorEntity existing = monitor(9L, "old_xxl_job");
        OpsMonitorEntity updated = monitor(9L, "amp_xxl_job");
        when(mapper.findByMonitorKey("支付:prod:XXL_JOB")).thenReturn(existing, updated);
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
        OpsMonitorService service = new OpsMonitorService(mapper, mock(BusinessLineMapper.class), schemaInitializer, cryptoService, collector);
        OpsMonitorEntity saved = monitor(10L, "amp_xxl_job");
        saved.setExecutorAppName("premium-job,premium-settle");
        when(mapper.findByMonitorKey("支付:prod:XXL_JOB")).thenReturn(null, saved);
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
        OpsMonitorService service = new OpsMonitorService(mapper, mock(BusinessLineMapper.class), schemaInitializer, cryptoService, collector);
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
        OpsMonitorService service = new OpsMonitorService(mapper, mock(BusinessLineMapper.class), schemaInitializer, cryptoService, collector);
        OpsMonitorEntity monitor = monitor(9L, "amp_xxl_job");
        LocalDate startDate = LocalDate.of(2026, 6, 1);
        LocalDate endDate = LocalDate.of(2026, 6, 9);
        XxlJobLogPageResponse logPage = new XxlJobLogPageResponse(
                9L,
                "支付:prod:XXL_JOB",
                "支付",
                "prod",
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
        OpsMonitorService service = new OpsMonitorService(mapper, mock(BusinessLineMapper.class), schemaInitializer, cryptoService, collector);
        OpsMonitorEntity monitor = monitor(9L, "amp_xxl_job");
        monitor.setExecutorAppName("premium-job,premium-settle");
        monitor.setLastStatus("ERROR");
        monitor.setLastMessage("上次采集失败");
        monitor.setLastCheckedAt(LocalDateTime.of(2026, 6, 9, 10, 30));
        when(mapper.findAll("XXL_JOB", "支付", "prod", null, true)).thenReturn(List.of(monitor));

        List<XxlJobDashboardResponse> dashboards = service.dashboard("支付", "prod", true);

        assertEquals(1, dashboards.size());
        XxlJobDashboardResponse dashboard = dashboards.getFirst();
        assertEquals(9L, dashboard.id());
        assertEquals("支付:prod:XXL_JOB", dashboard.monitorKey());
        assertEquals("支付", dashboard.businessLineCode());
        assertEquals("prod", dashboard.environmentCode());
        assertEquals("支付 prod XXL-JOB", dashboard.name());
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
        OpsMonitorService service = new OpsMonitorService(mapper, mock(BusinessLineMapper.class), schemaInitializer, cryptoService, collector);
        when(collector.listExecutors("保费分期", "prod", "amp_xxl_job"))
                .thenReturn(List.of(new XxlJobExecutorResponse("premium-job", "保费分期任务")));

        List<XxlJobExecutorResponse> executors = service.xxlJobExecutors("保费分期", "prod", "amp_xxl_job");

        assertEquals(1, executors.size());
        assertEquals("premium-job", executors.getFirst().appName());
        verify(collector).listExecutors("保费分期", "prod", "amp_xxl_job");
    }

    private OpsMonitorSaveRequest request(String databaseName) {
        OpsMonitorSaveRequest request = new OpsMonitorSaveRequest();
        request.setMonitorType("XXL_JOB");
        request.setBusinessLineCode("支付");
        request.setEnvironmentCode("prod");
        request.setXxlJobDatabaseName(databaseName);
        request.setEnabled(true);
        return request;
    }

    private OpsMonitorEntity monitor(Long id, String databaseName) {
        OpsMonitorEntity entity = new OpsMonitorEntity();
        entity.setId(id);
        entity.setMonitorType("XXL_JOB");
        entity.setMonitorKey("支付:prod:XXL_JOB");
        entity.setBusinessLineCode("支付");
        entity.setEnvironmentCode("prod");
        entity.setName("支付 prod XXL-JOB");
        entity.setXxlJobDatabaseName(databaseName);
        entity.setEnabled(true);
        return entity;
    }

    private XxlJobDashboardResponse dashboard() {
        return new XxlJobDashboardResponse(
                9L,
                "支付:prod:XXL_JOB",
                "支付",
                "prod",
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
                "2026-06-02",
                51,
                3,
                50,
                List.of()
        );
    }
}
