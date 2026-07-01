package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.dao.intake.DevelopmentAnalysisMapper;
import cn.aslight.workhub.dao.intake.IntakeHistoryMapper;
import cn.aslight.workhub.dao.intake.IntakeMapper;
import cn.aslight.workhub.dao.project.ProjectMapper;
import cn.aslight.workhub.dao.project.ProjectInvolvedSystemMapper;
import cn.aslight.workhub.dao.system.UserMapper;
import cn.aslight.workhub.model.intake.DevelopmentAnalysisDraft;
import cn.aslight.workhub.model.intake.DevelopmentAnalysisConfirmResponse;
import cn.aslight.workhub.model.intake.DevelopmentAnalysisDraftUpdateRequest;
import cn.aslight.workhub.model.intake.DevelopmentAnalysisEntity;
import cn.aslight.workhub.model.intake.DevelopmentAnalysisOwnerUpdateRequest;
import cn.aslight.workhub.model.intake.DevelopmentWorkItemDraft;
import cn.aslight.workhub.model.intake.IntakeHistoryEntity;
import cn.aslight.workhub.model.intake.IntakeRecordEntity;
import cn.aslight.workhub.model.project.ProjectDetailResponse;
import cn.aslight.workhub.model.project.ProjectInvolvedSystemEntity;
import cn.aslight.workhub.model.system.UserOptionResponse;
import cn.aslight.workhub.model.workitem.WorkItemDetailResponse;
import cn.aslight.workhub.service.workitem.WorkItemService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DevelopmentAnalysisServiceTest {

    @Test
    void analyze_shouldQueueAsyncExecutionAndExecution_shouldMarkDemandStatusAsPendingConfirmation() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeHistoryMapper intakeHistoryMapper = mock(IntakeHistoryMapper.class);
        DevelopmentAnalysisMapper developmentAnalysisMapper = mock(DevelopmentAnalysisMapper.class);
        ProjectMapper projectMapper = mock(ProjectMapper.class);
        ProjectInvolvedSystemMapper involvedSystemMapper = mock(ProjectInvolvedSystemMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        GitlabRepositoryService gitlabRepositoryService = mock(GitlabRepositoryService.class);
        ProjectKnowledgeBaseService projectKnowledgeBaseService = mock(ProjectKnowledgeBaseService.class);
        CodexCliDevelopmentAnalysisGenerator analysisGenerator = mock(CodexCliDevelopmentAnalysisGenerator.class);
        WorkItemService workItemService = mock(WorkItemService.class);

        DevelopmentAnalysisService service = new DevelopmentAnalysisService(
                intakeMapper,
                intakeHistoryMapper,
                developmentAnalysisMapper,
                projectMapper,
                involvedSystemMapper,
                userMapper,
                gitlabRepositoryService,
                projectKnowledgeBaseService,
                analysisGenerator,
                workItemService,
                command -> { },
                new ObjectMapper()
        );

        IntakeRecordEntity intake = new IntakeRecordEntity();
        intake.setId(39L);
        intake.setDemandStatus("待评估");
        intake.setStructuredDataJson("""
                {"category":"需求审批","approvalCode":"202603130005","requirementType":"研发需求","requirementName":"趣学呗、易单通系统改造后的业财对接","projectHint":"账单管理","fields":[],"attachmentSummaries":[]}
                """);
        when(intakeMapper.findById(39L)).thenReturn(intake);

        ProjectDetailResponse project = new ProjectDetailResponse(
                3L,
                "thctay-saps",
                "物流平台-账务系统",
                "业务项目",
                "账单管理",
                "石浩",
                "进行中",
                null,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        when(projectMapper.findFirstDetailByBusinessLine("账单管理")).thenReturn(project);
        when(userMapper.findActiveUsers()).thenReturn(List.of(new UserOptionResponse("石浩", "石浩", "账单管理")));

        GitlabRepositoryService.GitlabRepository repository = new GitlabRepositoryService.GitlabRepository(
                "http://10.10.116.21:10000/xxt-plateform/thctay-saps.git",
                Path.of("/tmp/git-cache/thctay-saps")
        );
        GitlabRepositoryService.GitlabRepositoryBundle repositoryBundle = new GitlabRepositoryService.GitlabRepositoryBundle(
                "xxt-plateform",
                Path.of("/tmp/git-cache/xxt-plateform"),
                List.of(repository)
        );
        when(gitlabRepositoryService.resolveAndFetchGroup(project)).thenReturn(repositoryBundle);
        when(projectKnowledgeBaseService.upsertRequirementIterationNote(any(), any(), eq(project)))
                .thenReturn(new ProjectKnowledgeBaseService.RequirementKnowledgeNote(
                        "raw/requirements/账单管理/物流平台-账务系统/2026/202603130005/source.md",
                        "wiki/projects/账单管理/物流平台-账务系统/需求迭代/2026/202603130005.md",
                        "obsidian://open?vault=Company+Obsidian+Vault&file=wiki%2Fprojects%2F%E8%B4%A6%E5%8D%95%E7%AE%A1%E7%90%86.md",
                        "# 需求 Markdown",
                        true
                ));
        when(projectKnowledgeBaseService.buildContext(any(), eq(project))).thenReturn("知识库参考");

        DevelopmentAnalysisDraft draft = new DevelopmentAnalysisDraft(
                "DRAFT",
                "账单管理",
                3L,
                "物流平台-账务系统",
                repository.repositoryUrl(),
                null,
                null,
                null,
                "summary",
                List.of("新增账单拆分口径"),
                List.of("module-a"),
                List.of(),
                List.of(),
                List.of("石浩"),
                List.of(),
                "16h",
                "16h",
                null,
                "2026/05/06",
                null,
                null,
                null,
                "RESERVED",
                "reserved",
                "2026-04-24T10:00:00",
                "Codex CLI"
        );
        when(analysisGenerator.generate(any(), any(), eq(project), eq(repositoryBundle), any(), any(), eq("知识库参考"))).thenReturn(draft);

        DevelopmentAnalysisEntity entity = new DevelopmentAnalysisEntity();
        entity.setId(1L);
        entity.setIntakeId(39L);
        entity.setAnalysisStatus("PENDING");
        entity.setAnalysisMessage("任务评估已提交，系统正在后台处理中");
        entity.setDraftJson(null);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        when(developmentAnalysisMapper.findLatestByIntakeId(39L)).thenReturn(null, entity);
        when(developmentAnalysisMapper.findById(1L)).thenReturn(entity);

        var response = service.analyze(39L, "账单管理", "admin");

        org.junit.jupiter.api.Assertions.assertEquals("PENDING", response.status());
        verify(intakeMapper).updateStructuredData(eq(39L), any());

        service.executeAnalysis(1L, 39L, "admin");

        verify(intakeMapper).updateManagementFields(eq(39L), any(), any(), eq("待评估"));
    }

    @Test
    void executeAnalysis_shouldRestorePendingEvaluationAndRecordHistoryWhenFailed() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeHistoryMapper intakeHistoryMapper = mock(IntakeHistoryMapper.class);
        DevelopmentAnalysisMapper developmentAnalysisMapper = mock(DevelopmentAnalysisMapper.class);
        ProjectMapper projectMapper = mock(ProjectMapper.class);
        ProjectInvolvedSystemMapper involvedSystemMapper = mock(ProjectInvolvedSystemMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        GitlabRepositoryService gitlabRepositoryService = mock(GitlabRepositoryService.class);
        ProjectKnowledgeBaseService projectKnowledgeBaseService = mock(ProjectKnowledgeBaseService.class);
        CodexCliDevelopmentAnalysisGenerator analysisGenerator = mock(CodexCliDevelopmentAnalysisGenerator.class);
        WorkItemService workItemService = mock(WorkItemService.class);

        DevelopmentAnalysisService service = new DevelopmentAnalysisService(
                intakeMapper,
                intakeHistoryMapper,
                developmentAnalysisMapper,
                projectMapper,
                involvedSystemMapper,
                userMapper,
                gitlabRepositoryService,
                projectKnowledgeBaseService,
                analysisGenerator,
                workItemService,
                command -> { },
                new ObjectMapper()
        );

        IntakeRecordEntity intake = new IntakeRecordEntity();
        intake.setId(40L);
        intake.setStructuredDataJson("""
                {"category":"需求审批","approvalCode":"202604280001","requirementType":"研发需求","requirementName":"找不到业务线的需求","projectHint":"不存在的业务线","fields":[],"attachmentSummaries":[]}
                """);
        when(intakeMapper.findById(40L)).thenReturn(intake);

        DevelopmentAnalysisEntity entity = new DevelopmentAnalysisEntity();
        entity.setId(2L);
        entity.setIntakeId(40L);
        entity.setAnalysisStatus("PENDING");
        entity.setDraftJson(null);
        when(developmentAnalysisMapper.findById(2L)).thenReturn(entity);
        when(gitlabRepositoryService.resolveAndFetchGroup(any(ProjectDetailResponse.class))).thenThrow(new IllegalArgumentException("GitLab 业务线未配置"));

        service.executeAnalysis(2L, 40L, "admin");

        verify(intakeMapper, org.mockito.Mockito.never()).updateManagementFields(eq(40L), any(), any(), any());
        ArgumentCaptor<IntakeHistoryEntity> historyCaptor = forClass(IntakeHistoryEntity.class);
        verify(intakeHistoryMapper).insert(historyCaptor.capture());
        assertEquals("任务评估失败", historyCaptor.getValue().getActionSummary());
        assertTrue(historyCaptor.getValue().getDetailText().contains("GitLab 业务线未配置"));
    }

    @Test
    void updateOwners_shouldPersistOwnerChangesIntoDraft() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeHistoryMapper intakeHistoryMapper = mock(IntakeHistoryMapper.class);
        DevelopmentAnalysisMapper developmentAnalysisMapper = mock(DevelopmentAnalysisMapper.class);
        ProjectMapper projectMapper = mock(ProjectMapper.class);
        ProjectInvolvedSystemMapper involvedSystemMapper = mock(ProjectInvolvedSystemMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        GitlabRepositoryService gitlabRepositoryService = mock(GitlabRepositoryService.class);
        ProjectKnowledgeBaseService projectKnowledgeBaseService = mock(ProjectKnowledgeBaseService.class);
        CodexCliDevelopmentAnalysisGenerator analysisGenerator = mock(CodexCliDevelopmentAnalysisGenerator.class);
        WorkItemService workItemService = mock(WorkItemService.class);
        ObjectMapper objectMapper = new ObjectMapper();

        DevelopmentAnalysisService service = new DevelopmentAnalysisService(
                intakeMapper,
                intakeHistoryMapper,
                developmentAnalysisMapper,
                projectMapper,
                involvedSystemMapper,
                userMapper,
                gitlabRepositoryService,
                projectKnowledgeBaseService,
                analysisGenerator,
                workItemService,
                command -> { },
                objectMapper
        );

        DevelopmentAnalysisDraft draft = new DevelopmentAnalysisDraft(
                "DRAFT",
                "账单管理",
                3L,
                "物流平台-账务系统",
                "http://gitlab/repo.git",
                null,
                null,
                null,
                "summary",
                List.of("支付回调需要增强验签和幂等"),
                List.of(),
                List.of(),
                List.of(),
                List.of("石浩"),
                List.of(new DevelopmentWorkItemDraft(
                        "支付回调改造",
                        "改造支付回调逻辑",
                        "支付回调需要增强验签和幂等",
                        "CODE_CHANGE",
                        List.of("支付回调接口"),
                        List.of("调整回调验签", "补充幂等处理"),
                        List.of("支付服务"),
                        List.of("需求原文"),
                        "MEDIUM",
                        "payment",
                        List.of(),
                        "8h",
                        "石浩",
                        "2",
                        "2026/05/01",
                        "2026/05/06",
                        null,
                        null
                )),
                "8h",
                "8h",
                null,
                "2026/05/06",
                null,
                null,
                null,
                "RESERVED",
                "reserved",
                "2026-04-24T10:00:00",
                "Codex CLI"
        );
        DevelopmentAnalysisEntity entity = new DevelopmentAnalysisEntity();
        entity.setId(1L);
        entity.setIntakeId(39L);
        entity.setAnalysisStatus("DRAFT");
        entity.setDraftJson(writeDraft(objectMapper, draft));
        when(developmentAnalysisMapper.findLatestByIntakeId(39L)).thenReturn(entity);

        service.updateOwners(
                39L,
                new DevelopmentAnalysisOwnerUpdateRequest(List.of(
                        new DevelopmentAnalysisOwnerUpdateRequest.WorkItemOwner(0, "王小明")
                )),
                "admin"
        );

        ArgumentCaptor<DevelopmentAnalysisEntity> captor = forClass(DevelopmentAnalysisEntity.class);
        verify(developmentAnalysisMapper).update(captor.capture());
        DevelopmentAnalysisDraft updated = readDraft(objectMapper, captor.getValue().getDraftJson());
        assertEquals("王小明", updated.workItems().getFirst().ownerUserName());
        assertTrue(updated.developerPool().contains("王小明"));
        verify(userMapper).upsertActiveUser("王小明", "王小明");
    }

    @Test
    void updateDraft_shouldPersistEffortAndIgnorePlannedDates() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeHistoryMapper intakeHistoryMapper = mock(IntakeHistoryMapper.class);
        DevelopmentAnalysisMapper developmentAnalysisMapper = mock(DevelopmentAnalysisMapper.class);
        ProjectMapper projectMapper = mock(ProjectMapper.class);
        ProjectInvolvedSystemMapper involvedSystemMapper = mock(ProjectInvolvedSystemMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        GitlabRepositoryService gitlabRepositoryService = mock(GitlabRepositoryService.class);
        ProjectKnowledgeBaseService projectKnowledgeBaseService = mock(ProjectKnowledgeBaseService.class);
        CodexCliDevelopmentAnalysisGenerator analysisGenerator = mock(CodexCliDevelopmentAnalysisGenerator.class);
        WorkItemService workItemService = mock(WorkItemService.class);
        ObjectMapper objectMapper = new ObjectMapper();

        DevelopmentAnalysisService service = new DevelopmentAnalysisService(
                intakeMapper,
                intakeHistoryMapper,
                developmentAnalysisMapper,
                projectMapper,
                involvedSystemMapper,
                userMapper,
                gitlabRepositoryService,
                projectKnowledgeBaseService,
                analysisGenerator,
                workItemService,
                command -> { },
                objectMapper
        );

        DevelopmentAnalysisDraft draft = new DevelopmentAnalysisDraft(
                "DRAFT",
                "账单管理",
                3L,
                "物流平台-账务系统",
                "http://gitlab/repo.git",
                null,
                null,
                null,
                "summary",
                List.of("支付回调需要增强验签和幂等"),
                List.of(),
                List.of(),
                List.of(),
                List.of("石浩"),
                List.of(new DevelopmentWorkItemDraft(
                        "支付回调改造",
                        "改造支付回调逻辑",
                        "支付回调需要增强验签和幂等",
                        "CODE_CHANGE",
                        List.of("支付回调接口"),
                        List.of("调整回调验签"),
                        List.of("支付服务"),
                        List.of("需求原文"),
                        "MEDIUM",
                        "payment",
                        List.of(),
                        "8h",
                        "石浩",
                        "2",
                        "2026/05/01",
                        "2026/05/06",
                        null,
                        null
                )),
                "8h",
                "8h",
                null,
                "2026/05/06",
                null,
                null,
                null,
                "RESERVED",
                "reserved",
                "2026-04-24T10:00:00",
                "Codex CLI"
        );
        DevelopmentAnalysisEntity entity = new DevelopmentAnalysisEntity();
        entity.setId(1L);
        entity.setIntakeId(39L);
        entity.setAnalysisStatus("DRAFT");
        entity.setDraftJson(writeDraft(objectMapper, draft));
        IntakeRecordEntity intake = new IntakeRecordEntity();
        intake.setId(39L);
        intake.setDemandStatus("待评估");
        intake.setStructuredDataJson("""
                {"category":"需求审批","requirementType":"研发需求","requirementName":"支付回调需求","projectHint":"账单管理","fields":[],"attachmentSummaries":[]}
                """);
        when(intakeMapper.findById(39L)).thenReturn(intake);
        ProjectDetailResponse project = new ProjectDetailResponse(
                3L,
                "thctay-saps",
                "物流平台-账务系统",
                "业务项目",
                "账单管理",
                "石浩",
                "进行中",
                null,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        when(projectMapper.findFirstDetailByBusinessLine("账单管理")).thenReturn(project);
        when(developmentAnalysisMapper.findLatestByIntakeId(39L)).thenReturn(entity);

        service.updateDraft(
                39L,
                new DevelopmentAnalysisDraftUpdateRequest(
                        "账单管理",
                        "2d",
                        "1.5d",
                        "4h",
                        "2026-05-10",
                        "2026-05-10",
                        "2026-05-11",
                        null,
                        "2026-05-13",
                        List.of(new DevelopmentAnalysisDraftUpdateRequest.WorkItemDraft(
                                "支付回调改造",
                                "改造支付回调逻辑",
                                "支付回调需要增强验签和幂等",
                                "CODE_CHANGE",
                                List.of("支付回调接口"),
                                List.of("调整回调验签"),
                                List.of("支付服务"),
                                List.of("需求原文"),
                                "MEDIUM",
                                "payment",
                                List.of(),
                                "1d",
                                "石浩",
                                "2",
                                "2026/05/01",
                                "2026/05/06",
                                null,
                                null
                        ))
                ),
                "admin"
        );

        ArgumentCaptor<DevelopmentAnalysisEntity> captor = forClass(DevelopmentAnalysisEntity.class);
        verify(developmentAnalysisMapper).update(captor.capture());
        DevelopmentAnalysisDraft updated = readDraft(objectMapper, captor.getValue().getDraftJson());
        assertEquals("12h", updated.totalEstimatedEffort());
        assertEquals("8h", updated.developmentEstimatedEffort());
        assertEquals("4h", updated.testingEstimatedEffort());
        assertEquals(null, updated.plannedDueDate());
        assertEquals(null, updated.plannedTestingStartDate());
        assertEquals(null, updated.plannedTestingEndDate());
        assertEquals(null, updated.plannedReleaseDate());
        assertEquals("8h", updated.workItems().getFirst().estimatedEffort());
        verify(userMapper).upsertActiveUser("石浩", "石浩");
    }

    @Test
    void updateDraft_shouldValidateSystemTagsWithBusinessLineCodeWhenProjectUsesDisplayName() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeHistoryMapper intakeHistoryMapper = mock(IntakeHistoryMapper.class);
        DevelopmentAnalysisMapper developmentAnalysisMapper = mock(DevelopmentAnalysisMapper.class);
        ProjectMapper projectMapper = mock(ProjectMapper.class);
        ProjectInvolvedSystemMapper involvedSystemMapper = mock(ProjectInvolvedSystemMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        GitlabRepositoryService gitlabRepositoryService = mock(GitlabRepositoryService.class);
        ProjectKnowledgeBaseService projectKnowledgeBaseService = mock(ProjectKnowledgeBaseService.class);
        CodexCliDevelopmentAnalysisGenerator analysisGenerator = mock(CodexCliDevelopmentAnalysisGenerator.class);
        WorkItemService workItemService = mock(WorkItemService.class);
        ObjectMapper objectMapper = new ObjectMapper();

        DevelopmentAnalysisService service = new DevelopmentAnalysisService(
                intakeMapper,
                intakeHistoryMapper,
                developmentAnalysisMapper,
                projectMapper,
                involvedSystemMapper,
                userMapper,
                gitlabRepositoryService,
                projectKnowledgeBaseService,
                analysisGenerator,
                workItemService,
                command -> { },
                objectMapper
        );

        IntakeRecordEntity intake = new IntakeRecordEntity();
        intake.setId(40L);
        intake.setDemandStatus("待评估");
        intake.setStructuredDataJson("""
                {"category":"需求审批","requirementType":"研发需求","requirementName":"支付系统评估","projectHint":"BL000001","fields":[],"attachmentSummaries":[]}
                """);
        when(intakeMapper.findById(40L)).thenReturn(intake);

        DevelopmentAnalysisEntity entity = new DevelopmentAnalysisEntity();
        entity.setId(2L);
        entity.setIntakeId(40L);
        entity.setAnalysisStatus("DRAFT");
        entity.setDraftJson(writeDraft(objectMapper, minimalDraft("8h", "2h", List.of())));
        when(developmentAnalysisMapper.findLatestByIntakeId(40L)).thenReturn(entity);

        ProjectDetailResponse project = new ProjectDetailResponse(
                3L,
                "payment",
                "支付系统",
                "业务项目",
                "BL000001",
                "账单管理",
                "石浩",
                "进行中",
                null,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        when(projectMapper.findFirstDetailByBusinessLine("BL000001")).thenReturn(project);
        when(involvedSystemMapper.findSelectableByBusinessLine("BL000001"))
                .thenReturn(List.of(involvedSystem("scf-payment")));

        service.updateDraft(
                40L,
                new DevelopmentAnalysisDraftUpdateRequest(
                        "BL000001",
                        "8h",
                        "6h",
                        "2h",
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(new DevelopmentAnalysisDraftUpdateRequest.WorkItemDraft(
                                "支付系统改造",
                                "补充支付系统任务",
                                "支付链路改造",
                                "CODE_CHANGE",
                                List.of("支付服务"),
                                List.of("调整支付链路"),
                                List.of("scf-payment"),
                                List.of(),
                                "MEDIUM",
                                null,
                                List.of(),
                                "6h",
                                "石浩",
                                "2",
                                null,
                                null,
                                null,
                                null
                        ))
                ),
                "admin"
        );

        verify(involvedSystemMapper).findSelectableByBusinessLine("BL000001");
        verify(developmentAnalysisMapper).update(any());
    }

    @Test
    void updateDraft_shouldCreateManualDraftWhenAiDraftDoesNotExist() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeHistoryMapper intakeHistoryMapper = mock(IntakeHistoryMapper.class);
        DevelopmentAnalysisMapper developmentAnalysisMapper = mock(DevelopmentAnalysisMapper.class);
        ProjectMapper projectMapper = mock(ProjectMapper.class);
        ProjectInvolvedSystemMapper involvedSystemMapper = mock(ProjectInvolvedSystemMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        GitlabRepositoryService gitlabRepositoryService = mock(GitlabRepositoryService.class);
        ProjectKnowledgeBaseService projectKnowledgeBaseService = mock(ProjectKnowledgeBaseService.class);
        CodexCliDevelopmentAnalysisGenerator analysisGenerator = mock(CodexCliDevelopmentAnalysisGenerator.class);
        WorkItemService workItemService = mock(WorkItemService.class);
        ObjectMapper objectMapper = new ObjectMapper();

        DevelopmentAnalysisService service = new DevelopmentAnalysisService(
                intakeMapper,
                intakeHistoryMapper,
                developmentAnalysisMapper,
                projectMapper,
                involvedSystemMapper,
                userMapper,
                gitlabRepositoryService,
                projectKnowledgeBaseService,
                analysisGenerator,
                workItemService,
                command -> { },
                objectMapper
        );

        IntakeRecordEntity intake = new IntakeRecordEntity();
        intake.setId(42L);
        intake.setDemandStatus("待评估");
        intake.setStructuredDataJson("""
                {"category":"需求审批","requirementType":"研发需求","requirementName":"人工拆解需求","requirementSummary":"需要人工录入研发任务","projectHint":"账单管理","fields":[],"attachmentSummaries":[]}
                """);
        when(intakeMapper.findById(42L)).thenReturn(intake);

        ProjectDetailResponse project = new ProjectDetailResponse(
                3L,
                "thctay-saps",
                "物流平台-账务系统",
                "业务项目",
                "账单管理",
                "石浩",
                "进行中",
                null,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        when(projectMapper.findFirstDetailByBusinessLine("账单管理")).thenReturn(project);
        when(involvedSystemMapper.findSelectableByBusinessLine("账单管理"))
                .thenReturn(List.of(involvedSystem("workhub-web")));

        AtomicReference<DevelopmentAnalysisEntity> savedEntity = new AtomicReference<>();
        when(developmentAnalysisMapper.findLatestByIntakeId(42L)).thenAnswer(invocation -> savedEntity.get());
        when(developmentAnalysisMapper.insert(any())).thenAnswer(invocation -> {
            DevelopmentAnalysisEntity entity = invocation.getArgument(0);
            entity.setId(12L);
            entity.setCreatedAt(LocalDateTime.now());
            entity.setUpdatedAt(LocalDateTime.now());
            savedEntity.set(entity);
            return 1;
        });

        var response = service.updateDraft(
                42L,
                new DevelopmentAnalysisDraftUpdateRequest(
                        "账单管理",
                        "10h",
                        "8h",
                        "2h",
                        "2026-05-10",
                        "2026-05-10",
                        null,
                        null,
                        null,
                        List.of(new DevelopmentAnalysisDraftUpdateRequest.WorkItemDraft(
                                "人工录入任务",
                                "人工填写任务描述",
                                "需要补充手工任务拆解",
                                "CODE_CHANGE",
                                List.of("需求管理页面"),
                                List.of("支持无 AI 草稿时保存任务项"),
                                List.of("workhub-web"),
                                List.of(),
                                "MEDIUM",
                                null,
                                List.of(),
                                "10h",
                                "石浩",
                                "2",
                                "2026/05/08",
                                "2026/05/10",
                                null,
                                null
                        ))
                ),
                "admin"
        );

        assertEquals("DRAFT", response.status());
        assertEquals("manual", response.draft().generator());
        assertEquals(3L, response.draft().projectId());
        assertEquals("账单管理", response.draft().businessLine());
        assertEquals("人工录入任务", response.draft().workItems().getFirst().title());
        assertEquals("12h", response.draft().totalEstimatedEffort());
        verify(developmentAnalysisMapper).insert(any());
        verify(intakeHistoryMapper).insert(any());
        verify(userMapper).upsertActiveUser("石浩", "石浩");
    }

    @Test
    void updateDraft_shouldAllowManualTaskWhenBusinessLineHasNoProjectYet() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeHistoryMapper intakeHistoryMapper = mock(IntakeHistoryMapper.class);
        DevelopmentAnalysisMapper developmentAnalysisMapper = mock(DevelopmentAnalysisMapper.class);
        ProjectMapper projectMapper = mock(ProjectMapper.class);
        ProjectInvolvedSystemMapper involvedSystemMapper = mock(ProjectInvolvedSystemMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        GitlabRepositoryService gitlabRepositoryService = mock(GitlabRepositoryService.class);
        ProjectKnowledgeBaseService projectKnowledgeBaseService = mock(ProjectKnowledgeBaseService.class);
        CodexCliDevelopmentAnalysisGenerator analysisGenerator = mock(CodexCliDevelopmentAnalysisGenerator.class);
        WorkItemService workItemService = mock(WorkItemService.class);
        ObjectMapper objectMapper = new ObjectMapper();

        DevelopmentAnalysisService service = new DevelopmentAnalysisService(
                intakeMapper,
                intakeHistoryMapper,
                developmentAnalysisMapper,
                projectMapper,
                involvedSystemMapper,
                userMapper,
                gitlabRepositoryService,
                projectKnowledgeBaseService,
                analysisGenerator,
                workItemService,
                command -> { },
                objectMapper
        );

        IntakeRecordEntity intake = new IntakeRecordEntity();
        intake.setId(84L);
        intake.setDemandStatus("待评估");
        intake.setStructuredDataJson("""
                {"category":"需求录入","approvalCode":"202604030001","requirementType":"研发需求","requirementName":"三川-江苏新合作模式","projectHint":"物流平台","fields":[],"attachmentSummaries":[]}
                """);
        when(intakeMapper.findById(84L)).thenReturn(intake);
        when(projectMapper.findFirstDetailByBusinessLine("物流平台")).thenReturn(null);
        when(involvedSystemMapper.findSelectableByBusinessLine("物流平台"))
                .thenReturn(List.of(involvedSystem("thctay-saps-core-common")));

        AtomicReference<DevelopmentAnalysisEntity> savedEntity = new AtomicReference<>();
        when(developmentAnalysisMapper.findLatestByIntakeId(84L)).thenAnswer(invocation -> savedEntity.get());
        when(developmentAnalysisMapper.insert(any())).thenAnswer(invocation -> {
            DevelopmentAnalysisEntity entity = invocation.getArgument(0);
            entity.setId(84L);
            entity.setCreatedAt(LocalDateTime.now());
            entity.setUpdatedAt(LocalDateTime.now());
            savedEntity.set(entity);
            return 1;
        });

        var response = service.updateDraft(
                84L,
                new DevelopmentAnalysisDraftUpdateRequest(
                        "物流平台",
                        "4h",
                        "3h",
                        "1h",
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(new DevelopmentAnalysisDraftUpdateRequest.WorkItemDraft(
                                "补充三川江苏任务",
                                "人工补充任务",
                                "支持新合作模式",
                                "CODE_CHANGE",
                                List.of("订单流程"),
                                List.of("新增合作模式处理"),
                                List.of("thctay-saps-core-common"),
                                List.of(),
                                "MEDIUM",
                                null,
                                List.of(),
                                "3h",
                                "耿庆阳",
                                "2",
                                null,
                                null,
                                null,
                                null
                        ))
                ),
                "admin"
        );

        assertEquals("物流平台", response.draft().businessLine());
        assertEquals(null, response.draft().projectId());
        assertEquals("补充三川江苏任务", response.draft().workItems().getFirst().title());
        verify(developmentAnalysisMapper).insert(any());
        verify(intakeHistoryMapper).insert(any());
    }

    @Test
    void confirm_shouldCreateWorkItemsAndMoveDemandStatusToEvaluated() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeHistoryMapper intakeHistoryMapper = mock(IntakeHistoryMapper.class);
        DevelopmentAnalysisMapper developmentAnalysisMapper = mock(DevelopmentAnalysisMapper.class);
        ProjectMapper projectMapper = mock(ProjectMapper.class);
        ProjectInvolvedSystemMapper involvedSystemMapper = mock(ProjectInvolvedSystemMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        GitlabRepositoryService gitlabRepositoryService = mock(GitlabRepositoryService.class);
        ProjectKnowledgeBaseService projectKnowledgeBaseService = mock(ProjectKnowledgeBaseService.class);
        CodexCliDevelopmentAnalysisGenerator analysisGenerator = mock(CodexCliDevelopmentAnalysisGenerator.class);
        WorkItemService workItemService = mock(WorkItemService.class);
        ObjectMapper objectMapper = new ObjectMapper();

        DevelopmentAnalysisService service = new DevelopmentAnalysisService(
                intakeMapper,
                intakeHistoryMapper,
                developmentAnalysisMapper,
                projectMapper,
                involvedSystemMapper,
                userMapper,
                gitlabRepositoryService,
                projectKnowledgeBaseService,
                analysisGenerator,
                workItemService,
                command -> { },
                objectMapper
        );

        IntakeRecordEntity intake = new IntakeRecordEntity();
        intake.setId(41L);
        intake.setDemandStatus("待评估");
        intake.setStructuredDataJson("""
                {"category":"需求审批","requirementType":"研发需求","requirementName":"确认后进入研发前置状态"}
                """);
        when(intakeMapper.findById(41L)).thenReturn(intake);

        DevelopmentAnalysisDraft draft = new DevelopmentAnalysisDraft(
                "DRAFT",
                "账单管理",
                3L,
                "物流平台-账务系统",
                "http://gitlab/repo.git",
                null,
                null,
                null,
                "summary",
                List.of("新增确认流转"),
                List.of(),
                List.of(),
                List.of(),
                List.of("石浩"),
                List.of(new DevelopmentWorkItemDraft(
                        "确认流转改造",
                        "确认草稿后推进需求状态",
                        "新增确认流转",
                        "CODE_CHANGE",
                        List.of("需求状态流转"),
                        List.of("确认后状态改为待排期"),
                        List.of("需求管理"),
                        List.of("DevelopmentAnalysisService.confirm"),
                        "HIGH",
                        "intake",
                        List.of(),
                        "4h",
                        "石浩",
                        "2",
                        "2026/05/01",
                        "2026/05/03",
                        null,
                        null
                )),
                "8h",
                "4h",
                "4h",
                "2026/05/03",
                null,
                null,
                null,
                "RESERVED",
                "reserved",
                "2026-04-24T10:00:00",
                "Codex CLI"
        );
        DevelopmentAnalysisEntity entity = new DevelopmentAnalysisEntity();
        entity.setId(9L);
        entity.setIntakeId(41L);
        entity.setAnalysisStatus("DRAFT");
        entity.setDraftJson(writeDraft(objectMapper, draft));
        when(developmentAnalysisMapper.findLatestByIntakeId(41L)).thenReturn(entity);
        when(workItemService.create(any(), eq("admin"))).thenReturn(new WorkItemDetailResponse(
                9001L,
                "WI-9001",
                3L,
                "物流平台-账务系统",
                null,
                null,
                null,
                null,
                "TASK",
                "确认流转改造",
                "确认草稿后推进需求状态",
                "INTAKE",
                "需求管理",
                "P2",
                null,
                "待澄清",
                "admin",
                "石浩",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        ));

        service.confirm(41L, "admin");

        verify(workItemService).create(any(), eq("admin"));
        verify(intakeMapper).updateManagementFields(eq(41L), any(), any(), eq("待排期"));
        verify(developmentAnalysisMapper).updateStatus(eq(9L), eq("CONFIRMED"), eq(null), eq("RESERVED"), any(), eq("admin"));
        ArgumentCaptor<IntakeHistoryEntity> historyCaptor = forClass(IntakeHistoryEntity.class);
        verify(intakeHistoryMapper).insert(historyCaptor.capture());
        assertEquals("确认研发评估", historyCaptor.getValue().getActionSummary());
    }

    @Test
    void confirm_shouldMoveDemandStatusToPendingSchedulingWhenNoWorkItemsExist() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeHistoryMapper intakeHistoryMapper = mock(IntakeHistoryMapper.class);
        DevelopmentAnalysisMapper developmentAnalysisMapper = mock(DevelopmentAnalysisMapper.class);
        ProjectMapper projectMapper = mock(ProjectMapper.class);
        ProjectInvolvedSystemMapper involvedSystemMapper = mock(ProjectInvolvedSystemMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        GitlabRepositoryService gitlabRepositoryService = mock(GitlabRepositoryService.class);
        ProjectKnowledgeBaseService projectKnowledgeBaseService = mock(ProjectKnowledgeBaseService.class);
        CodexCliDevelopmentAnalysisGenerator analysisGenerator = mock(CodexCliDevelopmentAnalysisGenerator.class);
        WorkItemService workItemService = mock(WorkItemService.class);
        ObjectMapper objectMapper = new ObjectMapper();

        DevelopmentAnalysisService service = new DevelopmentAnalysisService(
                intakeMapper,
                intakeHistoryMapper,
                developmentAnalysisMapper,
                projectMapper,
                involvedSystemMapper,
                userMapper,
                gitlabRepositoryService,
                projectKnowledgeBaseService,
                analysisGenerator,
                workItemService,
                command -> { },
                objectMapper
        );

        IntakeRecordEntity intake = new IntakeRecordEntity();
        intake.setId(43L);
        intake.setDemandStatus("待评估");
        intake.setStructuredDataJson("""
                {"category":"需求审批","requirementType":"研发需求","requirementName":"只确认工时"}
                """);
        when(intakeMapper.findById(43L)).thenReturn(intake);

        DevelopmentAnalysisDraft draft = new DevelopmentAnalysisDraft(
                "DRAFT",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "summary",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "12h",
                "8h",
                "4h",
                null,
                null,
                null,
                null,
                "RESERVED",
                "reserved",
                "2026-04-24T10:00:00",
                "manual"
        );
        DevelopmentAnalysisEntity entity = new DevelopmentAnalysisEntity();
        entity.setId(10L);
        entity.setIntakeId(43L);
        entity.setAnalysisStatus("DRAFT");
        entity.setDraftJson(writeDraft(objectMapper, draft));
        when(developmentAnalysisMapper.findLatestByIntakeId(43L)).thenReturn(entity);

        DevelopmentAnalysisConfirmResponse response = service.confirm(43L, "admin");

        assertEquals(List.of(), response.workItemIds());
        verify(workItemService, org.mockito.Mockito.never()).create(any(), any());
        verify(intakeMapper).updateManagementFields(eq(43L), any(), any(), eq("待排期"));
        ArgumentCaptor<IntakeHistoryEntity> historyCaptor = forClass(IntakeHistoryEntity.class);
        verify(intakeHistoryMapper).insert(historyCaptor.capture());
        assertEquals("确认研发评估", historyCaptor.getValue().getActionSummary());
        assertTrue(historyCaptor.getValue().getDetailText().contains("未创建正式工作项"));
    }

    @Test
    void confirm_shouldNotRequireProjectWhenDraftHasBusinessLineAndSystems() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeHistoryMapper intakeHistoryMapper = mock(IntakeHistoryMapper.class);
        DevelopmentAnalysisMapper developmentAnalysisMapper = mock(DevelopmentAnalysisMapper.class);
        ProjectMapper projectMapper = mock(ProjectMapper.class);
        ProjectInvolvedSystemMapper involvedSystemMapper = mock(ProjectInvolvedSystemMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        GitlabRepositoryService gitlabRepositoryService = mock(GitlabRepositoryService.class);
        ProjectKnowledgeBaseService projectKnowledgeBaseService = mock(ProjectKnowledgeBaseService.class);
        CodexCliDevelopmentAnalysisGenerator analysisGenerator = mock(CodexCliDevelopmentAnalysisGenerator.class);
        WorkItemService workItemService = mock(WorkItemService.class);
        ObjectMapper objectMapper = new ObjectMapper();

        DevelopmentAnalysisService service = new DevelopmentAnalysisService(
                intakeMapper,
                intakeHistoryMapper,
                developmentAnalysisMapper,
                projectMapper,
                involvedSystemMapper,
                userMapper,
                gitlabRepositoryService,
                projectKnowledgeBaseService,
                analysisGenerator,
                workItemService,
                command -> { },
                objectMapper
        );

        IntakeRecordEntity intake = new IntakeRecordEntity();
        intake.setId(84L);
        intake.setDemandStatus("待评估");
        intake.setStructuredDataJson("""
                {"category":"需求录入","approvalCode":"202604030001","requirementType":"研发需求","requirementName":"三川-江苏新合作模式","projectHint":"物流平台","fields":[],"attachmentSummaries":[]}
                """);
        when(intakeMapper.findById(84L)).thenReturn(intake);

        DevelopmentAnalysisDraft draft = new DevelopmentAnalysisDraft(
                "DRAFT",
                "物流平台",
                null,
                null,
                null,
                null,
                null,
                null,
                "summary",
                List.of("新增三川江苏合作模式"),
                List.of(),
                List.of(),
                List.of(),
                List.of("耿庆阳"),
                List.of(new DevelopmentWorkItemDraft(
                        "三川江苏合作模式开发",
                        "支持新合作模式",
                        "新增三川江苏合作模式",
                        "CODE_CHANGE",
                        List.of("订单流程"),
                        List.of("新增合作模式处理"),
                        List.of("thctay-saps-core-common"),
                        List.of("需求原文"),
                        "MEDIUM",
                        null,
                        List.of(),
                        "3h",
                        "耿庆阳",
                        "2",
                        null,
                        null,
                        null,
                        null
                )),
                "4h",
                "3h",
                "1h",
                null,
                null,
                null,
                null,
                "RESERVED",
                "reserved",
                "2026-05-26T10:00:00",
                "manual"
        );
        DevelopmentAnalysisEntity entity = new DevelopmentAnalysisEntity();
        entity.setId(84L);
        entity.setIntakeId(84L);
        entity.setAnalysisStatus("DRAFT");
        entity.setDraftJson(writeDraft(objectMapper, draft));
        when(developmentAnalysisMapper.findLatestByIntakeId(84L)).thenReturn(entity);
        when(involvedSystemMapper.findSelectableByBusinessLine("物流平台"))
                .thenReturn(List.of(involvedSystem("thctay-saps-core-common")));

        DevelopmentAnalysisConfirmResponse response = service.confirm(84L, "admin");

        assertTrue(response.workItemIds().isEmpty());
        verify(workItemService, org.mockito.Mockito.never()).create(any(), eq("admin"));
        verify(intakeMapper).updateManagementFields(eq(84L), any(), any(), eq("待排期"));
        verify(developmentAnalysisMapper).updateStatus(eq(84L), eq("CONFIRMED"), eq(null), eq("RESERVED"), any(), eq("admin"));
        ArgumentCaptor<IntakeHistoryEntity> historyCaptor = forClass(IntakeHistoryEntity.class);
        verify(intakeHistoryMapper).insert(historyCaptor.capture());
        assertTrue(historyCaptor.getValue().getDetailText().contains("未创建正式工作项"));
    }

    @Test
    void confirm_shouldRejectWhenDemandIsNotPendingEvaluation() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeHistoryMapper intakeHistoryMapper = mock(IntakeHistoryMapper.class);
        DevelopmentAnalysisMapper developmentAnalysisMapper = mock(DevelopmentAnalysisMapper.class);
        ProjectMapper projectMapper = mock(ProjectMapper.class);
        ProjectInvolvedSystemMapper involvedSystemMapper = mock(ProjectInvolvedSystemMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        GitlabRepositoryService gitlabRepositoryService = mock(GitlabRepositoryService.class);
        ProjectKnowledgeBaseService projectKnowledgeBaseService = mock(ProjectKnowledgeBaseService.class);
        CodexCliDevelopmentAnalysisGenerator analysisGenerator = mock(CodexCliDevelopmentAnalysisGenerator.class);
        WorkItemService workItemService = mock(WorkItemService.class);
        ObjectMapper objectMapper = new ObjectMapper();

        DevelopmentAnalysisService service = new DevelopmentAnalysisService(
                intakeMapper,
                intakeHistoryMapper,
                developmentAnalysisMapper,
                projectMapper,
                involvedSystemMapper,
                userMapper,
                gitlabRepositoryService,
                projectKnowledgeBaseService,
                analysisGenerator,
                workItemService,
                command -> { },
                objectMapper
        );

        IntakeRecordEntity intake = new IntakeRecordEntity();
        intake.setId(44L);
        intake.setDemandStatus("已收录");
        intake.setStructuredDataJson("""
                {"category":"需求审批","requirementType":"研发需求","requirementName":"未进入评估的需求"}
                """);
        when(intakeMapper.findById(44L)).thenReturn(intake);

        DevelopmentAnalysisEntity entity = new DevelopmentAnalysisEntity();
        entity.setId(11L);
        entity.setIntakeId(44L);
        entity.setAnalysisStatus("DRAFT");
        entity.setDraftJson(writeDraft(objectMapper, minimalDraft("8h", "4h", List.of())));
        when(developmentAnalysisMapper.findLatestByIntakeId(44L)).thenReturn(entity);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.confirm(44L, "admin"));

        assertEquals("仅待评估需求允许确认研发评估", error.getMessage());
        verify(workItemService, org.mockito.Mockito.never()).create(any(), any());
        verify(intakeMapper, org.mockito.Mockito.never()).updateManagementFields(any(), any(), any(), any());
    }

    @Test
    void confirm_shouldRejectAlreadyConfirmedAnalysis() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeHistoryMapper intakeHistoryMapper = mock(IntakeHistoryMapper.class);
        DevelopmentAnalysisMapper developmentAnalysisMapper = mock(DevelopmentAnalysisMapper.class);
        ProjectMapper projectMapper = mock(ProjectMapper.class);
        ProjectInvolvedSystemMapper involvedSystemMapper = mock(ProjectInvolvedSystemMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        GitlabRepositoryService gitlabRepositoryService = mock(GitlabRepositoryService.class);
        ProjectKnowledgeBaseService projectKnowledgeBaseService = mock(ProjectKnowledgeBaseService.class);
        CodexCliDevelopmentAnalysisGenerator analysisGenerator = mock(CodexCliDevelopmentAnalysisGenerator.class);
        WorkItemService workItemService = mock(WorkItemService.class);
        ObjectMapper objectMapper = new ObjectMapper();

        DevelopmentAnalysisService service = new DevelopmentAnalysisService(
                intakeMapper,
                intakeHistoryMapper,
                developmentAnalysisMapper,
                projectMapper,
                involvedSystemMapper,
                userMapper,
                gitlabRepositoryService,
                projectKnowledgeBaseService,
                analysisGenerator,
                workItemService,
                command -> { },
                objectMapper
        );

        IntakeRecordEntity intake = new IntakeRecordEntity();
        intake.setId(45L);
        intake.setDemandStatus("待评估");
        intake.setStructuredDataJson("""
                {"category":"需求审批","requirementType":"研发需求","requirementName":"已确认分析"}
                """);
        when(intakeMapper.findById(45L)).thenReturn(intake);

        DevelopmentAnalysisEntity entity = new DevelopmentAnalysisEntity();
        entity.setId(12L);
        entity.setIntakeId(45L);
        entity.setAnalysisStatus("CONFIRMED");
        entity.setDraftJson(writeDraft(objectMapper, minimalDraft("8h", "4h", List.of())));
        when(developmentAnalysisMapper.findLatestByIntakeId(45L)).thenReturn(entity);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.confirm(45L, "admin"));

        assertEquals("当前任务评估已确认，不能重复确认", error.getMessage());
        verify(workItemService, org.mockito.Mockito.never()).create(any(), any());
        verify(intakeMapper, org.mockito.Mockito.never()).updateManagementFields(any(), any(), any(), any());
    }

    @Test
    void confirm_shouldRequireTotalAndTestingEffort() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeHistoryMapper intakeHistoryMapper = mock(IntakeHistoryMapper.class);
        DevelopmentAnalysisMapper developmentAnalysisMapper = mock(DevelopmentAnalysisMapper.class);
        ProjectMapper projectMapper = mock(ProjectMapper.class);
        ProjectInvolvedSystemMapper involvedSystemMapper = mock(ProjectInvolvedSystemMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        GitlabRepositoryService gitlabRepositoryService = mock(GitlabRepositoryService.class);
        ProjectKnowledgeBaseService projectKnowledgeBaseService = mock(ProjectKnowledgeBaseService.class);
        CodexCliDevelopmentAnalysisGenerator analysisGenerator = mock(CodexCliDevelopmentAnalysisGenerator.class);
        WorkItemService workItemService = mock(WorkItemService.class);
        ObjectMapper objectMapper = new ObjectMapper();

        DevelopmentAnalysisService service = new DevelopmentAnalysisService(
                intakeMapper,
                intakeHistoryMapper,
                developmentAnalysisMapper,
                projectMapper,
                involvedSystemMapper,
                userMapper,
                gitlabRepositoryService,
                projectKnowledgeBaseService,
                analysisGenerator,
                workItemService,
                command -> { },
                objectMapper
        );

        IntakeRecordEntity intake = new IntakeRecordEntity();
        intake.setId(46L);
        intake.setDemandStatus("待评估");
        intake.setStructuredDataJson("""
                {"category":"需求审批","requirementType":"研发需求","requirementName":"缺少工时"}
                """);
        when(intakeMapper.findById(46L)).thenReturn(intake);

        DevelopmentAnalysisEntity entity = new DevelopmentAnalysisEntity();
        entity.setId(13L);
        entity.setIntakeId(46L);
        entity.setAnalysisStatus("DRAFT");
        entity.setDraftJson(writeDraft(objectMapper, minimalDraft(null, null, List.of())));
        when(developmentAnalysisMapper.findLatestByIntakeId(46L)).thenReturn(entity);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.confirm(46L, "admin"));

        assertEquals("确认研发评估前必须填写总评估工时", error.getMessage());
        verify(workItemService, org.mockito.Mockito.never()).create(any(), any());
        verify(intakeMapper, org.mockito.Mockito.never()).updateManagementFields(any(), any(), any(), any());
    }

    private static DevelopmentAnalysisDraft minimalDraft(String totalEstimatedEffort,
                                                         String testingEstimatedEffort,
                                                         List<DevelopmentWorkItemDraft> workItems) {
        return new DevelopmentAnalysisDraft(
                "DRAFT",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "summary",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                workItems,
                totalEstimatedEffort,
                null,
                testingEstimatedEffort,
                null,
                null,
                null,
                null,
                "RESERVED",
                "reserved",
                "2026-04-24T10:00:00",
                "manual"
        );
    }

    private static String writeDraft(ObjectMapper objectMapper, DevelopmentAnalysisDraft draft) {
        try {
            return objectMapper.writeValueAsString(draft);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static DevelopmentAnalysisDraft readDraft(ObjectMapper objectMapper, String json) {
        try {
            return objectMapper.readValue(json, DevelopmentAnalysisDraft.class);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static ProjectInvolvedSystemEntity involvedSystem(String systemName) {
        ProjectInvolvedSystemEntity entity = new ProjectInvolvedSystemEntity();
        entity.setId(1L);
        entity.setSystemScope("BUSINESS_LINE");
        entity.setBusinessLine("账单管理");
        entity.setSystemName(systemName);
        entity.setEnabled(true);
        entity.setSortOrder(0);
        return entity;
    }
}
