package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.dao.intake.IntakeHistoryMapper;
import cn.aslight.workhub.dao.intake.IntakeMapper;
import cn.aslight.workhub.dao.intake.IntakeStructuredFieldMapper;
import cn.aslight.workhub.dao.intake.IntakeTodoMapper;
import cn.aslight.workhub.dao.intake.IntakeWorkItemRelationMapper;
import cn.aslight.workhub.dao.system.UserMapper;
import cn.aslight.workhub.model.intake.IntakeDashboardResponse;
import cn.aslight.workhub.model.intake.IntakeRecordEntity;
import cn.aslight.workhub.service.attachment.AttachmentService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IntakeServiceDashboardGroupTest {

    @Test
    void dashboard_shouldCountDevelopmentCompletedIntoDevelopingGroup() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        when(intakeMapper.findAll(null)).thenReturn(List.of(
                dashboardRecord(1L, "开发中"),
                dashboardRecord(2L, "开发完成"),
                dashboardRecord(3L, "测试中")
        ));
        IntakeService service = service(intakeMapper);

        IntakeDashboardResponse response = service.dashboard(null);

        IntakeDashboardResponse.StageCard developing = response.demandCards().stream()
                .filter(card -> "developing".equals(card.key()))
                .findFirst().orElseThrow();
        assertEquals(List.of("开发中", "开发完成", "处理中"), developing.statuses());
        assertEquals(2, developing.value());
        IntakeDashboardResponse.BusinessLineStats stats = response.businessLineStats().getFirst();
        assertEquals(2, stats.developingCount());
        assertEquals(1, stats.testingCount());
        assertEquals(3, stats.totalCount());
    }

    @Test
    void progressReport_shouldSortDevelopmentCompletedBeforeTesting() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        when(intakeMapper.findAll(null)).thenReturn(List.of(
                dashboardRecord(3L, "测试中"),
                dashboardRecord(2L, "开发完成"),
                dashboardRecord(1L, "开发中")
        ));
        IntakeService service = service(intakeMapper);

        List<IntakeDashboardResponse.ProgressReportDemand> pending =
                service.dashboard(null).progressReport().pendingReleaseDemands();

        assertEquals(List.of(1L, 2L, 3L), pending.stream()
                .map(IntakeDashboardResponse.ProgressReportDemand::id)
                .toList());
        assertEquals(List.of("开发中", "开发完成", "测试中"), pending.stream()
                .map(IntakeDashboardResponse.ProgressReportDemand::demandStatus)
                .toList());
    }

    private IntakeService service(IntakeMapper intakeMapper) {
        return new IntakeService(
                intakeMapper,
                mock(IntakeHistoryMapper.class),
                mock(IntakeWorkItemRelationMapper.class),
                mock(IntakeTodoMapper.class),
                mock(IntakeStructuredFieldMapper.class),
                null,
                mock(AttachmentService.class),
                new IntakeStructuredDataExtractor(),
                new IntakeStructuredFieldNormalizer(),
                mock(IntakeEnrichmentService.class),
                mock(CodexCliSqlDraftGenerator.class),
                null,
                new ObjectMapper(),
                null,
                mock(UserMapper.class)
        );
    }

    private IntakeRecordEntity dashboardRecord(Long id, String demandStatus) {
        IntakeRecordEntity entity = new IntakeRecordEntity();
        entity.setId(id);
        entity.setSourceType("需求截图附件录入");
        entity.setSourceChannel("需求录入");
        entity.setSenderName("测试用户");
        entity.setReceivedAt(LocalDateTime.of(2026, 11, 9, 10, 0));
        entity.setDemandStatus(demandStatus);
        entity.setEnrichmentStatus("SUCCEEDED");
        entity.setRequirementType("研发需求");
        entity.setRequirementName("需求" + id);
        entity.setBusinessLineCode("BL000001");
        entity.setPriority("中");
        return entity;
    }
}
