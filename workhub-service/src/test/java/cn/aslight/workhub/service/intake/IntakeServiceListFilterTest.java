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

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void list_shouldSortByPriorityAfterExistingStatusSort() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        when(intakeMapper.findAll(null)).thenReturn(List.of(
                intakeRecord(1L, "BL000001", "资产业务", "低优先级", "已收录", "研发需求", "低"),
                intakeRecord(2L, "BL000001", "资产业务", "高优先级", "已收录", "研发需求", "高"),
                intakeRecord(3L, "BL000001", "资产业务", "中优先级", "已收录", "研发需求", "中"),
                intakeRecord(4L, "BL000001", "资产业务", "空优先级", "已收录", "研发需求", null)
        ));
        IntakeService service = service(intakeMapper);

        List<IntakeSummaryResponse> result = service.list(null, null, null, null, null, null, null, null, null);

        assertEquals(List.of(2L, 3L, 1L, 4L), result.stream().map(IntakeSummaryResponse::id).toList());
    }

    @Test
    void list_shouldKeepDemandStatusOrderBeforePriorityOrder() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        when(intakeMapper.findAll(null)).thenReturn(List.of(
                intakeRecord(1L, "BL000001", "资产业务", "开发中高优先级", "开发中", "研发需求", "高"),
                intakeRecord(2L, "BL000001", "资产业务", "已收录低优先级", "已收录", "研发需求", "低")
        ));
        IntakeService service = service(intakeMapper);

        List<IntakeSummaryResponse> result = service.list(null, null, null, null, null, null, null, null, null);

        assertEquals(List.of(2L, 1L), result.stream().map(IntakeSummaryResponse::id).toList());
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
