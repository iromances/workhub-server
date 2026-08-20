package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.dao.intake.IntakeHistoryMapper;
import cn.aslight.workhub.dao.intake.IntakeMapper;
import cn.aslight.workhub.dao.intake.IntakeWorkItemRelationMapper;
import cn.aslight.workhub.dao.project.BusinessLineMapper;
import cn.aslight.workhub.model.intake.IntakeRecordEntity;
import cn.aslight.workhub.model.intake.IntakeDashboardResponse;
import cn.aslight.workhub.model.intake.IntakeHistoryEntity;
import cn.aslight.workhub.model.intake.IntakePriorityUpdateRequest;
import cn.aslight.workhub.model.intake.IntakeSummaryResponse;
import cn.aslight.workhub.model.project.BusinessLineEntity;
import cn.aslight.workhub.service.attachment.AttachmentService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IntakeServiceListFilterTest {

    @Test
    void list_shouldFilterByBusinessLineCode() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        when(intakeMapper.findAll(null)).thenReturn(List.of(
                intakeRecord(1L, "BL000001", "资产业务", "资产需求"),
                intakeRecord(2L, "BL000002", "供应链科技", "供应链需求")
        ));
        IntakeService service = service(intakeMapper);

        List<IntakeSummaryResponse> result = service.list(null, null, null, null, "BL000001", null, null, null, null);

        assertEquals(1, result.size());
        assertEquals(1L, result.getFirst().id());
        assertEquals("资产业务", result.getFirst().businessLine());
        assertEquals("BL000001", result.getFirst().businessLineCode());
    }

    @Test
    void list_shouldFilterByBusinessLineCodeAfterNameChanged() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        when(intakeMapper.findAll(null)).thenReturn(List.of(
                intakeRecord(1L, "BL000001", "资产业务", "资产需求"),
                intakeRecord(2L, "BL000001", "资产业务新名称", "资产改名后需求")
        ));
        IntakeService service = service(intakeMapper);

        List<IntakeSummaryResponse> result = service.list(null, null, null, null, "BL000001", null, null, null, null);

        assertEquals(2, result.size());
    }

    @Test
    void list_shouldKeepOldBehaviorWhenBusinessLineIsBlank() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        when(intakeMapper.findAll(null)).thenReturn(List.of(
                intakeRecord(1L, "BL000001", "资产业务", "资产需求"),
                intakeRecord(2L, "BL000002", "供应链科技", "供应链需求")
        ));
        IntakeService service = service(intakeMapper);

        List<IntakeSummaryResponse> result = service.list(null, null, null, null, "  ", null, null, null, null);

        assertEquals(2, result.size());
    }

    @Test
    void list_shouldReturnEmptyWhenBusinessLineDoesNotMatch() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        when(intakeMapper.findAll(null)).thenReturn(List.of(
                intakeRecord(1L, "BL000001", "资产业务", "资产需求"),
                intakeRecord(2L, "BL000002", "供应链科技", "供应链需求")
        ));
        IntakeService service = service(intakeMapper);

        List<IntakeSummaryResponse> result = service.list(null, null, null, null, "不存在业务线", null, null, null, null);

        assertEquals(0, result.size());
    }

    @Test
    void list_shouldFilterByRequirementType() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        when(intakeMapper.findAll(null)).thenReturn(List.of(
                intakeRecord(1L, "BL000001", "资产业务", "研发需求", "已收录", "研发需求"),
                intakeRecord(2L, "BL000001", "资产业务", "运维需求", "已收录", "数据提取/运维")
        ));
        IntakeService service = service(intakeMapper);

        List<IntakeSummaryResponse> result = service.list(null, null, null, null, null, "研发需求", null, null, null);

        assertEquals(1, result.size());
        assertEquals("研发需求", result.getFirst().requirementType());
        assertEquals("研发需求", result.getFirst().requirementName());
    }

    @Test
    void list_shouldSortByDemandStatusBeforePriority() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        when(intakeMapper.findAll(null)).thenReturn(List.of(
                intakeRecord(1L, "BL000001", "资产业务", "低优先级", "待澄清", "研发需求", "低"),
                intakeRecord(2L, "BL000001", "资产业务", "高优先级", "终止关闭", "研发需求", "高"),
                intakeRecord(3L, "BL000001", "资产业务", "中优先级", "已收录", "研发需求", "中"),
                intakeRecord(4L, "BL000001", "资产业务", "空优先级", "待澄清", "研发需求", null)
        ));
        IntakeService service = service(intakeMapper);

        List<IntakeSummaryResponse> result = service.list(null, null, null, null, null, null, null, null, null);

        assertEquals(List.of(1L, 4L, 3L, 2L), result.stream().map(IntakeSummaryResponse::id).toList());
    }

    @Test
    void list_shouldSortByPriorityAfterDemandStatus() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        when(intakeMapper.findAll(null)).thenReturn(List.of(
                intakeRecord(1L, "BL000001", "资产业务", "开发中高优先级", "开发中", "研发需求", "高"),
                intakeRecord(2L, "BL000001", "资产业务", "开发中低优先级", "开发中", "研发需求", "低")
        ));
        IntakeService service = service(intakeMapper);

        List<IntakeSummaryResponse> result = service.list(null, null, null, null, null, null, null, null, null);

        assertEquals(List.of(1L, 2L), result.stream().map(IntakeSummaryResponse::id).toList());
    }

    @Test
    void list_shouldUseConfiguredDemandStatusOrder() {
        List<String> expectedStatuses = List.of(
                "待澄清", "待处理", "处理中", "待评估", "待排期", "待设计", "开发中",
                "测试中", "待验收", "待上线", "已收录", "已暂停", "已完成", "终止关闭");
        List<IntakeRecordEntity> records = new java.util.ArrayList<>();
        for (int index = expectedStatuses.size() - 1; index >= 0; index--) {
            long id = index + 1L;
            String demandStatus = expectedStatuses.get(index);
            records.add(intakeRecord(
                    id, "BL000001", "资产业务", demandStatus + "需求", demandStatus, "研发需求", "中"));
        }
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        when(intakeMapper.findAll(null)).thenReturn(records);
        IntakeService service = service(intakeMapper);

        List<IntakeSummaryResponse> result = service.list(null, null, null, null, null, null, null, null, null);

        assertEquals(expectedStatuses, result.stream().map(IntakeSummaryResponse::demandStatus).toList());
    }

    @Test
    void list_shouldSortByReceivedAtWhenStatusAndPriorityAreSame() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeRecordEntity newer = intakeRecord(
                1L, "BL000001", "资产业务", "较新需求", "开发中", "研发需求", "高");
        newer.setReceivedAt(LocalDateTime.of(2026, 6, 16, 10, 0));
        IntakeRecordEntity older = intakeRecord(
                2L, "BL000001", "资产业务", "较早需求", "开发中", "研发需求", "高");
        older.setReceivedAt(LocalDateTime.of(2026, 6, 15, 10, 0));
        when(intakeMapper.findAll(null)).thenReturn(List.of(older, newer));
        IntakeService service = service(intakeMapper);

        List<IntakeSummaryResponse> result = service.list(null, null, null, null, null, null, null, null, null);

        assertEquals(List.of(1L, 2L), result.stream().map(IntakeSummaryResponse::id).toList());
    }

    @Test
    void dashboard_shouldSortPendingReleaseDemandsLikeDemandManagement() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        when(intakeMapper.findAll(null)).thenReturn(List.of(
                intakeRecord(1L, "BL000001", "资产业务", "开发中高优先级", "开发中", "研发需求", "高"),
                intakeRecord(2L, "BL000001", "资产业务", "已收录低优先级", "已收录", "研发需求", "低"),
                intakeRecord(3L, "BL000001", "资产业务", "已收录高优先级", "已收录", "研发需求", "高"),
                intakeRecord(4L, "BL000001", "资产业务", "已收录中优先级", "已收录", "研发需求", "中")
        ));
        IntakeService service = service(intakeMapper);

        IntakeDashboardResponse response = service.dashboard("ALL");

        assertEquals(
                List.of(1L, 3L, 4L, 2L),
                response.progressReport().pendingReleaseDemands().stream()
                        .map(IntakeDashboardResponse.ProgressReportDemand::id)
                        .toList()
        );
    }

    @Test
    void dashboard_shouldIgnoreMissingDemandStatusWhenAggregatingStages() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeRecordEntity statusMissing = intakeRecord(
                1L, "BL000001", "资产业务", "状态为空的需求", null, "研发需求", "中");
        statusMissing.setEnrichmentStatus("FAILED");
        when(intakeMapper.findAll(null)).thenReturn(List.of(statusMissing));
        IntakeService service = service(intakeMapper);

        IntakeDashboardResponse response = service.dashboard("ALL");

        assertEquals(1, response.businessLineRecordsCount());
        assertEquals(0, response.demandCards().stream().mapToInt(IntakeDashboardResponse.StageCard::value).sum());
        assertEquals(0, response.businessLineStats().getFirst().totalCount());
        assertEquals(0, response.highlightedDemands().size());
    }

    @Test
    void updatePriority_shouldUpdatePriorityAndRecordHistory() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeHistoryMapper historyMapper = mock(IntakeHistoryMapper.class);
        IntakeRecordEntity entity = intakeRecord(9L, "BL000001", "资产业务", "优先级维护需求", "已收录", "研发需求", "低");
        when(intakeMapper.findById(9L)).thenReturn(entity);
        when(historyMapper.findRecentByIntakeId(9L, 20)).thenReturn(List.of());
        AttachmentService attachmentService = mock(AttachmentService.class);
        when(attachmentService.listIntakeAttachments(9L)).thenReturn(List.of());
        IntakeWorkItemRelationMapper relationMapper = mock(IntakeWorkItemRelationMapper.class);
        when(relationMapper.findByIntakeId(9L)).thenReturn(List.of());
        IntakeService service = service(intakeMapper, historyMapper, relationMapper, null, attachmentService);
        IntakePriorityUpdateRequest request = new IntakePriorityUpdateRequest();
        request.setPriority("高");

        service.updatePriority(9L, request, "admin");

        verify(intakeMapper).updatePriority(9L, "高");
        verify(historyMapper).insert(argThat((IntakeHistoryEntity history) ->
                "修改优先级".equals(history.getActionSummary())
                        && "优先级：低 -> 高".equals(history.getDetailText())
                        && "admin".equals(history.getOperatorUserName())));
    }

    @Test
    void updatePriority_shouldRejectInvalidPriority() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeRecordEntity entity = intakeRecord(9L, "BL000001", "资产业务", "优先级维护需求", "已收录", "研发需求", "低");
        when(intakeMapper.findById(9L)).thenReturn(entity);
        IntakeService service = service(intakeMapper);
        IntakePriorityUpdateRequest request = new IntakePriorityUpdateRequest();
        request.setPriority("紧急");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.updatePriority(9L, request, "admin"));

        assertEquals("优先级仅支持高、中、低", error.getMessage());
        verify(intakeMapper, never()).updatePriority(9L, "紧急");
    }

    @Test
    void dashboard_shouldAggregateBusinessLineStatsByBackendBusinessLineCode() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        when(intakeMapper.findAll(null)).thenReturn(List.of(
                intakeRecord(1L, "BL000001", "保费分期", "保费已收录需求", "已收录", "研发需求"),
                intakeRecord(2L, "BL000001", "", "保费开发需求", "开发中", "研发需求"),
                intakeRecord(3L, "BL000001", "保费分期旧名称", "保费完成需求", "已完成", "数据提取/运维"),
                intakeRecord(4L, "BL000002", "账单管理", "账单待上线需求", "待上线", "研发需求")
        ));
        IntakeService service = service(intakeMapper);

        IntakeDashboardResponse response = service.dashboard("ALL");

        IntakeDashboardResponse.BusinessLineStats premiumStats = response.businessLineStats().getFirst();
        assertEquals("BL000001", premiumStats.businessLineCode());
        assertEquals("保费分期", premiumStats.businessLine());
        assertEquals(3, premiumStats.demandCount());
        assertEquals(2, premiumStats.totalCount());
        assertEquals(1, premiumStats.notStartedCount());
        assertEquals(1, premiumStats.developingCount());
        assertEquals(0, premiumStats.testingCount());
        assertEquals(0, premiumStats.pendingReleaseCount());
        assertEquals(4, response.businessLineRecordsCount());
        assertEquals(3, response.progressReport().pendingReleaseDemands().size());
    }

    @Test
    void dashboard_shouldMergeLegacyBusinessLineNameRowsWithStableBusinessLineCode() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        BusinessLineMapper businessLineMapper = mock(BusinessLineMapper.class);
        when(intakeMapper.findAll(null)).thenReturn(List.of(
                intakeRecord(1L, "BL000001", "保费分期", "保费已收录需求", "已收录", "研发需求"),
                intakeRecord(2L, null, "保费分期", "保费开发需求", "开发中", "研发需求"),
                intakeRecord(3L, null, "未配置业务线", "未配置需求", "待上线", "研发需求")
        ));
        when(businessLineMapper.findByName("保费分期")).thenReturn(businessLine("BL000001", "保费分期"));
        IntakeService service = service(intakeMapper, businessLineMapper);

        IntakeDashboardResponse response = service.dashboard("ALL");

        assertEquals(2, response.businessLineStats().size());
        IntakeDashboardResponse.BusinessLineStats premiumStats = response.businessLineStats().getFirst();
        assertEquals("BL000001", premiumStats.businessLineCode());
        assertEquals("保费分期", premiumStats.businessLine());
        assertEquals(2, premiumStats.demandCount());
        assertEquals(2, premiumStats.totalCount());
        assertEquals(1, premiumStats.notStartedCount());
        assertEquals(1, premiumStats.developingCount());
    }

    @Test
    void dashboard_shouldBuildProgressReportFieldsFromCurrentDemandDates() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeRecordEntity developing = intakeRecord(
                1L, "BL000001", "保费分期", "开发中的需求", "开发中", "研发需求");
        developing.setDevelopmentStartedDate(LocalDate.of(2026, 7, 1));
        developing.setPlannedTestingStartDate(LocalDate.of(2026, 7, 8));
        developing.setPlannedReleaseDate(LocalDate.of(2026, 7, 15));

        IntakeRecordEntity testing = intakeRecord(
                2L, "BL000002", "账单管理", "已经提测的需求", "测试中", "研发需求");
        testing.setTestingStartedDate(LocalDate.of(2026, 7, 9));
        testing.setActualTestingCompletedDate(LocalDate.of(2026, 7, 10));
        testing.setPlannedReleaseDate(LocalDate.of(2026, 7, 20));

        IntakeRecordEntity released = intakeRecord(
                3L, "BL000003", "嘉泰资产平台", "本周上线的需求", "已完成", "研发需求");
        released.setReleasedDate(LocalDate.now());

        IntakeRecordEntity releasedOperations = intakeRecord(
                4L, "BL000004", "资产平台", "本周完成的数据运维需求", "已完成", "数据提取/运维");
        releasedOperations.setReleasedDate(LocalDate.now());

        IntakeRecordEntity emptyDates = new IntakeRecordEntity();
        emptyDates.setId(5L);
        emptyDates.setSourceType("需求截图附件录入");
        emptyDates.setSourceChannel("需求录入");
        emptyDates.setSenderName("测试用户");
        emptyDates.setReceivedAt(LocalDateTime.of(2026, 6, 15, 10, 5));
        emptyDates.setDemandStatus("已收录");
        emptyDates.setEnrichmentStatus("SUCCEEDED");
        emptyDates.setStructuredDataJson("""
                {"requirementType":"研发需求","requirementName":"日期为空的需求","businessLine":"智能柜","developmentStartedDate":"无","plannedTestingStartDate":"无","testingStartedDate":"无","plannedReleaseDate":"无","fields":[],"attachmentSummaries":[]}
                """);

        when(intakeMapper.findAll(null)).thenReturn(List.of(developing, testing, released, releasedOperations, emptyDates));
        IntakeService service = service(intakeMapper);

        IntakeDashboardResponse response = service.dashboard("ALL");

        assertEquals("项目进度", response.progressReport().title());
        assertTrue(response.progressReport().generatedAt().matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
        IntakeDashboardResponse.ProgressReportDemand developingReport = response.progressReport().pendingReleaseDemands().stream()
                .filter(item -> item.id().equals(1L))
                .findFirst()
                .orElseThrow();
        assertEquals(List.of(
                new IntakeDashboardResponse.ProgressReportDateItem("开发日期", "2026-07-01"),
                new IntakeDashboardResponse.ProgressReportDateItem("预计提测日期", "2026-07-08"),
                new IntakeDashboardResponse.ProgressReportDateItem("预计上线日期", "2026-07-15")
        ), developingReport.dateItems());

        IntakeDashboardResponse.ProgressReportDemand testingReport = response.progressReport().pendingReleaseDemands().stream()
                .filter(item -> item.id().equals(2L))
                .findFirst()
                .orElseThrow();
        assertEquals(List.of(
                new IntakeDashboardResponse.ProgressReportDateItem("实际提测日期", "2026-07-09"),
                new IntakeDashboardResponse.ProgressReportDateItem("预计上线日期", "2026-07-20")
        ), testingReport.dateItems());

        IntakeDashboardResponse.ProgressReportDemand emptyDatesReport = response.progressReport().pendingReleaseDemands().stream()
                .filter(item -> item.id().equals(5L))
                .findFirst()
                .orElseThrow();
        assertEquals(List.of(
                new IntakeDashboardResponse.ProgressReportDateItem("开发日期", "-"),
                new IntakeDashboardResponse.ProgressReportDateItem("预计提测日期", "-"),
                new IntakeDashboardResponse.ProgressReportDateItem("预计上线日期", "-")
        ), emptyDatesReport.dateItems());

        List<String> releasedRequirementTypes = response.progressReport().weeklyReleasedDemands().stream()
                .map(IntakeDashboardResponse.ProgressReportReleasedDemand::requirementType)
                .toList();
        assertEquals(2, releasedRequirementTypes.size());
        assertTrue(releasedRequirementTypes.containsAll(List.of("研发需求", "数据提取/运维")));
    }

    private IntakeService service(IntakeMapper intakeMapper) {
        return service(intakeMapper, null);
    }

    private IntakeService service(IntakeMapper intakeMapper, BusinessLineMapper businessLineMapper) {
        return service(
                intakeMapper,
                mock(IntakeHistoryMapper.class),
                mock(IntakeWorkItemRelationMapper.class),
                businessLineMapper,
                mock(AttachmentService.class)
        );
    }

    private IntakeService service(IntakeMapper intakeMapper,
                                  IntakeHistoryMapper historyMapper,
                                  IntakeWorkItemRelationMapper relationMapper,
                                  BusinessLineMapper businessLineMapper,
                                  AttachmentService attachmentService) {
        return new IntakeService(
                intakeMapper,
                historyMapper,
                relationMapper,
                businessLineMapper,
                attachmentService,
                new IntakeStructuredDataExtractor(),
                mock(IntakeEnrichmentService.class),
                mock(CodexCliSqlDraftGenerator.class),
                null,
                new ObjectMapper()
        );
    }

    private BusinessLineEntity businessLine(String code, String name) {
        BusinessLineEntity entity = new BusinessLineEntity();
        entity.setBusinessLineCode(code);
        entity.setBusinessLineName(name);
        return entity;
    }

    private IntakeRecordEntity intakeRecord(Long id, String businessLineCode, String businessLine, String requirementName) {
        return intakeRecord(id, businessLineCode, businessLine, requirementName, "已收录", "研发需求");
    }

    private IntakeRecordEntity intakeRecord(Long id,
                                            String businessLineCode,
                                            String businessLine,
                                            String requirementName,
                                            String demandStatus,
                                            String requirementType) {
        return intakeRecord(id, businessLineCode, businessLine, requirementName, demandStatus, requirementType, null);
    }

    private IntakeRecordEntity intakeRecord(Long id,
                                            String businessLineCode,
                                            String businessLine,
                                            String requirementName,
                                            String demandStatus,
                                            String requirementType,
                                            String priority) {
        IntakeRecordEntity entity = new IntakeRecordEntity();
        entity.setId(id);
        entity.setSourceType("需求截图附件录入");
        entity.setSourceChannel("需求录入");
        entity.setSenderName("测试用户");
        entity.setReceivedAt(LocalDateTime.of(2026, 6, 15, 10, id.intValue()));
        entity.setDemandStatus(demandStatus);
        entity.setEnrichmentStatus("SUCCEEDED");
        entity.setRequirementType(requirementType);
        entity.setRequirementName(requirementName);
        entity.setBusinessLine(businessLine);
        entity.setBusinessLineCode(businessLineCode);
        entity.setPriority(priority);
        return entity;
    }
}
