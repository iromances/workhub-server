package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.dao.intake.IntakeMapper;
import cn.aslight.workhub.dao.intake.IntakeHistoryMapper;
import cn.aslight.workhub.dao.intake.IntakeWorkItemRelationMapper;
import cn.aslight.workhub.dao.project.BusinessLineMapper;
import cn.aslight.workhub.model.intake.IntakeBusinessLineUpdateRequest;
import cn.aslight.workhub.model.intake.IntakeDevelopmentBranchRequest;
import cn.aslight.workhub.model.intake.IntakeHistoryEntity;
import cn.aslight.workhub.model.intake.IntakePauseRequest;
import cn.aslight.workhub.model.intake.IntakeRecordEntity;
import cn.aslight.workhub.model.intake.IntakeSqlDraft;
import cn.aslight.workhub.model.intake.IntakeStageActionRequest;
import cn.aslight.workhub.model.intake.IntakeZentaoLinkRequest;
import cn.aslight.workhub.model.intake.IntakeSummaryResponse;
import cn.aslight.workhub.model.project.BusinessLineEntity;
import cn.aslight.workhub.service.attachment.AttachmentService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class IntakeServiceTest {

    @Test
    void pauseDemand_shouldRecordPreviousStatusReasonPauseDateAndHistory() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeHistoryMapper intakeHistoryMapper = mock(IntakeHistoryMapper.class);
        IntakeService service = new IntakeService(
                intakeMapper,
                intakeHistoryMapper,
                mock(AttachmentService.class),
                new IntakeStructuredDataExtractor(),
                mock(IntakeEnrichmentService.class),
                mock(CodexCliSqlDraftGenerator.class),
                new ObjectMapper()
        );

        IntakeRecordEntity entity = new IntakeRecordEntity();
        entity.setId(901L);
        entity.setDemandStatus("测试中");
        entity.setEnrichmentStatus("SUCCEEDED");
        entity.setStructuredDataJson("""
                {"requirementType":"研发需求","requirementName":"暂停入口测试","fields":[],"attachmentSummaries":[]}
                """);
        when(intakeMapper.findById(901L)).thenReturn(entity);

        IntakePauseRequest request = new IntakePauseRequest();
        request.setReason("等待外部联调环境");
        request.setPauseDate(LocalDate.of(2026, 6, 4));

        service.pauseDemand(901L, request, "admin");

        verify(intakeMapper).pauseDemand(901L, "测试中", "等待外部联调环境", LocalDate.of(2026, 6, 4));
        ArgumentCaptor<IntakeHistoryEntity> historyCaptor = ArgumentCaptor.forClass(IntakeHistoryEntity.class);
        verify(intakeHistoryMapper).insert(historyCaptor.capture());
        assertEquals("暂停需求", historyCaptor.getValue().getActionSummary());
        assertTrue(historyCaptor.getValue().getDetailText().contains("需求状态：测试中 -> 已暂停"));
        assertTrue(historyCaptor.getValue().getDetailText().contains("暂停日期：- -> 2026-06-04"));
        assertTrue(historyCaptor.getValue().getDetailText().contains("暂停原因：- -> 等待外部联调环境"));
    }

    @Test
    void resumeDemand_shouldRestorePreviousStatusAndRecordHistory() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeHistoryMapper intakeHistoryMapper = mock(IntakeHistoryMapper.class);
        IntakeService service = new IntakeService(
                intakeMapper,
                intakeHistoryMapper,
                mock(AttachmentService.class),
                new IntakeStructuredDataExtractor(),
                mock(IntakeEnrichmentService.class),
                mock(CodexCliSqlDraftGenerator.class),
                new ObjectMapper()
        );

        IntakeRecordEntity entity = new IntakeRecordEntity();
        entity.setId(902L);
        entity.setDemandStatus("已暂停");
        entity.setPausePreviousDemandStatus("待验收");
        entity.setPauseReason("等待验收排期");
        entity.setPauseDate(LocalDate.of(2026, 6, 3));
        entity.setEnrichmentStatus("SUCCEEDED");
        entity.setStructuredDataJson("""
                {"requirementType":"研发需求","requirementName":"恢复入口测试","fields":[],"attachmentSummaries":[]}
                """);
        when(intakeMapper.findById(902L)).thenReturn(entity);

        service.resumeDemand(902L, "admin");

        verify(intakeMapper).restorePausedDemand(902L, "待验收");
        ArgumentCaptor<IntakeHistoryEntity> historyCaptor = ArgumentCaptor.forClass(IntakeHistoryEntity.class);
        verify(intakeHistoryMapper).insert(historyCaptor.capture());
        assertEquals("恢复需求", historyCaptor.getValue().getActionSummary());
        assertTrue(historyCaptor.getValue().getDetailText().contains("需求状态：已暂停 -> 待验收"));
        assertTrue(historyCaptor.getValue().getDetailText().contains("暂停前状态：待验收"));
    }

    @Test
    void list_shouldFlattenStructuredDataFromJsonInsteadOfDatabaseJsonFunctions() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeHistoryMapper intakeHistoryMapper = mock(IntakeHistoryMapper.class);
        AttachmentService attachmentService = mock(AttachmentService.class);
        IntakeStructuredDataExtractor intakeStructuredDataExtractor = new IntakeStructuredDataExtractor();
        IntakeEnrichmentService intakeEnrichmentService = mock(IntakeEnrichmentService.class);

        IntakeService service = new IntakeService(
                intakeMapper,
                intakeHistoryMapper,
                attachmentService,
                intakeStructuredDataExtractor,
                intakeEnrichmentService,
                mock(CodexCliSqlDraftGenerator.class),
                new ObjectMapper()
        );

        IntakeRecordEntity populated = new IntakeRecordEntity();
        populated.setId(101L);
        populated.setSourceType("需求截图附件录入");
        populated.setSourceChannel("需求截图录入");
        populated.setSenderName("zhoutuo");
        populated.setReceivedAt(LocalDateTime.of(2026, 3, 31, 10, 0));
        populated.setStructuredDataJson("""
                {"category":"需求审批","approvalTitle":"周拓的系统开发2.0","proposerName":"周拓","approvalCode":"202603250009","submittedTime":"2026/3/25 16:17","requirementType":"研发需求","requirementDigest":"沃橙绑卡至嘉泰保理","requirementName":"沃橙项目增加客户绑卡至嘉泰保理的需求0325","requirementSummary":"为避免单一支付通道暂停风险，需要补充嘉泰保理易宝商户号绑卡方案。","department":"供应链业务部","businessLine":"供应链科技","remark":"涉及沃诚项目额度释放，最高优先级。","estimatedEffort":"2d","plannedDueDate":"2026/3/31","plannedDevelopmentStartDate":"2026/04/01","actualEffort":"无","actualCompletedTime":"无","acceptanceTime":"无","projectHint":"供应链科技","fields":[],"attachmentSummaries":[]}
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

        when(intakeMapper.findAll(eq(null))).thenReturn(List.of(populated, blank));

        List<IntakeSummaryResponse> result = service.list(null, null, null, null, null, null, null);

        assertEquals(2, result.size());
        assertEquals("202603250009", result.get(0).approvalCode());
        assertEquals("供应链业务部", result.get(0).department());
        assertEquals("沃橙项目增加客户绑卡至嘉泰保理的需求0325", result.get(0).requirementName());
        assertEquals("周拓", result.get(0).proposerName());
        assertEquals("2026/3/25 16:17", result.get(0).submittedTime());
        assertEquals("研发需求", result.get(0).requirementType());
        assertEquals("沃橙绑卡至嘉泰保理", result.get(0).requirementDigest());
        assertEquals("2026/04/01", result.get(0).plannedDevelopmentStartDate());
        assertEquals("已收录", result.get(0).demandStatus());
        assertEquals("SUCCEEDED", result.get(0).enrichmentStatus());

        assertEquals("LEGACY-001", result.get(1).approvalCode());
        assertEquals("历史数据补解析", result.get(1).requirementName());
        assertEquals("张三", result.get(1).proposerName());
        assertEquals("2026/3/31 11:00", result.get(1).submittedTime());
        assertEquals("研发需求", result.get(1).requirementType());
        assertNull(result.get(1).demandStatus());
        assertEquals("PENDING", result.get(1).enrichmentStatus());
    }

    @Test
    void list_shouldFallbackOperationsEffortAndDatesFromStructuredData() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeHistoryMapper intakeHistoryMapper = mock(IntakeHistoryMapper.class);
        AttachmentService attachmentService = mock(AttachmentService.class);
        IntakeStructuredDataExtractor intakeStructuredDataExtractor = new IntakeStructuredDataExtractor();
        IntakeEnrichmentService intakeEnrichmentService = mock(IntakeEnrichmentService.class);

        IntakeService service = new IntakeService(
                intakeMapper,
                intakeHistoryMapper,
                attachmentService,
                intakeStructuredDataExtractor,
                intakeEnrichmentService,
                mock(CodexCliSqlDraftGenerator.class),
                new ObjectMapper()
        );

        IntakeRecordEntity operations = new IntakeRecordEntity();
        operations.setId(103L);
        operations.setSourceType("需求截图附件录入");
        operations.setSourceChannel("需求录入");
        operations.setSenderName("ops-user");
        operations.setReceivedAt(LocalDateTime.of(2026, 4, 2, 10, 0));
        operations.setStructuredDataJson("""
                {"category":"需求审批","approvalTitle":"数据修复申请","proposerName":"运维同学","approvalCode":"OPS-004","submittedTime":"2026/4/2 10:00","requirementType":"数据提取/运维","requirementDigest":"批量修复历史还款数据","requirementName":"批量修复历史还款数据","requirementSummary":"描述","department":"运营支持部","businessLine":"资产业务","remark":"紧急处理","estimatedEffort":"4h","plannedDueDate":"2026/04/02","developmentStartedDate":"2026/04/02","actualEffort":"5h","testingStartedDate":null,"actualCompletedTime":"2026/04/03","acceptanceTime":"2026/04/03","releasedTime":"2026/04/03","projectHint":"资产业务","fields":[],"attachmentSummaries":[]}
                """);
        operations.setDemandStatus("已完成");
        operations.setEnrichmentStatus("SUCCEEDED");
        operations.setIntakeStatus("待整理");

        when(intakeMapper.findAll(eq(null))).thenReturn(List.of(operations));

        List<IntakeSummaryResponse> result = service.list(null, null, null, null, null, null, null);

        assertEquals(1, result.size());
        IntakeSummaryResponse item = result.getFirst();
        assertEquals("数据提取/运维", item.requirementType());
        assertEquals("4h", item.totalEstimatedEffort());
        assertEquals("4h", item.developmentEstimatedEffort());
        assertNull(item.testingEstimatedEffort());
        assertEquals("2026/04/02", item.developmentStartedDate());
        assertEquals("5h", item.actualEffort());
        assertEquals("2026/04/03", item.testingStartedDate());
    }

    @Test
    void list_shouldNormalizeLegacySucceededCodexFailureAsFailedAndNotRecorded() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeHistoryMapper intakeHistoryMapper = mock(IntakeHistoryMapper.class);
        AttachmentService attachmentService = mock(AttachmentService.class);
        IntakeService service = new IntakeService(
                intakeMapper,
                intakeHistoryMapper,
                attachmentService,
                new IntakeStructuredDataExtractor(),
                mock(IntakeEnrichmentService.class),
                mock(CodexCliSqlDraftGenerator.class),
                new ObjectMapper()
        );

        IntakeRecordEntity entity = new IntakeRecordEntity();
        entity.setId(103L);
        entity.setSourceType("需求截图附件录入");
        entity.setSourceChannel("需求录入");
        entity.setSenderName("admin");
        entity.setReceivedAt(LocalDateTime.of(2026, 4, 28, 15, 30));
        entity.setStructuredDataJson("""
                {"category":"需求审批","approvalTitle":null,"proposerName":"顾佳青","approvalCode":null,"submittedTime":null,"requirementType":"研发需求","requirementDigest":"趣学呗主体账户变更","requirementName":null,"requirementSummary":"趣学呗需要调整平台收款账户。","department":null,"businessLine":null,"remark":null,"estimatedEffort":null,"plannedDueDate":null,"actualEffort":null,"actualCompletedTime":null,"acceptanceTime":null,"projectHint":null,"fields":[],"attachmentSummaries":[]}
                """);
        entity.setDemandStatus("已收录");
        entity.setEnrichmentStatus("SUCCEEDED");
        entity.setEnrichmentErrorSummary("Codex CLI 执行失败");
        entity.setIntakeStatus("待整理");
        when(intakeMapper.findAll(eq(null))).thenReturn(List.of(entity));

        List<IntakeSummaryResponse> result = service.list(null, null, null, null, null, null, null);

        assertEquals(1, result.size());
        assertNull(result.get(0).demandStatus());
        assertEquals("FAILED", result.get(0).enrichmentStatus());
    }

    @Test
    void list_shouldFilterByReleasedDateRange() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeHistoryMapper intakeHistoryMapper = mock(IntakeHistoryMapper.class);
        AttachmentService attachmentService = mock(AttachmentService.class);
        IntakeService service = new IntakeService(
                intakeMapper,
                intakeHistoryMapper,
                attachmentService,
                new IntakeStructuredDataExtractor(),
                mock(IntakeEnrichmentService.class),
                mock(CodexCliSqlDraftGenerator.class),
                new ObjectMapper()
        );

        IntakeRecordEntity matched = new IntakeRecordEntity();
        matched.setId(201L);
        matched.setSourceType("需求截图附件录入");
        matched.setSourceChannel("需求录入");
        matched.setSenderName("admin");
        matched.setReceivedAt(LocalDateTime.of(2026, 4, 2, 10, 0));
        matched.setRawContent("上线时间筛选测试");
        matched.setStructuredDataJson("""
                {"category":"需求审批","approvalCode":"REL-001","submittedTime":"2026/4/1 10:00","requirementType":"研发需求","requirementName":"上线筛选命中","requirementDigest":"上线筛选命中","releasedTime":"2026/04/03","fields":[],"attachmentSummaries":[]}
                """);
        matched.setDemandStatus("已完成");
        matched.setEnrichmentStatus("SUCCEEDED");
        matched.setIntakeStatus("待整理");

        IntakeRecordEntity missed = new IntakeRecordEntity();
        missed.setId(202L);
        missed.setSourceType("需求截图附件录入");
        missed.setSourceChannel("需求录入");
        missed.setSenderName("admin");
        missed.setReceivedAt(LocalDateTime.of(2026, 4, 5, 10, 0));
        missed.setRawContent("上线时间筛选测试");
        missed.setStructuredDataJson("""
                {"category":"需求审批","approvalCode":"REL-002","submittedTime":"2026/4/1 10:00","requirementType":"研发需求","requirementName":"上线筛选未命中","requirementDigest":"上线筛选未命中","releasedTime":"2026/04/10 12:00","fields":[],"attachmentSummaries":[]}
                """);
        missed.setDemandStatus("已完成");
        missed.setEnrichmentStatus("SUCCEEDED");
        missed.setIntakeStatus("待整理");

        when(intakeMapper.findAll(eq(null))).thenReturn(List.of(matched, missed));

        List<IntakeSummaryResponse> result = service.list(null, null, null, null, null, "2026/04/01", "2026/04/05");

        assertEquals(1, result.size());
        assertEquals("REL-001", result.getFirst().approvalCode());
    }

    @Test
    void list_shouldFilterByRequirementNameOnly() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeService service = new IntakeService(
                intakeMapper,
                mock(IntakeHistoryMapper.class),
                mock(AttachmentService.class),
                new IntakeStructuredDataExtractor(),
                mock(IntakeEnrichmentService.class),
                mock(CodexCliSqlDraftGenerator.class),
                new ObjectMapper()
        );

        IntakeRecordEntity matched = new IntakeRecordEntity();
        matched.setId(301L);
        matched.setSourceType("需求截图附件录入");
        matched.setSourceChannel("需求录入");
        matched.setSenderName("admin");
        matched.setReceivedAt(LocalDateTime.of(2026, 4, 5, 10, 0));
        matched.setRawContent("需求名称筛选测试");
        matched.setStructuredDataJson("""
                {"category":"需求审批","approvalCode":"NAME-001","submittedTime":"2026/4/5 10:00","requirementType":"研发需求","requirementName":"沃橙绑卡核验规则","requirementDigest":"绑卡核验","fields":[],"attachmentSummaries":[]}
                """);
        matched.setDemandStatus("已收录");
        matched.setEnrichmentStatus("SUCCEEDED");
        matched.setIntakeStatus("待整理");

        IntakeRecordEntity missed = new IntakeRecordEntity();
        missed.setId(302L);
        missed.setSourceType("需求截图附件录入");
        missed.setSourceChannel("需求录入");
        missed.setSenderName("admin");
        missed.setReceivedAt(LocalDateTime.of(2026, 4, 5, 11, 0));
        missed.setRawContent("审批编号：沃橙；需求类型：研发需求");
        missed.setStructuredDataJson("""
                {"category":"需求审批","approvalCode":"沃橙-002","submittedTime":"2026/4/5 11:00","requirementType":"沃橙研发需求","requirementName":"还款接口调整","requirementDigest":"还款调整","fields":[],"attachmentSummaries":[]}
                """);
        missed.setDemandStatus("已收录");
        missed.setEnrichmentStatus("SUCCEEDED");
        missed.setIntakeStatus("待整理");

        when(intakeMapper.findAll(eq(null))).thenReturn(List.of(matched, missed));

        List<IntakeSummaryResponse> result = service.list(null, "绑卡", null, null, null, null, null);

        assertEquals(1, result.size());
        assertEquals("NAME-001", result.getFirst().approvalCode());
    }

    @Test
    void list_shouldFilterByApprovalCodeAndProposerName() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeService service = new IntakeService(
                intakeMapper,
                mock(IntakeHistoryMapper.class),
                mock(AttachmentService.class),
                new IntakeStructuredDataExtractor(),
                mock(IntakeEnrichmentService.class),
                mock(CodexCliSqlDraftGenerator.class),
                new ObjectMapper()
        );

        IntakeRecordEntity matched = new IntakeRecordEntity();
        matched.setId(401L);
        matched.setSourceType("需求截图附件录入");
        matched.setSourceChannel("需求录入");
        matched.setSenderName("admin");
        matched.setReceivedAt(LocalDateTime.of(2026, 4, 6, 10, 0));
        matched.setStructuredDataJson("""
                {"category":"需求审批","approvalCode":"202604060001","proposerName":"周拓","submittedTime":"2026/4/6 10:00","requirementType":"研发需求","requirementName":"匹配需求","requirementDigest":"匹配需求","fields":[],"attachmentSummaries":[]}
                """);
        matched.setDemandStatus("已收录");
        matched.setEnrichmentStatus("SUCCEEDED");
        matched.setIntakeStatus("待整理");

        IntakeRecordEntity missed = new IntakeRecordEntity();
        missed.setId(402L);
        missed.setSourceType("需求截图附件录入");
        missed.setSourceChannel("需求录入");
        missed.setSenderName("admin");
        missed.setReceivedAt(LocalDateTime.of(2026, 4, 6, 11, 0));
        missed.setStructuredDataJson("""
                {"category":"需求审批","approvalCode":"202604060002","proposerName":"顾佳青","submittedTime":"2026/4/6 11:00","requirementType":"研发需求","requirementName":"未匹配需求","requirementDigest":"未匹配需求","fields":[],"attachmentSummaries":[]}
                """);
        missed.setDemandStatus("已收录");
        missed.setEnrichmentStatus("SUCCEEDED");
        missed.setIntakeStatus("待整理");

        when(intakeMapper.findAll(eq(null))).thenReturn(List.of(matched, missed));

        List<IntakeSummaryResponse> result = service.list(null, null, "060001", "周", null, null, null);

        assertEquals(1, result.size());
        assertEquals("202604060001", result.getFirst().approvalCode());
        assertEquals("周拓", result.getFirst().proposerName());
    }

    @Test
    void delete_shouldMarkIntakeRecordDeleted() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeHistoryMapper intakeHistoryMapper = mock(IntakeHistoryMapper.class);
        AttachmentService attachmentService = mock(AttachmentService.class);
        IntakeStructuredDataExtractor intakeStructuredDataExtractor = new IntakeStructuredDataExtractor();
        IntakeEnrichmentService intakeEnrichmentService = mock(IntakeEnrichmentService.class);

        IntakeService service = new IntakeService(
                intakeMapper,
                intakeHistoryMapper,
                attachmentService,
                intakeStructuredDataExtractor,
                intakeEnrichmentService,
                mock(CodexCliSqlDraftGenerator.class),
                new ObjectMapper()
        );

        IntakeRecordEntity entity = new IntakeRecordEntity();
        entity.setId(301L);
        when(intakeMapper.findById(301L)).thenReturn(entity);

        service.delete(301L, "admin");

        verify(intakeMapper).markDeleted(eq(301L), any(), eq("admin"));
        verify(intakeHistoryMapper, never()).insert(any());
    }

    @Test
    void retryEnrichment_shouldRecordHistoryAndScheduleAsyncRetryForFailedIntake() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeHistoryMapper intakeHistoryMapper = mock(IntakeHistoryMapper.class);
        AttachmentService attachmentService = mock(AttachmentService.class);
        IntakeEnrichmentService intakeEnrichmentService = mock(IntakeEnrichmentService.class);
        IntakeService service = new IntakeService(
                intakeMapper,
                intakeHistoryMapper,
                attachmentService,
                new IntakeStructuredDataExtractor(),
                intakeEnrichmentService,
                mock(CodexCliSqlDraftGenerator.class),
                new ObjectMapper()
        );

        IntakeRecordEntity failed = new IntakeRecordEntity();
        failed.setId(501L);
        failed.setSourceType("需求截图附件录入");
        failed.setSourceChannel("需求录入");
        failed.setSenderName("admin");
        failed.setReceivedAt(LocalDateTime.of(2026, 5, 5, 10, 0));
        failed.setRawContent("审批编号：RETRY-001");
        failed.setEnrichmentStatus("FAILED");
        failed.setEnrichmentErrorSummary("Codex CLI 执行失败");
        failed.setIntakeStatus("待整理");

        when(intakeMapper.findById(501L)).thenReturn(failed);
        when(attachmentService.listIntakeAttachments(501L)).thenReturn(List.of());
        when(intakeHistoryMapper.findRecentByIntakeId(501L, 20)).thenReturn(List.of());

        service.retryEnrichment(501L, "admin");

        ArgumentCaptor<IntakeHistoryEntity> historyCaptor = ArgumentCaptor.forClass(IntakeHistoryEntity.class);
        verify(intakeHistoryMapper).insert(historyCaptor.capture());
        assertEquals("重试需求识别", historyCaptor.getValue().getActionSummary());
        assertTrue(historyCaptor.getValue().getDetailText().contains("Codex CLI 执行失败"));
        verify(intakeEnrichmentService).scheduleUploadedEnrichment(501L, "需求录入", "审批编号：RETRY-001");
    }

    @Test
    void retryEnrichment_shouldRejectRunningIntake() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeHistoryMapper intakeHistoryMapper = mock(IntakeHistoryMapper.class);
        AttachmentService attachmentService = mock(AttachmentService.class);
        IntakeEnrichmentService intakeEnrichmentService = mock(IntakeEnrichmentService.class);
        IntakeService service = new IntakeService(
                intakeMapper,
                intakeHistoryMapper,
                attachmentService,
                new IntakeStructuredDataExtractor(),
                intakeEnrichmentService,
                mock(CodexCliSqlDraftGenerator.class),
                new ObjectMapper()
        );

        IntakeRecordEntity running = new IntakeRecordEntity();
        running.setId(502L);
        running.setSourceType("需求截图附件录入");
        running.setSourceChannel("需求录入");
        running.setSenderName("admin");
        running.setReceivedAt(LocalDateTime.of(2026, 5, 5, 10, 0));
        running.setEnrichmentStatus("RUNNING");
        running.setIntakeStatus("待整理");
        when(intakeMapper.findById(502L)).thenReturn(running);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.retryEnrichment(502L, "admin"));

        assertEquals("当前需求不是识别失败状态，不能重新识别", error.getMessage());
        verify(intakeHistoryMapper, never()).insert(any());
        verify(intakeEnrichmentService, never()).scheduleUploadedEnrichment(any(), any(), any());
    }

    @Test
    void advanceStage_shouldRejectManualEvaluationStageAction() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeHistoryMapper intakeHistoryMapper = mock(IntakeHistoryMapper.class);
        AttachmentService attachmentService = mock(AttachmentService.class);
        IntakeStructuredDataExtractor intakeStructuredDataExtractor = new IntakeStructuredDataExtractor();
        IntakeEnrichmentService intakeEnrichmentService = mock(IntakeEnrichmentService.class);

        IntakeService service = new IntakeService(
                intakeMapper,
                intakeHistoryMapper,
                attachmentService,
                intakeStructuredDataExtractor,
                intakeEnrichmentService,
                mock(CodexCliSqlDraftGenerator.class),
                new ObjectMapper()
        );

        IntakeRecordEntity before = new IntakeRecordEntity();
        before.setId(201L);
        before.setSenderName("zhoutuo");
        before.setReceivedAt(LocalDateTime.of(2026, 4, 1, 10, 0));
        before.setStructuredDataJson("""
                {"category":"需求审批","approvalTitle":"周拓的系统开发2.0","proposerName":"周拓","approvalCode":"202603250009","submittedTime":"2026/3/25 16:17","requirementType":"研发需求","requirementDigest":"沃橙绑卡","requirementName":"沃橙绑卡需求","requirementSummary":"描述","department":"供应链业务部","businessLine":"供应链科技","remark":"高优","estimatedEffort":null,"plannedDueDate":null,"developmentStartedDate":null,"actualEffort":null,"testingStartedDate":null,"actualCompletedTime":null,"acceptanceTime":null,"releasedTime":null,"projectHint":"供应链科技","fields":[],"attachmentSummaries":[]}
                """);
        before.setDemandStatus("待评估");

        when(intakeMapper.findById(201L)).thenReturn(before);

        IntakeStageActionRequest request = new IntakeStageActionRequest();
        request.setAction("COMPLETE_EVALUATION");
        request.setPlannedDueDate("2026/04/05");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.advanceStage(201L, request, "admin"));

        assertEquals("待评估需求必须通过研发任务评估确认推进到待排期", error.getMessage());
        verify(intakeMapper, never()).updateManagementFields(any(), any(), any(), any());
        verify(intakeHistoryMapper, never()).insert(any());
    }

    @Test
    void advanceStage_shouldRejectInvalidEffortText() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeHistoryMapper intakeHistoryMapper = mock(IntakeHistoryMapper.class);
        AttachmentService attachmentService = mock(AttachmentService.class);
        IntakeService service = new IntakeService(
                intakeMapper,
                intakeHistoryMapper,
                attachmentService,
                new IntakeStructuredDataExtractor(),
                mock(IntakeEnrichmentService.class),
                mock(CodexCliSqlDraftGenerator.class),
                new ObjectMapper()
        );

        IntakeRecordEntity before = new IntakeRecordEntity();
        before.setId(204L);
        before.setSenderName("ops-user");
        before.setReceivedAt(LocalDateTime.of(2026, 4, 1, 10, 0));
        before.setStructuredDataJson("""
                {"category":"需求审批","approvalCode":"REQ-204","requirementType":"研发需求","requirementName":"研发需求","estimatedEffort":null,"plannedDueDate":null,"fields":[],"attachmentSummaries":[]}
                """);
        before.setDemandStatus("待评估");
        when(intakeMapper.findById(204L)).thenReturn(before);

        IntakeStageActionRequest request = new IntakeStageActionRequest();
        request.setAction("COMPLETE_EVALUATION");
        request.setPlannedDueDate("2026/04/05");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.advanceStage(204L, request, "admin"));

        assertEquals("待评估需求必须通过研发任务评估确认推进到待排期", error.getMessage());
        verify(intakeMapper, never()).updateManagementFields(any(), any(), any(), any());
        verify(intakeHistoryMapper, never()).insert(any());
    }

    @Test
    void advanceStage_shouldMoveOperationsDemandFromRecordedToPendingProcessing() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeHistoryMapper intakeHistoryMapper = mock(IntakeHistoryMapper.class);
        AttachmentService attachmentService = mock(AttachmentService.class);
        IntakeService service = new IntakeService(
                intakeMapper,
                intakeHistoryMapper,
                attachmentService,
                new IntakeStructuredDataExtractor(),
                mock(IntakeEnrichmentService.class),
                mock(CodexCliSqlDraftGenerator.class),
                new ObjectMapper()
        );

        IntakeRecordEntity before = new IntakeRecordEntity();
        before.setId(205L);
        before.setSenderName("ops-user");
        before.setReceivedAt(LocalDateTime.of(2026, 4, 1, 10, 0));
        before.setStructuredDataJson("""
                {"category":"需求审批","approvalCode":"REQ-205","requirementType":"数据提取/运维","requirementName":"导数需求","fields":[],"attachmentSummaries":[]}
                """);
        before.setDemandStatus("已收录");

        IntakeRecordEntity after = new IntakeRecordEntity();
        after.setId(205L);
        after.setSenderName("ops-user");
        after.setReceivedAt(LocalDateTime.of(2026, 4, 1, 10, 0));
        after.setStructuredDataJson(before.getStructuredDataJson());
        after.setDemandStatus("待处理");

        when(intakeMapper.findById(205L)).thenReturn(before, after);
        when(attachmentService.listIntakeAttachments(205L)).thenReturn(List.of());
        when(intakeHistoryMapper.findRecentByIntakeId(205L, 20)).thenReturn(List.of());

        IntakeStageActionRequest request = new IntakeStageActionRequest();
        request.setAction("CONFIRM_RECORDED");

        service.advanceStage(205L, request, "admin");

        verify(intakeMapper).updateManagementFields(eq(205L), any(), any(), eq("待处理"));
        ArgumentCaptor<IntakeHistoryEntity> historyCaptor = ArgumentCaptor.forClass(IntakeHistoryEntity.class);
        verify(intakeHistoryMapper).insert(historyCaptor.capture());
        assertEquals("确认收录", historyCaptor.getValue().getActionSummary());
    }

    @Test
    void advanceStage_shouldSkipAiClarificationWhenDisabled() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeHistoryMapper intakeHistoryMapper = mock(IntakeHistoryMapper.class);
        AttachmentService attachmentService = mock(AttachmentService.class);
        IntakeClarificationAnalysisService clarificationAnalysisService = mock(IntakeClarificationAnalysisService.class);
        IntakeService service = new IntakeService(
                intakeMapper,
                intakeHistoryMapper,
                mock(IntakeWorkItemRelationMapper.class),
                attachmentService,
                new IntakeStructuredDataExtractor(),
                mock(IntakeEnrichmentService.class),
                mock(CodexCliSqlDraftGenerator.class),
                clarificationAnalysisService,
                new ObjectMapper()
        );

        IntakeRecordEntity before = new IntakeRecordEntity();
        before.setId(207L);
        before.setSenderName("product-user");
        before.setReceivedAt(LocalDateTime.of(2026, 4, 1, 10, 0));
        before.setStructuredDataJson("""
                {"category":"需求审批","approvalCode":"REQ-207","requirementType":"研发需求","requirementName":"研发澄清需求","fields":[],"attachmentSummaries":[]}
                """);
        before.setDemandStatus("已收录");

        IntakeRecordEntity after = new IntakeRecordEntity();
        after.setId(207L);
        after.setSenderName("product-user");
        after.setReceivedAt(LocalDateTime.of(2026, 4, 1, 10, 0));
        after.setStructuredDataJson(before.getStructuredDataJson());
        after.setDemandStatus("待澄清");

        when(intakeMapper.findById(207L)).thenReturn(before, after);
        when(attachmentService.listIntakeAttachments(207L)).thenReturn(List.of());
        when(intakeHistoryMapper.findRecentByIntakeId(207L, 20)).thenReturn(List.of());

        IntakeStageActionRequest request = new IntakeStageActionRequest();
        request.setAction("START_CLARIFICATION");
        request.setAiClarificationEnabled(false);

        service.advanceStage(207L, request, "admin");

        verify(intakeMapper).updateManagementFields(eq(207L), any(), any(), eq("待澄清"));
        verify(clarificationAnalysisService, never()).analyze(eq(207L), eq("admin"));
    }

    @Test
    void advanceStage_shouldMoveOperationsDemandFromProcessingToPendingAcceptance() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeHistoryMapper intakeHistoryMapper = mock(IntakeHistoryMapper.class);
        AttachmentService attachmentService = mock(AttachmentService.class);
        IntakeService service = new IntakeService(
                intakeMapper,
                intakeHistoryMapper,
                attachmentService,
                new IntakeStructuredDataExtractor(),
                mock(IntakeEnrichmentService.class),
                mock(CodexCliSqlDraftGenerator.class),
                new ObjectMapper()
        );

        IntakeRecordEntity before = new IntakeRecordEntity();
        before.setId(206L);
        before.setSenderName("ops-user");
        before.setReceivedAt(LocalDateTime.of(2026, 4, 1, 10, 0));
        before.setStructuredDataJson("""
                {"category":"需求审批","approvalCode":"REQ-206","requirementType":"数据提取/运维","requirementName":"导数需求","actualEffort":null,"actualCompletedTime":null,"fields":[],"attachmentSummaries":[]}
                """);
        before.setDemandStatus("处理中");

        IntakeRecordEntity after = new IntakeRecordEntity();
        after.setId(206L);
        after.setSenderName("ops-user");
        after.setReceivedAt(LocalDateTime.of(2026, 4, 1, 10, 0));
        after.setStructuredDataJson("""
                {"category":"需求审批","approvalCode":"REQ-206","requirementType":"数据提取/运维","requirementName":"导数需求","actualEffort":"3h","actualCompletedTime":"2026/04/03","releasedTime":"2026/04/03","fields":[],"attachmentSummaries":[]}
                """);
        after.setDemandStatus("待验收");

        when(intakeMapper.findById(206L)).thenReturn(before, after);
        when(attachmentService.listIntakeAttachments(206L)).thenReturn(List.of());
        when(intakeHistoryMapper.findRecentByIntakeId(206L, 20)).thenReturn(List.of());

        IntakeStageActionRequest request = new IntakeStageActionRequest();
        request.setAction("SUBMIT_ACCEPTANCE");
        request.setActualEffort("3h");
        request.setActualCompletedTime("2026/04/03");

        service.advanceStage(206L, request, "admin");

        ArgumentCaptor<String> structuredJsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(intakeMapper).updateManagementFields(eq(206L), structuredJsonCaptor.capture(), any(), eq("待验收"));
        assertTrue(structuredJsonCaptor.getValue().contains("\"actualEffort\":\"3h\""));
        assertTrue(structuredJsonCaptor.getValue().contains("\"actualCompletedTime\":\"2026/04/03\""));
        assertTrue(structuredJsonCaptor.getValue().contains("\"releasedTime\":\"2026/04/03\""));
    }

    @Test
    void advanceStage_shouldConfirmDesignWithoutDevelopmentOwnerInJson() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeHistoryMapper intakeHistoryMapper = mock(IntakeHistoryMapper.class);
        AttachmentService attachmentService = mock(AttachmentService.class);
        IntakeStructuredDataExtractor intakeStructuredDataExtractor = new IntakeStructuredDataExtractor();
        IntakeEnrichmentService intakeEnrichmentService = mock(IntakeEnrichmentService.class);

        IntakeService service = new IntakeService(
                intakeMapper,
                intakeHistoryMapper,
                attachmentService,
                intakeStructuredDataExtractor,
                intakeEnrichmentService,
                mock(CodexCliSqlDraftGenerator.class),
                new ObjectMapper()
        );

        IntakeRecordEntity before = new IntakeRecordEntity();
        before.setId(211L);
        before.setSenderName("zhoutuo");
        before.setReceivedAt(LocalDateTime.of(2026, 4, 1, 10, 0));
        before.setStructuredDataJson("""
                {"category":"需求审批","approvalTitle":"周拓的系统开发2.0","proposerName":"周拓","approvalCode":"202603250009","submittedTime":"2026/3/25 16:17","requirementType":"研发需求","requirementDigest":"沃橙绑卡","requirementName":"沃橙绑卡需求","requirementSummary":"描述","department":"供应链业务部","businessLine":"供应链科技","remark":"高优","estimatedEffort":"2d","plannedDueDate":"2026/04/05","developmentStartedDate":null,"actualEffort":null,"testingStartedDate":null,"actualCompletedTime":null,"acceptanceTime":null,"releasedTime":null,"projectHint":"供应链科技","fields":[],"attachmentSummaries":[]}
                """);
        before.setDemandStatus("待设计");

        IntakeRecordEntity after = new IntakeRecordEntity();
        after.setId(211L);
        after.setSenderName("zhoutuo");
        after.setReceivedAt(LocalDateTime.of(2026, 4, 1, 10, 0));
        after.setStructuredDataJson("""
                {"category":"需求审批","approvalTitle":"周拓的系统开发2.0","proposerName":"周拓","approvalCode":"202603250009","submittedTime":"2026/3/25 16:17","requirementType":"研发需求","requirementDigest":"沃橙绑卡","requirementName":"沃橙绑卡需求","requirementSummary":"描述","department":"供应链业务部","businessLine":"供应链科技","remark":"高优","estimatedEffort":"2d","plannedDueDate":"2026/04/05","developmentStartedDate":"2026/04/06","actualEffort":null,"testingStartedDate":null,"actualCompletedTime":null,"acceptanceTime":null,"releasedTime":null,"projectHint":"供应链科技","fields":[],"attachmentSummaries":[]}
                """);
        after.setDemandStatus("开发中");

        when(intakeMapper.findById(211L)).thenReturn(before, after);
        when(attachmentService.listIntakeAttachments(211L)).thenReturn(List.of());
        when(intakeHistoryMapper.findRecentByIntakeId(211L, 20)).thenReturn(List.of());

        IntakeStageActionRequest request = new IntakeStageActionRequest();
        request.setAction("CONFIRM_DESIGN");
        request.setOccurredAt("2026/04/06");

        service.advanceStage(211L, request, "admin");

        ArgumentCaptor<String> structuredJsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(intakeMapper).updateManagementFields(eq(211L), structuredJsonCaptor.capture(), any(), eq("开发中"));
        assertTrue(structuredJsonCaptor.getValue().contains("\"developmentOwnerUserName\":null"));
        assertTrue(structuredJsonCaptor.getValue().contains("\"developmentStartedDate\":\"2026/04/06\""));
        ArgumentCaptor<IntakeHistoryEntity> historyCaptor = ArgumentCaptor.forClass(IntakeHistoryEntity.class);
        verify(intakeHistoryMapper).insert(historyCaptor.capture());
        assertTrue(!historyCaptor.getValue().getDetailText().contains("研发人员："));
    }

    @Test
    void advanceStage_shouldSaveScheduleDatesWhenConfirmScheduling() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeHistoryMapper intakeHistoryMapper = mock(IntakeHistoryMapper.class);
        AttachmentService attachmentService = mock(AttachmentService.class);
        IntakeService service = new IntakeService(
                intakeMapper,
                intakeHistoryMapper,
                attachmentService,
                new IntakeStructuredDataExtractor(),
                mock(IntakeEnrichmentService.class),
                mock(CodexCliSqlDraftGenerator.class),
                new ObjectMapper()
        );

        IntakeRecordEntity before = new IntakeRecordEntity();
        before.setId(212L);
        before.setSenderName("product-user");
        before.setReceivedAt(LocalDateTime.of(2026, 4, 1, 10, 0));
        before.setStructuredDataJson("""
                {"category":"需求审批","approvalCode":"REQ-212","requirementType":"研发需求","requirementName":"排期需求","plannedDevelopmentStartDate":null,"plannedTestingStartDate":null,"plannedReleaseDate":null,"fields":[],"attachmentSummaries":[]}
                """);
        before.setDemandStatus("待排期");

        IntakeRecordEntity after = new IntakeRecordEntity();
        after.setId(212L);
        after.setSenderName("product-user");
        after.setReceivedAt(LocalDateTime.of(2026, 4, 1, 10, 0));
        after.setStructuredDataJson("""
                {"category":"需求审批","approvalCode":"REQ-212","requirementType":"研发需求","requirementName":"排期需求","plannedDevelopmentStartDate":"2026/04/08","plannedTestingStartDate":"2026/04/09","plannedReleaseDate":"2026/04/12","fields":[],"attachmentSummaries":[]}
                """);
        after.setDemandStatus("待设计");

        when(intakeMapper.findById(212L)).thenReturn(before, after);
        when(attachmentService.listIntakeAttachments(212L)).thenReturn(List.of());
        when(intakeHistoryMapper.findRecentByIntakeId(212L, 20)).thenReturn(List.of());

        IntakeStageActionRequest request = new IntakeStageActionRequest();
        request.setAction("CONFIRM_SCHEDULING");
        request.setPlannedDevelopmentStartDate("2026-04-08");
        request.setPlannedTestingStartDate("2026-04-09");
        request.setPlannedReleaseDate("2026/04/12");

        service.advanceStage(212L, request, "admin");

        ArgumentCaptor<String> structuredJsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(intakeMapper).updateManagementFields(eq(212L), structuredJsonCaptor.capture(), any(), eq("待设计"));
        assertTrue(structuredJsonCaptor.getValue().contains("\"plannedDevelopmentStartDate\":\"2026/04/08\""));
        assertTrue(structuredJsonCaptor.getValue().contains("\"plannedTestingStartDate\":\"2026/04/09\""));
        assertTrue(structuredJsonCaptor.getValue().contains("\"plannedReleaseDate\":\"2026/04/12\""));
        ArgumentCaptor<IntakeHistoryEntity> historyCaptor = ArgumentCaptor.forClass(IntakeHistoryEntity.class);
        verify(intakeHistoryMapper).insert(historyCaptor.capture());
        assertTrue(historyCaptor.getValue().getDetailText().contains("预估开发日期：- -> 2026/04/08"));
        assertTrue(historyCaptor.getValue().getDetailText().contains("预估提测日期：- -> 2026/04/09"));
        assertTrue(historyCaptor.getValue().getDetailText().contains("预估上线日期：- -> 2026/04/12"));
    }

    @Test
    void advanceStage_shouldAllowOptionalScheduleDatesWhenConfirmScheduling() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeHistoryMapper intakeHistoryMapper = mock(IntakeHistoryMapper.class);
        AttachmentService attachmentService = mock(AttachmentService.class);
        IntakeService service = new IntakeService(
                intakeMapper,
                intakeHistoryMapper,
                attachmentService,
                new IntakeStructuredDataExtractor(),
                mock(IntakeEnrichmentService.class),
                mock(CodexCliSqlDraftGenerator.class),
                new ObjectMapper()
        );

        IntakeRecordEntity before = new IntakeRecordEntity();
        before.setId(213L);
        before.setStructuredDataJson("""
                {"category":"需求审批","approvalCode":"REQ-213","requirementType":"研发需求","requirementName":"排期需求","fields":[],"attachmentSummaries":[]}
                """);
        before.setDemandStatus("待排期");

        IntakeRecordEntity after = new IntakeRecordEntity();
        after.setId(213L);
        after.setStructuredDataJson("""
                {"category":"需求审批","approvalCode":"REQ-213","requirementType":"研发需求","requirementName":"排期需求","plannedTestingStartDate":"2026/04/09","plannedReleaseDate":null,"fields":[],"attachmentSummaries":[]}
                """);
        after.setDemandStatus("待设计");

        when(intakeMapper.findById(213L)).thenReturn(before, after);
        when(attachmentService.listIntakeAttachments(213L)).thenReturn(List.of());
        when(intakeHistoryMapper.findRecentByIntakeId(213L, 20)).thenReturn(List.of());

        IntakeStageActionRequest request = new IntakeStageActionRequest();
        request.setAction("CONFIRM_SCHEDULING");
        request.setPlannedTestingStartDate("2026/04/09");

        service.advanceStage(213L, request, "admin");

        ArgumentCaptor<String> structuredJsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(intakeMapper).updateManagementFields(eq(213L), structuredJsonCaptor.capture(), any(), eq("待设计"));
        assertTrue(structuredJsonCaptor.getValue().contains("\"plannedTestingStartDate\":\"2026/04/09\""));
        assertTrue(structuredJsonCaptor.getValue().contains("\"plannedReleaseDate\":null"));
    }

    @Test
    void advanceStage_shouldRejectReleaseDateBeforeTestingDateWhenConfirmScheduling() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeService service = new IntakeService(
                intakeMapper,
                mock(IntakeHistoryMapper.class),
                mock(AttachmentService.class),
                new IntakeStructuredDataExtractor(),
                mock(IntakeEnrichmentService.class),
                mock(CodexCliSqlDraftGenerator.class),
                new ObjectMapper()
        );

        IntakeRecordEntity before = new IntakeRecordEntity();
        before.setId(214L);
        before.setStructuredDataJson("""
                {"category":"需求审批","approvalCode":"REQ-214","requirementType":"研发需求","requirementName":"排期需求","fields":[],"attachmentSummaries":[]}
                """);
        before.setDemandStatus("待排期");
        when(intakeMapper.findById(214L)).thenReturn(before);

        IntakeStageActionRequest request = new IntakeStageActionRequest();
        request.setAction("CONFIRM_SCHEDULING");
        request.setPlannedTestingStartDate("2026/04/09");
        request.setPlannedReleaseDate("2026/04/08");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.advanceStage(214L, request, "admin"));

        assertEquals("预估上线日期不能早于预估提测日期", error.getMessage());
    }

    @Test
    void advanceStage_shouldConfirmAcceptanceAndMoveToPendingRelease() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeHistoryMapper intakeHistoryMapper = mock(IntakeHistoryMapper.class);
        AttachmentService attachmentService = mock(AttachmentService.class);
        IntakeStructuredDataExtractor intakeStructuredDataExtractor = new IntakeStructuredDataExtractor();
        IntakeEnrichmentService intakeEnrichmentService = mock(IntakeEnrichmentService.class);

        IntakeService service = new IntakeService(
                intakeMapper,
                intakeHistoryMapper,
                attachmentService,
                intakeStructuredDataExtractor,
                intakeEnrichmentService,
                mock(CodexCliSqlDraftGenerator.class),
                new ObjectMapper()
        );

        IntakeRecordEntity before = new IntakeRecordEntity();
        before.setId(202L);
        before.setSenderName("product-user");
        before.setReceivedAt(LocalDateTime.of(2026, 4, 2, 10, 0));
        before.setStructuredDataJson("""
                {"category":"需求审批","approvalTitle":"接口上线验收","proposerName":"产品同学","approvalCode":"REQ-001","submittedTime":"2026/4/2 10:00","requirementType":"研发需求","requirementDigest":"接口上线验收","requirementName":"接口上线验收","requirementSummary":"描述","department":"产品部","businessLine":"资产业务","remark":"普通","estimatedEffort":"4h","plannedDueDate":"2026/04/02","developmentStartedDate":"2026/04/02","actualEffort":"5h","testingStartedDate":"2026/04/03","actualCompletedTime":"2026/04/04","acceptanceTime":null,"releasedTime":null,"projectHint":"资产业务","fields":[],"attachmentSummaries":[]}
                """);
        before.setDemandStatus("待验收");

        IntakeRecordEntity after = new IntakeRecordEntity();
        after.setId(202L);
        after.setSenderName("product-user");
        after.setReceivedAt(LocalDateTime.of(2026, 4, 2, 10, 0));
        after.setStructuredDataJson("""
                {"category":"需求审批","approvalTitle":"接口上线验收","proposerName":"产品同学","approvalCode":"REQ-001","submittedTime":"2026/4/2 10:00","requirementType":"研发需求","requirementDigest":"接口上线验收","requirementName":"接口上线验收","requirementSummary":"描述","department":"产品部","businessLine":"资产业务","remark":"普通","estimatedEffort":"4h","plannedDueDate":"2026/04/02","developmentStartedDate":"2026/04/02","actualEffort":"5h","testingStartedDate":"2026/04/03","actualCompletedTime":"2026/04/04","acceptanceTime":"2026/04/06","releasedTime":null,"projectHint":"资产业务","fields":[],"attachmentSummaries":[]}
                """);
        after.setDemandStatus("待上线");

        when(intakeMapper.findById(202L)).thenReturn(before, after);
        when(attachmentService.listIntakeAttachments(202L)).thenReturn(List.of());
        when(intakeHistoryMapper.findRecentByIntakeId(202L, 20)).thenReturn(List.of());

        IntakeStageActionRequest request = new IntakeStageActionRequest();
        request.setAction("CONFIRM_ACCEPTANCE");
        request.setAcceptanceTime("2026/04/06");
        request.setOccurredAt("2026/04/06");

        service.advanceStage(202L, request, "admin");

        ArgumentCaptor<String> structuredJsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(intakeMapper).updateManagementFields(eq(202L), structuredJsonCaptor.capture(), any(), eq("待上线"));
        assertTrue(structuredJsonCaptor.getValue().contains("\"acceptanceTime\":\"2026/04/06\""));
        verify(intakeHistoryMapper).insert(any());
        verify(intakeHistoryMapper).findRecentByIntakeId(202L, 20);
    }

    @Test
    void advanceStage_shouldSubmitTestingAndRecordActualTestingDate() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeHistoryMapper intakeHistoryMapper = mock(IntakeHistoryMapper.class);
        AttachmentService attachmentService = mock(AttachmentService.class);
        IntakeStructuredDataExtractor intakeStructuredDataExtractor = new IntakeStructuredDataExtractor();
        IntakeEnrichmentService intakeEnrichmentService = mock(IntakeEnrichmentService.class);

        IntakeService service = new IntakeService(
                intakeMapper,
                intakeHistoryMapper,
                attachmentService,
                intakeStructuredDataExtractor,
                intakeEnrichmentService,
                mock(CodexCliSqlDraftGenerator.class),
                new ObjectMapper()
        );

        IntakeRecordEntity before = new IntakeRecordEntity();
        before.setId(213L);
        before.setSenderName("tester");
        before.setReceivedAt(LocalDateTime.of(2026, 4, 8, 10, 0));
        before.setStructuredDataJson("""
                {"category":"需求审批","approvalTitle":"开发完成提交测试","proposerName":"产品同学","approvalCode":"REQ-213","submittedTime":"2026/4/8 10:00","requirementType":"研发需求","requirementDigest":"提交测试","requirementName":"提交测试需求","requirementSummary":"描述","department":"产品部","businessLine":"资产业务","remark":"普通","plannedDueDate":"2026/04/08","developmentStartedDate":"2026/04/08","actualEffort":null,"testingStartedDate":null,"actualCompletedTime":null,"acceptanceTime":null,"releasedTime":null,"projectHint":"资产业务","fields":[],"attachmentSummaries":[]}
                """);
        before.setDemandStatus("开发中");

        IntakeRecordEntity after = new IntakeRecordEntity();
        after.setId(213L);
        after.setSenderName("tester");
        after.setReceivedAt(LocalDateTime.of(2026, 4, 8, 10, 0));
        after.setStructuredDataJson("""
                {"category":"需求审批","approvalTitle":"开发完成提交测试","proposerName":"产品同学","approvalCode":"REQ-213","submittedTime":"2026/4/8 10:00","requirementType":"研发需求","requirementDigest":"提交测试","requirementName":"提交测试需求","requirementSummary":"描述","department":"产品部","businessLine":"资产业务","remark":"普通","plannedDueDate":"2026/04/08","developmentStartedDate":"2026/04/08","actualEffort":"4h","testingStartedDate":"2026/04/09","actualCompletedTime":"2026/04/09","acceptanceTime":null,"releasedTime":null,"projectHint":"资产业务","fields":[],"attachmentSummaries":[]}
                """);
        after.setDemandStatus("测试中");

        when(intakeMapper.findById(213L)).thenReturn(before, after);
        when(attachmentService.listIntakeAttachments(213L)).thenReturn(List.of());
        when(intakeHistoryMapper.findRecentByIntakeId(213L, 20)).thenReturn(List.of());

        IntakeStageActionRequest request = new IntakeStageActionRequest();
        request.setAction("SUBMIT_TESTING");
        request.setActualEffort("4h");
        request.setActualCompletedTime("2026/04/09");
        request.setOccurredAt("2026/04/09");

        service.advanceStage(213L, request, "admin");

        ArgumentCaptor<String> structuredJsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(intakeMapper).updateManagementFields(eq(213L), structuredJsonCaptor.capture(), any(), eq("测试中"));
        assertTrue(structuredJsonCaptor.getValue().contains("\"testingStartedDate\":\"2026/04/09\""));
        ArgumentCaptor<IntakeHistoryEntity> historyCaptor = ArgumentCaptor.forClass(IntakeHistoryEntity.class);
        verify(intakeHistoryMapper).insert(historyCaptor.capture());
        assertEquals("提交测试", historyCaptor.getValue().getActionSummary());
        assertTrue(historyCaptor.getValue().getDetailText().contains("实际提测日期：- -> 2026/04/09"));
    }

    @Test
    void advanceStage_shouldPassTestingAndMoveToPendingAcceptance() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeHistoryMapper intakeHistoryMapper = mock(IntakeHistoryMapper.class);
        AttachmentService attachmentService = mock(AttachmentService.class);
        IntakeStructuredDataExtractor intakeStructuredDataExtractor = new IntakeStructuredDataExtractor();
        IntakeEnrichmentService intakeEnrichmentService = mock(IntakeEnrichmentService.class);

        IntakeService service = new IntakeService(
                intakeMapper,
                intakeHistoryMapper,
                attachmentService,
                intakeStructuredDataExtractor,
                intakeEnrichmentService,
                mock(CodexCliSqlDraftGenerator.class),
                new ObjectMapper()
        );

        IntakeRecordEntity before = new IntakeRecordEntity();
        before.setId(212L);
        before.setSenderName("tester");
        before.setReceivedAt(LocalDateTime.of(2026, 4, 8, 10, 0));
        before.setStructuredDataJson("""
                {"category":"需求审批","approvalTitle":"测试完成提交验收","proposerName":"产品同学","approvalCode":"REQ-212","submittedTime":"2026/4/8 10:00","requirementType":"研发需求","requirementDigest":"提交验收","requirementName":"提交验收需求","requirementSummary":"描述","department":"产品部","businessLine":"资产业务","remark":"普通","estimatedEffort":"4h","plannedDueDate":"2026/04/08","developmentStartedDate":"2026/04/08","actualEffort":"4h","testingStartedDate":"2026/04/09","actualCompletedTime":"2026/04/09","acceptanceTime":null,"releasedTime":null,"projectHint":"资产业务","fields":[],"attachmentSummaries":[]}
                """);
        before.setDemandStatus("测试中");

        IntakeRecordEntity after = new IntakeRecordEntity();
        after.setId(212L);
        after.setSenderName("tester");
        after.setReceivedAt(LocalDateTime.of(2026, 4, 8, 10, 0));
        after.setStructuredDataJson("""
                {"category":"需求审批","approvalTitle":"测试完成提交验收","proposerName":"产品同学","approvalCode":"REQ-212","submittedTime":"2026/4/8 10:00","requirementType":"研发需求","requirementDigest":"提交验收","requirementName":"提交验收需求","requirementSummary":"描述","department":"产品部","businessLine":"资产业务","remark":"普通","estimatedEffort":"4h","plannedDueDate":"2026/04/08","developmentStartedDate":"2026/04/08","actualEffort":"4h","testingStartedDate":"2026/04/09","actualCompletedTime":"2026/04/09","scheduledAcceptanceDate":"2026/04/11","actualTestingEffort":"2h","actualTestingCompletedDate":"2026/04/10","acceptanceTime":null,"releasedTime":null,"projectHint":"资产业务","fields":[],"attachmentSummaries":[]}
                """);
        after.setDemandStatus("待验收");

        when(intakeMapper.findById(212L)).thenReturn(before, after);
        when(attachmentService.listIntakeAttachments(212L)).thenReturn(List.of());
        when(intakeHistoryMapper.findRecentByIntakeId(212L, 20)).thenReturn(List.of());

        IntakeStageActionRequest request = new IntakeStageActionRequest();
        request.setAction("PASS_TESTING");
        request.setScheduledAcceptanceDate("2026/04/11");
        request.setActualTestingEffort("2h");
        request.setActualTestingCompletedDate("2026/04/10");

        service.advanceStage(212L, request, "admin");

        ArgumentCaptor<String> structuredJsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(intakeMapper).updateManagementFields(eq(212L), structuredJsonCaptor.capture(), any(), eq("待验收"));
        assertTrue(structuredJsonCaptor.getValue().contains("\"testingStartedDate\":\"2026/04/09\""));
        assertTrue(structuredJsonCaptor.getValue().contains("\"scheduledAcceptanceDate\":\"2026/04/11\""));
        assertTrue(structuredJsonCaptor.getValue().contains("\"actualTestingEffort\":\"2h\""));
        assertTrue(structuredJsonCaptor.getValue().contains("\"actualTestingCompletedDate\":\"2026/04/10\""));
        ArgumentCaptor<IntakeHistoryEntity> historyCaptor = ArgumentCaptor.forClass(IntakeHistoryEntity.class);
        verify(intakeHistoryMapper).insert(historyCaptor.capture());
        assertEquals("测试通过", historyCaptor.getValue().getActionSummary());
        assertTrue(historyCaptor.getValue().getDetailText().contains("需求状态：测试中 -> 待验收"));
    }

    @Test
    void advanceStage_shouldConfirmReleaseAndRecordReleaseDate() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeHistoryMapper intakeHistoryMapper = mock(IntakeHistoryMapper.class);
        AttachmentService attachmentService = mock(AttachmentService.class);
        IntakeStructuredDataExtractor intakeStructuredDataExtractor = new IntakeStructuredDataExtractor();
        IntakeEnrichmentService intakeEnrichmentService = mock(IntakeEnrichmentService.class);

        IntakeService service = new IntakeService(
                intakeMapper,
                intakeHistoryMapper,
                attachmentService,
                intakeStructuredDataExtractor,
                intakeEnrichmentService,
                mock(CodexCliSqlDraftGenerator.class),
                new ObjectMapper()
        );

        IntakeRecordEntity before = new IntakeRecordEntity();
        before.setId(214L);
        before.setSenderName("release-user");
        before.setReceivedAt(LocalDateTime.of(2026, 4, 10, 10, 0));
        before.setStructuredDataJson("""
                {"category":"需求审批","approvalTitle":"确认上线测试","proposerName":"产品同学","approvalCode":"REQ-214","submittedTime":"2026/4/10 10:00","requirementType":"研发需求","requirementDigest":"确认上线","requirementName":"确认上线测试","requirementSummary":"描述","department":"产品部","businessLine":"资产业务","remark":"普通","plannedDueDate":"2026/04/10","developmentStartedDate":"2026/04/08","actualEffort":"4h","testingStartedDate":"2026/04/09","actualCompletedTime":"2026/04/09","acceptanceTime":null,"releasedTime":null,"projectHint":"资产业务","fields":[],"attachmentSummaries":[]}
                """);
        before.setDemandStatus("待上线");

        IntakeRecordEntity after = new IntakeRecordEntity();
        after.setId(214L);
        after.setSenderName("release-user");
        after.setReceivedAt(LocalDateTime.of(2026, 4, 10, 10, 0));
        after.setStructuredDataJson("""
                {"category":"需求审批","approvalTitle":"确认上线测试","proposerName":"产品同学","approvalCode":"REQ-214","submittedTime":"2026/4/10 10:00","requirementType":"研发需求","requirementDigest":"确认上线","requirementName":"确认上线测试","requirementSummary":"描述","department":"产品部","businessLine":"资产业务","remark":"普通","plannedDueDate":"2026/04/10","developmentStartedDate":"2026/04/08","actualEffort":"4h","testingStartedDate":"2026/04/09","actualCompletedTime":"2026/04/09","acceptanceTime":null,"releasedTime":"2026/04/11","projectHint":"资产业务","fields":[],"attachmentSummaries":[]}
                """);
        after.setDemandStatus("已完成");

        when(intakeMapper.findById(214L)).thenReturn(before, after);
        when(attachmentService.listIntakeAttachments(214L)).thenReturn(List.of());
        when(intakeHistoryMapper.findRecentByIntakeId(214L, 20)).thenReturn(List.of());

        IntakeStageActionRequest request = new IntakeStageActionRequest();
        request.setAction("CONFIRM_RELEASE");
        request.setOccurredAt("2026/04/11");

        service.advanceStage(214L, request, "admin");

        ArgumentCaptor<String> structuredJsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(intakeMapper).updateManagementFields(eq(214L), structuredJsonCaptor.capture(), any(), eq("已完成"));
        assertTrue(structuredJsonCaptor.getValue().contains("\"releasedTime\":\"2026/04/11\""));
        ArgumentCaptor<IntakeHistoryEntity> historyCaptor = ArgumentCaptor.forClass(IntakeHistoryEntity.class);
        verify(intakeHistoryMapper).insert(historyCaptor.capture());
        assertEquals("确认上线", historyCaptor.getValue().getActionSummary());
        assertTrue(historyCaptor.getValue().getDetailText().contains("实际上线日期：- -> 2026/04/11"));
    }

    @Test
    void advanceStage_shouldCloseDemandWithReason() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeHistoryMapper intakeHistoryMapper = mock(IntakeHistoryMapper.class);
        AttachmentService attachmentService = mock(AttachmentService.class);
        IntakeStructuredDataExtractor intakeStructuredDataExtractor = new IntakeStructuredDataExtractor();
        IntakeEnrichmentService intakeEnrichmentService = mock(IntakeEnrichmentService.class);

        IntakeService service = new IntakeService(
                intakeMapper,
                intakeHistoryMapper,
                attachmentService,
                intakeStructuredDataExtractor,
                intakeEnrichmentService,
                mock(CodexCliSqlDraftGenerator.class),
                new ObjectMapper()
        );

        IntakeRecordEntity before = new IntakeRecordEntity();
        before.setId(203L);
        before.setSenderName("product-user");
        before.setReceivedAt(LocalDateTime.of(2026, 4, 3, 10, 0));
        before.setStructuredDataJson("""
                {"category":"需求审批","approvalTitle":"接口改造申请","proposerName":"产品同学","approvalCode":"REQ-203","submittedTime":"2026/4/3 10:00","requirementType":"研发需求","requirementDigest":"接口改造","requirementName":"接口改造申请","requirementSummary":"描述","department":"产品部","businessLine":"资产业务","remark":"普通","estimatedEffort":"4h","plannedDueDate":"2026/04/08","developmentStartedDate":null,"actualEffort":null,"testingStartedDate":null,"actualCompletedTime":null,"acceptanceTime":null,"releasedTime":null,"projectHint":"资产业务","fields":[],"attachmentSummaries":[]}
                """);
        before.setDemandStatus("待排期");

        IntakeRecordEntity after = new IntakeRecordEntity();
        after.setId(203L);
        after.setSenderName("product-user");
        after.setReceivedAt(LocalDateTime.of(2026, 4, 3, 10, 0));
        after.setStructuredDataJson("""
                {"category":"需求审批","approvalTitle":"接口改造申请","proposerName":"产品同学","approvalCode":"REQ-203","submittedTime":"2026/4/3 10:00","requirementType":"研发需求","requirementDigest":"接口改造","requirementName":"接口改造申请","requirementSummary":"描述","department":"产品部","businessLine":"资产业务","remark":"普通","estimatedEffort":"4h","plannedDueDate":"2026/04/08","developmentStartedDate":null,"actualEffort":null,"testingStartedDate":null,"actualCompletedTime":null,"acceptanceTime":null,"releasedTime":null,"closedTime":"2026/04/04","closeReason":"业务取消","projectHint":"资产业务","fields":[],"attachmentSummaries":[]}
                """);
        after.setDemandStatus("终止关闭");

        when(intakeMapper.findById(203L)).thenReturn(before, after);
        when(attachmentService.listIntakeAttachments(203L)).thenReturn(List.of());
        when(intakeHistoryMapper.findRecentByIntakeId(203L, 20)).thenReturn(List.of());

        IntakeStageActionRequest request = new IntakeStageActionRequest();
        request.setAction("CLOSE_REQUIREMENT");
        request.setCloseReason("业务取消");
        request.setOccurredAt("2026/04/04");

        service.advanceStage(203L, request, "admin");

        ArgumentCaptor<String> structuredJsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(intakeMapper).updateManagementFields(eq(203L), structuredJsonCaptor.capture(), any(), eq("终止关闭"));
        assertTrue(structuredJsonCaptor.getValue().contains("\"closedTime\":\"2026/04/04\""));
        assertTrue(structuredJsonCaptor.getValue().contains("\"closeReason\":\"业务取消\""));
        ArgumentCaptor<IntakeHistoryEntity> historyCaptor = ArgumentCaptor.forClass(IntakeHistoryEntity.class);
        verify(intakeHistoryMapper).insert(historyCaptor.capture());
        assertEquals("关闭需求", historyCaptor.getValue().getActionSummary());
        assertTrue(historyCaptor.getValue().getDetailText().contains("关闭原因：- -> 业务取消"));
    }

    @Test
    void detail_shouldNormalizeLegacyRequirementTypeGenerateBranchAndBackfillProposer() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeHistoryMapper intakeHistoryMapper = mock(IntakeHistoryMapper.class);
        AttachmentService attachmentService = mock(AttachmentService.class);
        IntakeStructuredDataExtractor intakeStructuredDataExtractor = new IntakeStructuredDataExtractor();
        IntakeEnrichmentService intakeEnrichmentService = mock(IntakeEnrichmentService.class);

        IntakeService service = new IntakeService(
                intakeMapper,
                intakeHistoryMapper,
                attachmentService,
                intakeStructuredDataExtractor,
                intakeEnrichmentService,
                mock(CodexCliSqlDraftGenerator.class),
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
        assertEquals("feature/liyi_ebike_split_202603240005", detail.structuredData().developmentBranchName());
    }

    @Test
    void updateZentaoLink_shouldPersistUrlAndRecordHistory() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeHistoryMapper intakeHistoryMapper = mock(IntakeHistoryMapper.class);
        AttachmentService attachmentService = mock(AttachmentService.class);
        IntakeStructuredDataExtractor intakeStructuredDataExtractor = new IntakeStructuredDataExtractor();
        IntakeEnrichmentService intakeEnrichmentService = mock(IntakeEnrichmentService.class);

        IntakeService service = new IntakeService(
                intakeMapper,
                intakeHistoryMapper,
                attachmentService,
                intakeStructuredDataExtractor,
                intakeEnrichmentService,
                mock(CodexCliSqlDraftGenerator.class),
                new ObjectMapper()
        );

        IntakeRecordEntity before = new IntakeRecordEntity();
        before.setId(401L);
        before.setSenderName("admin");
        before.setReceivedAt(LocalDateTime.of(2026, 4, 1, 10, 0));
        before.setStructuredDataJson("""
                {"category":"需求审批","approvalTitle":"周拓的系统开发2.0","proposerName":"周拓","approvalCode":"202603250009","submittedTime":"2026/3/25 16:17","requirementType":"研发需求","developmentBranchName":"feature/req-202603250009","zentaoUrl":null,"requirementDigest":"沃橙绑卡","requirementName":"沃橙绑卡需求","requirementSummary":"描述","department":"供应链业务部","businessLine":"供应链科技","remark":"高优","estimatedEffort":"2d","plannedDueDate":"2026/04/05","developmentStartedDate":null,"actualEffort":null,"testingStartedDate":null,"actualCompletedTime":null,"acceptanceTime":null,"releasedTime":null,"projectHint":"供应链科技","fields":[],"attachmentSummaries":[]}
                """);
        before.setDemandStatus("待排期");
        before.setEnrichmentStatus("SUCCEEDED");
        before.setIntakeStatus("待整理");

        IntakeRecordEntity after = new IntakeRecordEntity();
        after.setId(401L);
        after.setSenderName("admin");
        after.setReceivedAt(LocalDateTime.of(2026, 4, 1, 10, 0));
        after.setStructuredDataJson("""
                {"category":"需求审批","approvalTitle":"周拓的系统开发2.0","proposerName":"周拓","approvalCode":"202603250009","submittedTime":"2026/3/25 16:17","requirementType":"研发需求","developmentBranchName":"feature/req-202603250009","zentaoUrl":"https://zentao.example.com/story-view-123.html","requirementDigest":"沃橙绑卡","requirementName":"沃橙绑卡需求","requirementSummary":"描述","department":"供应链业务部","businessLine":"供应链科技","remark":"高优","estimatedEffort":"2d","plannedDueDate":"2026/04/05","developmentStartedDate":null,"actualEffort":null,"testingStartedDate":null,"actualCompletedTime":null,"acceptanceTime":null,"releasedTime":null,"projectHint":"供应链科技","fields":[],"attachmentSummaries":[]}
                """);
        after.setDemandStatus("待排期");
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
        assertTrue(structuredJsonCaptor.getValue().contains("\"developmentBranchName\":\"feature/req-202603250009\""));
        verify(intakeHistoryMapper).insert(any());
        assertEquals("https://zentao.example.com/story-view-123.html", detail.structuredData().zentaoUrl());
        assertEquals("feature/req-202603250009", detail.structuredData().developmentBranchName());
    }

    @Test
    void updateBusinessLine_shouldPersistBusinessLineForCompletedHistoricalDemand() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeHistoryMapper intakeHistoryMapper = mock(IntakeHistoryMapper.class);
        BusinessLineMapper businessLineMapper = mock(BusinessLineMapper.class);
        AttachmentService attachmentService = mock(AttachmentService.class);
        IntakeStructuredDataExtractor intakeStructuredDataExtractor = new IntakeStructuredDataExtractor();
        IntakeEnrichmentService intakeEnrichmentService = mock(IntakeEnrichmentService.class);

        IntakeService service = new IntakeService(
                intakeMapper,
                intakeHistoryMapper,
                mock(IntakeWorkItemRelationMapper.class),
                businessLineMapper,
                attachmentService,
                intakeStructuredDataExtractor,
                intakeEnrichmentService,
                mock(CodexCliSqlDraftGenerator.class),
                null,
                new ObjectMapper()
        );

        BusinessLineEntity targetBusinessLine = new BusinessLineEntity();
        targetBusinessLine.setBusinessLineName("资产业务");
        targetBusinessLine.setEnabled(true);
        when(businessLineMapper.findByName("资产业务")).thenReturn(targetBusinessLine);

        IntakeRecordEntity before = new IntakeRecordEntity();
        before.setId(403L);
        before.setSenderName("admin");
        before.setReceivedAt(LocalDateTime.of(2026, 4, 1, 10, 0));
        before.setStructuredDataJson("""
                {"category":"需求审批","approvalTitle":"周拓的系统开发2.0","proposerName":"周拓","approvalCode":"202603250009","submittedTime":"2026/3/25 16:17","requirementType":"研发需求","developmentBranchName":"feature/req-202603250009","zentaoUrl":"https://zentao.example.com/story-view-123.html","requirementDigest":"沃橙绑卡","requirementName":"沃橙绑卡需求","requirementSummary":"描述","department":"供应链业务部","businessLine":"供应链科技","remark":"高优","estimatedEffort":"2d","plannedDueDate":"2026/04/05","developmentStartedDate":null,"actualEffort":null,"testingStartedDate":null,"actualCompletedTime":null,"scheduledAcceptanceDate":"2026/04/07","actualTestingEffort":"3h","actualTestingCompletedDate":"2026/04/08","acceptanceTime":"2026/04/08","releasedTime":"2026/04/09","projectHint":"供应链科技","fields":[{"label":"审批编号","value":"202603250009"}],"attachmentSummaries":[]}
                """);
        before.setDemandStatus("已完成");
        before.setEnrichmentStatus("SUCCEEDED");
        before.setIntakeStatus("待整理");

        IntakeRecordEntity after = new IntakeRecordEntity();
        after.setId(403L);
        after.setSenderName("admin");
        after.setReceivedAt(LocalDateTime.of(2026, 4, 1, 10, 0));
        after.setStructuredDataJson("""
                {"category":"需求审批","approvalTitle":"周拓的系统开发2.0","proposerName":"周拓","approvalCode":"202603250009","submittedTime":"2026/3/25 16:17","requirementType":"研发需求","developmentBranchName":"feature/req-202603250009","zentaoUrl":"https://zentao.example.com/story-view-123.html","requirementDigest":"沃橙绑卡","requirementName":"沃橙绑卡需求","requirementSummary":"描述","department":"供应链业务部","businessLine":"资产业务","remark":"高优","estimatedEffort":"2d","plannedDueDate":"2026/04/05","developmentStartedDate":null,"actualEffort":null,"testingStartedDate":null,"actualCompletedTime":null,"scheduledAcceptanceDate":"2026/04/07","actualTestingEffort":"3h","actualTestingCompletedDate":"2026/04/08","acceptanceTime":"2026/04/08","releasedTime":"2026/04/09","projectHint":"资产业务","fields":[{"label":"审批编号","value":"202603250009"}],"attachmentSummaries":[]}
                """);
        after.setDemandStatus("已完成");
        after.setEnrichmentStatus("SUCCEEDED");
        after.setIntakeStatus("待整理");

        when(intakeMapper.findById(403L)).thenReturn(before, after);
        when(attachmentService.listIntakeAttachments(403L)).thenReturn(List.of());
        when(intakeHistoryMapper.findRecentByIntakeId(403L, 20)).thenReturn(List.of());

        IntakeBusinessLineUpdateRequest request = new IntakeBusinessLineUpdateRequest();
        request.setBusinessLine("资产业务");

        var detail = service.updateBusinessLine(403L, request, "admin");

        ArgumentCaptor<String> structuredJsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(intakeMapper).updateStructuredData(eq(403L), structuredJsonCaptor.capture());
        assertTrue(structuredJsonCaptor.getValue().contains("\"businessLine\":\"资产业务\""));
        assertTrue(structuredJsonCaptor.getValue().contains("\"projectHint\":\"资产业务\""));
        assertTrue(structuredJsonCaptor.getValue().contains("\"zentaoUrl\":\"https://zentao.example.com/story-view-123.html\""));
        assertTrue(structuredJsonCaptor.getValue().contains("\"scheduledAcceptanceDate\":\"2026/04/07\""));
        assertTrue(structuredJsonCaptor.getValue().contains("\"actualTestingEffort\":\"3h\""));
        assertTrue(structuredJsonCaptor.getValue().contains("\"actualTestingCompletedDate\":\"2026/04/08\""));
        assertTrue(structuredJsonCaptor.getValue().contains("\"fields\":[{\"label\":\"审批编号\",\"value\":\"202603250009\"}]"));
        ArgumentCaptor<IntakeHistoryEntity> historyCaptor = ArgumentCaptor.forClass(IntakeHistoryEntity.class);
        verify(intakeHistoryMapper).insert(historyCaptor.capture());
        assertEquals("修改业务线", historyCaptor.getValue().getActionSummary());
        assertTrue(historyCaptor.getValue().getDetailText().contains("业务线：供应链科技 -> 资产业务"));
        assertEquals("资产业务", detail.structuredData().businessLine());
        assertEquals("资产业务", detail.structuredData().projectHint());
    }

    @Test
    void updateBusinessLine_shouldRejectDisabledBusinessLine() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeHistoryMapper intakeHistoryMapper = mock(IntakeHistoryMapper.class);
        BusinessLineMapper businessLineMapper = mock(BusinessLineMapper.class);
        IntakeService service = new IntakeService(
                intakeMapper,
                intakeHistoryMapper,
                mock(IntakeWorkItemRelationMapper.class),
                businessLineMapper,
                mock(AttachmentService.class),
                new IntakeStructuredDataExtractor(),
                mock(IntakeEnrichmentService.class),
                mock(CodexCliSqlDraftGenerator.class),
                null,
                new ObjectMapper()
        );

        IntakeRecordEntity entity = new IntakeRecordEntity();
        entity.setId(404L);
        entity.setStructuredDataJson("""
                {"requirementType":"研发需求","businessLine":"供应链科技","projectHint":"供应链科技","fields":[],"attachmentSummaries":[]}
                """);
        when(intakeMapper.findById(404L)).thenReturn(entity);

        BusinessLineEntity disabled = new BusinessLineEntity();
        disabled.setBusinessLineName("资产业务");
        disabled.setEnabled(false);
        when(businessLineMapper.findByName("资产业务")).thenReturn(disabled);

        IntakeBusinessLineUpdateRequest request = new IntakeBusinessLineUpdateRequest();
        request.setBusinessLine("资产业务");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.updateBusinessLine(404L, request, "admin"));
        assertEquals("业务线不存在或未启用：资产业务", ex.getMessage());
        verify(intakeMapper, never()).updateStructuredData(eq(404L), any());
        verify(intakeHistoryMapper, never()).insert(any());
    }

    @Test
    void updateDevelopmentBranch_shouldPersistBranchAndRecordHistory() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeHistoryMapper intakeHistoryMapper = mock(IntakeHistoryMapper.class);
        AttachmentService attachmentService = mock(AttachmentService.class);
        IntakeStructuredDataExtractor intakeStructuredDataExtractor = new IntakeStructuredDataExtractor();
        IntakeEnrichmentService intakeEnrichmentService = mock(IntakeEnrichmentService.class);

        IntakeService service = new IntakeService(
                intakeMapper,
                intakeHistoryMapper,
                attachmentService,
                intakeStructuredDataExtractor,
                intakeEnrichmentService,
                mock(CodexCliSqlDraftGenerator.class),
                new ObjectMapper()
        );

        IntakeRecordEntity before = new IntakeRecordEntity();
        before.setId(402L);
        before.setSenderName("admin");
        before.setReceivedAt(LocalDateTime.of(2026, 4, 1, 10, 0));
        before.setStructuredDataJson("""
                {"category":"需求审批","approvalTitle":"周拓的系统开发2.0","proposerName":"周拓","approvalCode":"202603250009","submittedTime":"2026/3/25 16:17","requirementType":"研发需求","developmentBranchName":"feature/req-202603250009","zentaoUrl":"https://zentao.example.com/story-view-123.html","requirementDigest":"沃橙绑卡","requirementName":"沃橙绑卡需求","requirementSummary":"描述","department":"供应链业务部","businessLine":"供应链科技","remark":"高优","estimatedEffort":"2d","plannedDueDate":"2026/04/05","developmentStartedDate":null,"actualEffort":null,"testingStartedDate":null,"actualCompletedTime":null,"acceptanceTime":null,"releasedTime":null,"projectHint":"供应链科技","fields":[],"attachmentSummaries":[]}
                """);
        before.setDemandStatus("待排期");
        before.setEnrichmentStatus("SUCCEEDED");
        before.setIntakeStatus("待整理");

        IntakeRecordEntity after = new IntakeRecordEntity();
        after.setId(402L);
        after.setSenderName("admin");
        after.setReceivedAt(LocalDateTime.of(2026, 4, 1, 10, 0));
        after.setStructuredDataJson("""
                {"category":"需求审批","approvalTitle":"周拓的系统开发2.0","proposerName":"周拓","approvalCode":"202603250009","submittedTime":"2026/3/25 16:17","requirementType":"研发需求","developmentBranchName":"feature/custom_branch_202603250009","zentaoUrl":"https://zentao.example.com/story-view-123.html","requirementDigest":"沃橙绑卡","requirementName":"沃橙绑卡需求","requirementSummary":"描述","department":"供应链业务部","businessLine":"供应链科技","remark":"高优","estimatedEffort":"2d","plannedDueDate":"2026/04/05","developmentStartedDate":null,"actualEffort":null,"testingStartedDate":null,"actualCompletedTime":null,"acceptanceTime":null,"releasedTime":null,"projectHint":"供应链科技","fields":[],"attachmentSummaries":[]}
                """);
        after.setDemandStatus("待排期");
        after.setEnrichmentStatus("SUCCEEDED");
        after.setIntakeStatus("待整理");

        when(intakeMapper.findById(402L)).thenReturn(before, after);
        when(attachmentService.listIntakeAttachments(402L)).thenReturn(List.of());
        when(intakeHistoryMapper.findRecentByIntakeId(402L, 20)).thenReturn(List.of());

        IntakeDevelopmentBranchRequest request = new IntakeDevelopmentBranchRequest();
        request.setDevelopmentBranchName("feature/custom_branch_20260325");

        var detail = service.updateDevelopmentBranch(402L, request, "admin");

        ArgumentCaptor<String> structuredJsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(intakeMapper).updateStructuredData(eq(402L), structuredJsonCaptor.capture());
        assertTrue(structuredJsonCaptor.getValue().contains("\"developmentBranchName\":\"feature/custom_branch_202603250009\""));
        assertTrue(structuredJsonCaptor.getValue().contains("\"zentaoUrl\":\"https://zentao.example.com/story-view-123.html\""));
        ArgumentCaptor<IntakeHistoryEntity> historyCaptor = ArgumentCaptor.forClass(IntakeHistoryEntity.class);
        verify(intakeHistoryMapper).insert(historyCaptor.capture());
        assertTrue(historyCaptor.getValue().getDetailText().contains("研发分支：feature/req-202603250009 -> feature/custom_branch_202603250009"));
        assertEquals("feature/custom_branch_202603250009", detail.structuredData().developmentBranchName());
        assertEquals("https://zentao.example.com/story-view-123.html", detail.structuredData().zentaoUrl());
    }

    @Test
    void advanceStage_shouldSaveDeliveryFilesForOperationsDemandCompletion() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeHistoryMapper intakeHistoryMapper = mock(IntakeHistoryMapper.class);
        AttachmentService attachmentService = mock(AttachmentService.class);
        IntakeStructuredDataExtractor intakeStructuredDataExtractor = new IntakeStructuredDataExtractor();
        IntakeEnrichmentService intakeEnrichmentService = mock(IntakeEnrichmentService.class);

        IntakeService service = new IntakeService(
                intakeMapper,
                intakeHistoryMapper,
                attachmentService,
                intakeStructuredDataExtractor,
                intakeEnrichmentService,
                mock(CodexCliSqlDraftGenerator.class),
                new ObjectMapper()
        );

        IntakeRecordEntity before = new IntakeRecordEntity();
        before.setId(501L);
        before.setSenderName("ops-user");
        before.setReceivedAt(LocalDateTime.of(2026, 4, 2, 10, 0));
        before.setStructuredDataJson("""
                {"category":"需求审批","approvalTitle":"数据修复申请","proposerName":"运维同学","approvalCode":"OPS-002","submittedTime":"2026/4/2 10:00","requirementType":"数据提取/运维","requirementDigest":"批量修复历史还款数据","requirementName":"批量修复历史还款数据","requirementSummary":"描述","department":"运营支持部","businessLine":"资产业务","remark":"紧急处理","estimatedEffort":"4h","plannedDueDate":"2026/04/02","developmentStartedDate":"2026/04/02","actualEffort":null,"testingStartedDate":null,"actualCompletedTime":null,"acceptanceTime":null,"releasedTime":null,"projectHint":"资产业务","fields":[],"attachmentSummaries":[]}
                """);
        before.setDemandStatus("待验收");

        IntakeRecordEntity after = new IntakeRecordEntity();
        after.setId(501L);
        after.setSenderName("ops-user");
        after.setReceivedAt(LocalDateTime.of(2026, 4, 2, 10, 0));
        after.setStructuredDataJson("""
                {"category":"需求审批","approvalTitle":"数据修复申请","proposerName":"运维同学","approvalCode":"OPS-002","submittedTime":"2026/4/2 10:00","requirementType":"数据提取/运维","requirementDigest":"批量修复历史还款数据","requirementName":"批量修复历史还款数据","requirementSummary":"描述","department":"运营支持部","businessLine":"资产业务","remark":"紧急处理","estimatedEffort":"4h","plannedDueDate":"2026/04/02","developmentStartedDate":"2026/04/02","actualEffort":"5h","testingStartedDate":null,"actualCompletedTime":"2026/04/03","acceptanceTime":"2026/04/03","releasedTime":"2026/04/03","projectHint":"资产业务","fields":[],"attachmentSummaries":[]}
                """);
        after.setDemandStatus("已完成");

        when(intakeMapper.findById(501L)).thenReturn(before, after);
        when(attachmentService.listIntakeAttachments(501L)).thenReturn(List.of());
        when(intakeHistoryMapper.findRecentByIntakeId(501L, 20)).thenReturn(List.of());

        IntakeStageActionRequest request = new IntakeStageActionRequest();
        request.setAction("CONFIRM_ACCEPTANCE");
        request.setAcceptanceTime("2026/04/03");
        request.setOccurredAt("2026/04/03");

        MockMultipartFile dataFile = new MockMultipartFile(
                "dataFiles",
                "delivery-result.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "demo".getBytes()
        );

        service.advanceStage(501L, request, List.of(dataFile), "admin");

        verify(attachmentService).saveIntakeDeliveryFiles(eq(501L), any());
        ArgumentCaptor<IntakeHistoryEntity> historyCaptor = ArgumentCaptor.forClass(IntakeHistoryEntity.class);
        verify(intakeHistoryMapper).insert(historyCaptor.capture());
        assertTrue(historyCaptor.getValue().getDetailText().contains("上传数据文件：delivery-result.xlsx"));
    }

    @Test
    void generateSqlDraft_shouldPersistDraftIntoStructuredDataAndHistory() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeHistoryMapper intakeHistoryMapper = mock(IntakeHistoryMapper.class);
        AttachmentService attachmentService = mock(AttachmentService.class);
        IntakeStructuredDataExtractor intakeStructuredDataExtractor = new IntakeStructuredDataExtractor();
        IntakeEnrichmentService intakeEnrichmentService = mock(IntakeEnrichmentService.class);
        CodexCliSqlDraftGenerator sqlDraftGenerator = mock(CodexCliSqlDraftGenerator.class);

        IntakeService service = new IntakeService(
                intakeMapper,
                intakeHistoryMapper,
                attachmentService,
                intakeStructuredDataExtractor,
                intakeEnrichmentService,
                sqlDraftGenerator,
                new ObjectMapper()
        );

        IntakeRecordEntity before = new IntakeRecordEntity();
        before.setId(601L);
        before.setSourceType("需求截图附件录入");
        before.setSourceChannel("需求录入");
        before.setSenderName("ops-user");
        before.setReceivedAt(LocalDateTime.of(2026, 4, 2, 10, 0));
        before.setRawContent("导出 2026 年 4 月支付失败订单");
        before.setStructuredDataJson("""
                {"category":"需求审批","approvalTitle":"支付失败订单导出","proposerName":"运维同学","approvalCode":"OPS-003","submittedTime":"2026/4/2 10:00","requirementType":"数据提取/运维","requirementDigest":"导出支付失败订单","requirementName":"支付失败订单导出","requirementSummary":"需要导出 2026 年 4 月支付失败订单。","department":"运营支持部","businessLine":"资产业务","remark":"人工执行 SQL","estimatedEffort":null,"plannedDueDate":null,"developmentStartedDate":null,"actualEffort":null,"testingStartedDate":null,"actualCompletedTime":null,"acceptanceTime":null,"releasedTime":null,"projectHint":"资产业务","fields":[],"attachmentSummaries":[]}
                """);
        before.setDemandStatus("已收录");

        IntakeSqlDraft draft = new IntakeSqlDraft(
                "MySQL",
                "SELECT * FROM pay_order WHERE status = :status;",
                "导出支付失败订单",
                List.of(":status=FAIL"),
                List.of("推断订单表为 pay_order"),
                List.of("请确认真实订单表名"),
                List.of("人工执行前确认生产库和导出范围"),
                "2026-04-02T10:01:00",
                "Codex CLI"
        );
        IntakeRecordEntity after = new IntakeRecordEntity();
        after.setId(601L);
        after.setSourceType(before.getSourceType());
        after.setSourceChannel(before.getSourceChannel());
        after.setSenderName(before.getSenderName());
        after.setReceivedAt(before.getReceivedAt());
        after.setRawContent(before.getRawContent());
        after.setStructuredDataJson("""
                {"category":"需求审批","approvalTitle":"支付失败订单导出","proposerName":"运维同学","approvalCode":"OPS-003","submittedTime":"2026/4/2 10:00","requirementType":"数据提取/运维","requirementDigest":"导出支付失败订单","requirementName":"支付失败订单导出","requirementSummary":"需要导出 2026 年 4 月支付失败订单。","department":"运营支持部","businessLine":"资产业务","remark":"人工执行 SQL","estimatedEffort":null,"plannedDueDate":null,"developmentStartedDate":null,"actualEffort":null,"testingStartedDate":null,"actualCompletedTime":null,"acceptanceTime":null,"releasedTime":null,"projectHint":"资产业务","fields":[],"attachmentSummaries":[],"sqlDraft":{"dialect":"MySQL","sql":"SELECT * FROM pay_order WHERE status = :status;","explanation":"导出支付失败订单","parameters":[":status=FAIL"],"assumptions":["推断订单表为 pay_order"],"questions":["请确认真实订单表名"],"riskWarnings":["人工执行前确认生产库和导出范围"],"generatedAt":"2026-04-02T10:01:00","generator":"Codex CLI"}}
                """);
        after.setDemandStatus("已收录");

        when(intakeMapper.findById(601L)).thenReturn(before, after);
        when(attachmentService.listIntakeFileContexts(601L)).thenReturn(List.of());
        when(attachmentService.listIntakeAttachments(601L)).thenReturn(List.of());
        when(intakeHistoryMapper.findRecentByIntakeId(601L, 20)).thenReturn(List.of());
        when(sqlDraftGenerator.generate(eq(before), any(), eq(List.of())))
                .thenReturn(CodexCliSqlDraftGenerator.SqlDraftGenerationResult.succeeded(draft));

        var detail = service.generateSqlDraft(601L, "admin");

        ArgumentCaptor<String> structuredJsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(intakeMapper).updateStructuredData(eq(601L), structuredJsonCaptor.capture());
        assertTrue(structuredJsonCaptor.getValue().contains("\"sqlDraft\""));
        assertTrue(structuredJsonCaptor.getValue().contains("SELECT * FROM pay_order"));
        ArgumentCaptor<IntakeHistoryEntity> historyCaptor = ArgumentCaptor.forClass(IntakeHistoryEntity.class);
        verify(intakeHistoryMapper).insert(historyCaptor.capture());
        assertEquals("生成 SQL 草稿", historyCaptor.getValue().getActionSummary());
        assertEquals("SELECT * FROM pay_order WHERE status = :status;", detail.structuredData().sqlDraft().sql());
    }
}
