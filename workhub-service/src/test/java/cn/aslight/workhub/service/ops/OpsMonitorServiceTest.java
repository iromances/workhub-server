package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.dao.ops.OpsMonitorMapper;
import cn.aslight.workhub.model.ops.OpsMonitorEntity;
import cn.aslight.workhub.model.ops.OpsMonitorResponse;
import cn.aslight.workhub.model.ops.OpsMonitorSaveRequest;
import cn.aslight.workhub.model.ops.XxlJobDashboardResponse;
import cn.aslight.workhub.model.ops.XxlJobExecutorResponse;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsMonitorServiceTest {

    @Test
    void create_shouldUpdateExistingDefaultXxlJobMonitorWhenKeyAlreadyExists() {
        OpsMonitorMapper mapper = mock(OpsMonitorMapper.class);
        OpsMonitorSchemaInitializer schemaInitializer = mock(OpsMonitorSchemaInitializer.class);
        McpCryptoService cryptoService = mock(McpCryptoService.class);
        XxlJobMonitorCollector collector = mock(XxlJobMonitorCollector.class);
        OpsMonitorService service = new OpsMonitorService(mapper, schemaInitializer, cryptoService, collector);
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
        OpsMonitorService service = new OpsMonitorService(mapper, schemaInitializer, cryptoService, collector);
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
        OpsMonitorService service = new OpsMonitorService(mapper, schemaInitializer, cryptoService, collector);
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
    void xxlJobExecutors_shouldDelegateToCollectorWithBusinessLineEnvironmentAndDatabase() {
        OpsMonitorMapper mapper = mock(OpsMonitorMapper.class);
        OpsMonitorSchemaInitializer schemaInitializer = mock(OpsMonitorSchemaInitializer.class);
        McpCryptoService cryptoService = mock(McpCryptoService.class);
        XxlJobMonitorCollector collector = mock(XxlJobMonitorCollector.class);
        OpsMonitorService service = new OpsMonitorService(mapper, schemaInitializer, cryptoService, collector);
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
