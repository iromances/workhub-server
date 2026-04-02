package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.dao.intake.IntakeMapper;
import cn.aslight.workhub.dao.intake.IntakeHistoryMapper;
import cn.aslight.workhub.model.intake.IntakeRecordEntity;
import cn.aslight.workhub.model.intake.IntakeStageActionRequest;
import cn.aslight.workhub.model.intake.IntakeZentaoLinkRequest;
import cn.aslight.workhub.model.intake.IntakeSummaryResponse;
import cn.aslight.workhub.service.attachment.AttachmentService;
import cn.aslight.workhub.service.workitem.WorkItemFollowUpService;
import cn.aslight.workhub.service.workitem.WorkItemService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IntakeServiceTest {

    @Test
    void list_shouldFlattenStructuredDataFromJsonInsteadOfDatabaseJsonFunctions() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeHistoryMapper intakeHistoryMapper = mock(IntakeHistoryMapper.class);
        WorkItemService workItemService = mock(WorkItemService.class);
        AttachmentService attachmentService = mock(AttachmentService.class);
        WorkItemFollowUpService workItemFollowUpService = mock(WorkItemFollowUpService.class);
        IntakeStructuredDataExtractor intakeStructuredDataExtractor = new IntakeStructuredDataExtractor();
        IntakeEnrichmentService intakeEnrichmentService = mock(IntakeEnrichmentService.class);

        IntakeService service = new IntakeService(
                intakeMapper,
                intakeHistoryMapper,
                workItemService,
                attachmentService,
                workItemFollowUpService,
                intakeStructuredDataExtractor,
                intakeEnrichmentService,
                new ObjectMapper()
        );

        IntakeRecordEntity populated = new IntakeRecordEntity();
        populated.setId(101L);
        populated.setSourceType("需求截图附件录入");
        populated.setSourceChannel("需求截图录入");
        populated.setSenderName("zhoutuo");
        populated.setReceivedAt(LocalDateTime.of(2026, 3, 31, 10, 0));
        populated.setStructuredDataJson("""
                {"category":"需求审批","approvalTitle":"周拓的系统开发2.0","proposerName":"周拓","approvalCode":"202603250009","submittedTime":"2026/3/25 16:17","requirementType":"研发需求","requirementDigest":"沃橙绑卡至嘉泰保理","requirementName":"沃橙项目增加客户绑卡至嘉泰保理的需求0325","requirementSummary":"为避免单一支付通道暂停风险，需要补充嘉泰保理易宝商户号绑卡方案。","department":"供应链业务部","businessLine":"供应链科技","remark":"涉及沃诚项目额度释放，最高优先级。","estimatedEffort":"2d","plannedDueDate":"2026/3/31","actualEffort":"无","actualCompletedTime":"无","acceptanceTime":"无","projectHint":"供应链科技","fields":[],"attachmentSummaries":[]}
                """);
        populated.setDemandStatus("已收录");
        populated.setEnrichmentStatus("SUCCEEDED");
        populated.setIntakeStatus("待整理");

        IntakeRecordEntity blank = new IntakeRecordEntity();
        blank.setId(102L);
        blank.setSourceType("人工录入");
        blank.setSourceChannel("人工录入");
        blank.setSenderName("admin");
        blank.setReceivedAt(LocalDateTime.of(2026, 3, 31, 11, 0));
        blank.setStructuredDataJson(null);
        blank.setRawContent("""
                提出人：张三
                审批编号：LEGACY-001
                提交时间：2026/3/31 11:00
                所在部门：产品部
                需求名称：历史数据补解析
                预估工时：1d
                """);
        blank.setDemandStatus(null);
        blank.setEnrichmentStatus("PENDING");
        blank.setIntakeStatus("待整理");

        when(intakeMapper.findAll(eq(null), eq(null), eq(null), eq(null))).thenReturn(List.of(populated, blank));

        List<IntakeSummaryResponse> result = service.list(null, null, null, null, null);

        assertEquals(2, result.size());
        assertEquals("202603250009", result.get(0).approvalCode());
        assertEquals("供应链业务部", result.get(0).department());
        assertEquals("沃橙项目增加客户绑卡至嘉泰保理的需求0325", result.get(0).requirementName());
        assertEquals("周拓", result.get(0).proposerName());
        assertEquals("2026/3/25 16:17", result.get(0).submittedTime());
        assertEquals("研发需求", result.get(0).requirementType());
        assertEquals("沃橙绑卡至嘉泰保理", result.get(0).requirementDigest());
        assertEquals("2d", result.get(0).estimatedEffort());
        assertEquals("已收录", result.get(0).demandStatus());
        assertEquals("SUCCEEDED", result.get(0).enrichmentStatus());

        assertEquals("LEGACY-001", result.get(1).approvalCode());
        assertEquals("历史数据补解析", result.get(1).requirementName());
        assertEquals("张三", result.get(1).proposerName());
        assertEquals("2026/3/31 11:00", result.get(1).submittedTime());
        assertEquals("研发需求", result.get(1).requirementType());
        assertEquals("已收录", result.get(1).demandStatus());
        assertEquals("PENDING", result.get(1).enrichmentStatus());
    }

    @Test
    void advanceStage_shouldEvaluateEffortAndMoveToEvaluated() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeHistoryMapper intakeHistoryMapper = mock(IntakeHistoryMapper.class);
        WorkItemService workItemService = mock(WorkItemService.class);
        AttachmentService attachmentService = mock(AttachmentService.class);
        WorkItemFollowUpService workItemFollowUpService = mock(WorkItemFollowUpService.class);
        IntakeStructuredDataExtractor intakeStructuredDataExtractor = new IntakeStructuredDataExtractor();
        IntakeEnrichmentService intakeEnrichmentService = mock(IntakeEnrichmentService.class);

        IntakeService service = new IntakeService(
                intakeMapper,
                intakeHistoryMapper,
                workItemService,
                attachmentService,
                workItemFollowUpService,
                intakeStructuredDataExtractor,
                intakeEnrichmentService,
                new ObjectMapper()
        );

        IntakeRecordEntity before = new IntakeRecordEntity();
        before.setId(201L);
        before.setSenderName("zhoutuo");
        before.setReceivedAt(LocalDateTime.of(2026, 4, 1, 10, 0));
        before.setStructuredDataJson("""
                {"category":"需求审批","approvalTitle":"周拓的系统开发2.0","proposerName":"周拓","approvalCode":"202603250009","submittedTime":"2026/3/25 16:17","requirementType":"研发需求","requirementDigest":"沃橙绑卡","requirementName":"沃橙绑卡需求","requirementSummary":"描述","department":"供应链业务部","businessLine":"供应链科技","remark":"高优","estimatedEffort":null,"plannedDueDate":null,"developmentStartedDate":null,"actualEffort":null,"testingStartedDate":null,"actualCompletedTime":null,"acceptanceTime":null,"releasedTime":null,"projectHint":"供应链科技","fields":[],"attachmentSummaries":[]}
                """);
        before.setDemandStatus("已收录");

        IntakeRecordEntity after = new IntakeRecordEntity();
        after.setId(201L);
        after.setSenderName("zhoutuo");
        after.setReceivedAt(LocalDateTime.of(2026, 4, 1, 10, 0));
        after.setStructuredDataJson("""
                {"category":"需求审批","approvalTitle":"周拓的系统开发2.0","proposerName":"周拓","approvalCode":"202603250009","submittedTime":"2026/3/25 16:17","requirementType":"研发需求","requirementDigest":"沃橙绑卡","requirementName":"沃橙绑卡需求","requirementSummary":"描述","department":"供应链业务部","businessLine":"供应链科技","remark":"高优","estimatedEffort":"2d","plannedDueDate":"2026/04/05","developmentStartedDate":null,"actualEffort":null,"testingStartedDate":null,"actualCompletedTime":null,"acceptanceTime":null,"releasedTime":null,"projectHint":"供应链科技","fields":[],"attachmentSummaries":[]}
                """);
        after.setDemandStatus("已评估");

        when(intakeMapper.findById(201L)).thenReturn(before, after);
        when(attachmentService.listIntakeAttachments(201L)).thenReturn(List.of());
        when(intakeHistoryMapper.findRecentByIntakeId(201L, 20)).thenReturn(List.of());

        IntakeStageActionRequest request = new IntakeStageActionRequest();
        request.setAction("EVALUATE_EFFORT");
        request.setEstimatedEffort("2d");
        request.setPlannedDueDate("2026/04/05");

        service.advanceStage(201L, request, "admin");

        ArgumentCaptor<String> structuredJsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(intakeMapper).updateManagementFields(eq(201L), structuredJsonCaptor.capture(), eq("已评估"));
        assertEquals(true, structuredJsonCaptor.getValue().contains("\"estimatedEffort\":\"2d\""));
        assertEquals(true, structuredJsonCaptor.getValue().contains("\"plannedDueDate\":\"2026/04/05\""));
        verify(intakeHistoryMapper).insert(any());
        verify(intakeHistoryMapper).findRecentByIntakeId(201L, 20);
    }

    @Test
    void detail_shouldNormalizeLegacyRequirementTypeGenerateBranchAndBackfillProposer() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeHistoryMapper intakeHistoryMapper = mock(IntakeHistoryMapper.class);
        WorkItemService workItemService = mock(WorkItemService.class);
        AttachmentService attachmentService = mock(AttachmentService.class);
        WorkItemFollowUpService workItemFollowUpService = mock(WorkItemFollowUpService.class);
        IntakeStructuredDataExtractor intakeStructuredDataExtractor = new IntakeStructuredDataExtractor();
        IntakeEnrichmentService intakeEnrichmentService = mock(IntakeEnrichmentService.class);

        IntakeService service = new IntakeService(
                intakeMapper,
                intakeHistoryMapper,
                workItemService,
                attachmentService,
                workItemFollowUpService,
                intakeStructuredDataExtractor,
                intakeEnrichmentService,
                new ObjectMapper()
        );

        IntakeRecordEntity entity = new IntakeRecordEntity();
        entity.setId(301L);
        entity.setSenderName("admin");
        entity.setReceivedAt(LocalDateTime.of(2026, 4, 1, 10, 0));
        entity.setStructuredDataJson("""
                {"category":"需求审批","approvalTitle":"范怡的系统开发2.0","proposerName":null,"approvalCode":"202603240005","submittedTime":"2026/3/24 11:54","requirementType":"研发","requirementDigest":"新增分账核验规则并改造接口","requirementName":"里易二轮车换电项目迭代优化（三期）——新增分账核验规则","requirementSummary":"描述","department":"产品部","businessLine":"设备运营","remark":"高优","estimatedEffort":null,"plannedDueDate":null,"developmentStartedDate":null,"actualEffort":null,"testingStartedDate":null,"actualCompletedTime":null,"acceptanceTime":null,"releasedTime":null,"projectHint":"设备运营","fields":[],"attachmentSummaries":[]}
                """);
        entity.setDemandStatus("已收录");
        entity.setEnrichmentStatus("SUCCEEDED");
        entity.setIntakeStatus("待整理");

        when(intakeMapper.findById(301L)).thenReturn(entity);
        when(attachmentService.listIntakeAttachments(301L)).thenReturn(List.of());
        when(intakeHistoryMapper.findRecentByIntakeId(301L, 20)).thenReturn(List.of());

        var detail = service.detail(301L);

        assertEquals("范怡", detail.structuredData().proposerName());
        assertEquals("研发需求", detail.structuredData().requirementType());
        assertEquals("feature/req-202603240005", detail.structuredData().developmentBranchName());
    }

    @Test
    void updateZentaoLink_shouldPersistUrlAndRecordHistory() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeHistoryMapper intakeHistoryMapper = mock(IntakeHistoryMapper.class);
        WorkItemService workItemService = mock(WorkItemService.class);
        AttachmentService attachmentService = mock(AttachmentService.class);
        WorkItemFollowUpService workItemFollowUpService = mock(WorkItemFollowUpService.class);
        IntakeStructuredDataExtractor intakeStructuredDataExtractor = new IntakeStructuredDataExtractor();
        IntakeEnrichmentService intakeEnrichmentService = mock(IntakeEnrichmentService.class);

        IntakeService service = new IntakeService(
                intakeMapper,
                intakeHistoryMapper,
                workItemService,
                attachmentService,
                workItemFollowUpService,
                intakeStructuredDataExtractor,
                intakeEnrichmentService,
                new ObjectMapper()
        );

        IntakeRecordEntity before = new IntakeRecordEntity();
        before.setId(401L);
        before.setSenderName("admin");
        before.setReceivedAt(LocalDateTime.of(2026, 4, 1, 10, 0));
        before.setStructuredDataJson("""
                {"category":"需求审批","approvalTitle":"周拓的系统开发2.0","proposerName":"周拓","approvalCode":"202603250009","submittedTime":"2026/3/25 16:17","requirementType":"研发需求","developmentBranchName":"feature/req-202603250009","zentaoUrl":null,"requirementDigest":"沃橙绑卡","requirementName":"沃橙绑卡需求","requirementSummary":"描述","department":"供应链业务部","businessLine":"供应链科技","remark":"高优","estimatedEffort":"2d","plannedDueDate":"2026/04/05","developmentStartedDate":null,"actualEffort":null,"testingStartedDate":null,"actualCompletedTime":null,"acceptanceTime":null,"releasedTime":null,"projectHint":"供应链科技","fields":[],"attachmentSummaries":[]}
                """);
        before.setDemandStatus("已评估");
        before.setEnrichmentStatus("SUCCEEDED");
        before.setIntakeStatus("待整理");

        IntakeRecordEntity after = new IntakeRecordEntity();
        after.setId(401L);
        after.setSenderName("admin");
        after.setReceivedAt(LocalDateTime.of(2026, 4, 1, 10, 0));
        after.setStructuredDataJson("""
                {"category":"需求审批","approvalTitle":"周拓的系统开发2.0","proposerName":"周拓","approvalCode":"202603250009","submittedTime":"2026/3/25 16:17","requirementType":"研发需求","developmentBranchName":"feature/req-202603250009","zentaoUrl":"https://zentao.example.com/story-view-123.html","requirementDigest":"沃橙绑卡","requirementName":"沃橙绑卡需求","requirementSummary":"描述","department":"供应链业务部","businessLine":"供应链科技","remark":"高优","estimatedEffort":"2d","plannedDueDate":"2026/04/05","developmentStartedDate":null,"actualEffort":null,"testingStartedDate":null,"actualCompletedTime":null,"acceptanceTime":null,"releasedTime":null,"projectHint":"供应链科技","fields":[],"attachmentSummaries":[]}
                """);
        after.setDemandStatus("已评估");
        after.setEnrichmentStatus("SUCCEEDED");
        after.setIntakeStatus("待整理");

        when(intakeMapper.findById(401L)).thenReturn(before, after);
        when(attachmentService.listIntakeAttachments(401L)).thenReturn(List.of());
        when(intakeHistoryMapper.findRecentByIntakeId(401L, 20)).thenReturn(List.of());

        IntakeZentaoLinkRequest request = new IntakeZentaoLinkRequest();
        request.setZentaoUrl("https://zentao.example.com/story-view-123.html");

        var detail = service.updateZentaoLink(401L, request, "admin");

        ArgumentCaptor<String> structuredJsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(intakeMapper).updateStructuredData(eq(401L), structuredJsonCaptor.capture());
        assertTrue(structuredJsonCaptor.getValue().contains("\"zentaoUrl\":\"https://zentao.example.com/story-view-123.html\""));
        verify(intakeHistoryMapper).insert(any());
        assertEquals("https://zentao.example.com/story-view-123.html", detail.structuredData().zentaoUrl());
    }
}
