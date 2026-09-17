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
import cn.aslight.workhub.model.intake.IntakeListQuery;
import cn.aslight.workhub.model.project.BusinessLineEntity;
import cn.aslight.workhub.service.attachment.AttachmentService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class IntakeServiceListFilterTest {

    @Test
    void page_shouldPassNormalizedFiltersAndKeepDatabasePageOrderAndTotal() {
        IntakeMapper mapper = mock(IntakeMapper.class);
        IntakeRecordEntity first = intakeRecord(1L, "BL000001", "资产业务", "正式名称", "开发中", "研发需求", "低");
        first.setApprovalCode("REQ-001");
        first.setProposerName("周拓");
        first.setRequirementSummary("正式说明");
        first.setRemark("正式备注");
        first.setReleasedDate(LocalDate.of(2026, 9, 1));
        first.setStructuredDataJson("""
                {"requirementName":"旧名称","approvalCode":"旧编号","proposerName":"旧提出人","releasedTime":"2026/01/01"}
                """);
        IntakeRecordEntity second = intakeRecord(2L, "BL000001", "资产业务", "第二条", "待澄清", "研发需求", "高");
        when(mapper.count(any())).thenReturn(31L);
        when(mapper.findPage(any())).thenReturn(List.of(first, second));

        var result = service(mapper).page(" 待整理 ", " 名称 ", " REQ ", " 周 ", " BL000001 ",
                " 研发需求 ", " 开发中 ", "2026/9/1", "2026-09-08", 2, 10);

        assertEquals(31, result.total());
        assertEquals(List.of(1L, 2L), result.items().stream().map(IntakeSummaryResponse::id).toList());
        assertEquals("正式名称", result.items().getFirst().requirementName());
        assertEquals("REQ-001", result.items().getFirst().approvalCode());
        assertEquals("周拓", result.items().getFirst().proposerName());
        assertEquals("正式说明", result.items().getFirst().requirementSummary());
        assertEquals("正式备注", result.items().getFirst().remark());
        assertEquals("2026/09/01", result.items().getFirst().releasedTime());
        var captor = ArgumentCaptor.forClass(IntakeListQuery.class);
        verify(mapper).count(captor.capture());
        IntakeListQuery query = captor.getValue();
        assertEquals(new IntakeListQuery("待整理", "名称", "REQ", "周", "BL000001", "研发需求", "开发中",
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 8), 10, 10), query);
        verify(mapper).findPage(query);
        verify(mapper, never()).findAll(any());
    }

    @Test
    void page_shouldSkipPageQueryForEmptyOrOutOfRangeResults() {
        IntakeMapper mapper = mock(IntakeMapper.class);
        when(mapper.count(any())).thenReturn(0L, 11L);
        IntakeService service = service(mapper);

        assertTrue(service.page(null, null, null, null, null, null, null, null, null, 1, 10).items().isEmpty());
        var beyondEnd = service.page(null, null, null, null, null, null, null, null, null, 3, 10);
        assertEquals(11, beyondEnd.total());
        assertTrue(beyondEnd.items().isEmpty());
        verify(mapper, never()).findPage(any());
    }

    @Test
    void page_shouldNormalizeNonPositivePaginationAndAvoidIntegerOverflow() {
        IntakeMapper mapper = mock(IntakeMapper.class);
        when(mapper.count(any())).thenReturn(0L);
        IntakeService service = service(mapper);
        service.page(" ", " ", " ", " ", " ", " ", " ", " ", " ", 0, -1);
        verify(mapper).count(new IntakeListQuery(null, null, null, null, null, null, null, null, null, 0, 1));

        service.page(null, null, null, null, null, null, null, null, null, Integer.MAX_VALUE, Integer.MAX_VALUE);
        verify(mapper).count(new IntakeListQuery(null, null, null, null, null, null, null, null, null,
                (long) (Integer.MAX_VALUE - 1) * Integer.MAX_VALUE, Integer.MAX_VALUE));
    }

    @Test
    void page_shouldRejectInvalidDatesBeforeQueryingDatabase() {
        IntakeMapper mapper = mock(IntakeMapper.class);
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service(mapper).page(null, null, null, null, null, null, null, "2026/02/30", null, 1, 10));
        assertEquals("上线开始日期格式不正确，请使用 yyyy/MM/dd", exception.getMessage());
        verifyNoInteractions(mapper);
    }

    @Test
    void search_shouldPassKeywordAndLimitToDatabase() {
        IntakeMapper mapper = mock(IntakeMapper.class);
        when(mapper.findPage(any())).thenReturn(List.of());

        service(mapper).search(" REQ ", " 名称 ", " MCP ", 10);

        verify(mapper).findPage(new IntakeListQuery(null, "名称", "REQ", null, null, null, null,
                null, null, 0, 10, "MCP"));
        verify(mapper, never()).findAll(any());
        verify(mapper, never()).count(any());
    }

    @Test
    void dashboard_shouldSortPendingReleaseDemandsLikeDemandManagement() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        when(intakeMapper.findAll(null)).thenReturn(List.of(
                intakeRecord(1L, "BL000001", "资产业务", "开发中低优先级", "开发中", "研发需求", "低"),
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
