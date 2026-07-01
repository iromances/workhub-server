package cn.aslight.workhub.controller.intake;

import cn.aslight.workhub.model.intake.IntakeDetailResponse;
import cn.aslight.workhub.model.intake.IntakeHistoryResponse;
import cn.aslight.workhub.model.intake.DevelopmentAnalysisResponse;
import cn.aslight.workhub.model.intake.IntakeBusinessLineUpdateRequest;
import cn.aslight.workhub.model.intake.IntakeStructuredData;
import cn.aslight.workhub.model.intake.IntakeSummaryResponse;
import cn.aslight.workhub.model.intake.IntakeTodoResponse;
import cn.aslight.workhub.service.intake.DevelopmentAnalysisService;
import cn.aslight.workhub.service.intake.IntakeService;
import cn.aslight.workhub.service.intake.IntakeTodoService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IntakeControllerTest {

    private IntakeController controller(IntakeService intakeService) {
        return new IntakeController(intakeService, mock(DevelopmentAnalysisService.class));
    }

    @Test
    void pauseDemand_shouldExposePauseApi() throws Exception {
        IntakeService intakeService = mock(IntakeService.class);
        when(intakeService.pauseDemand(eq(9L), any(), eq("admin"))).thenReturn(simpleDetail(9L, "已暂停"));

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller(intakeService)).build();

        mockMvc.perform(post("/api/intake/9/pause")
                        .principal(new UsernamePasswordAuthenticationToken("admin", "N/A"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"等待外部联调环境","pauseDate":"2026-06-04"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.demandStatus").value("已暂停"));

        verify(intakeService).pauseDemand(eq(9L), any(), eq("admin"));
    }

    @Test
    void createTodo_shouldExposeTodoApi() throws Exception {
        IntakeService intakeService = mock(IntakeService.class);
        IntakeTodoService todoService = mock(IntakeTodoService.class);
        when(todoService.create(eq(9L), any(), eq("admin"))).thenReturn(new IntakeTodoResponse(
                1001L,
                9L,
                "确认资方接口口径",
                "需要和资方确认还款状态映射。",
                "待处理",
                "shihao",
                LocalDateTime.of(2026, 6, 5, 10, 0),
                null,
                null,
                LocalDateTime.of(2026, 6, 4, 10, 0),
                LocalDateTime.of(2026, 6, 4, 10, 0)
        ));

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new IntakeController(
                intakeService,
                mock(DevelopmentAnalysisService.class),
                todoService
        )).build();

        mockMvc.perform(post("/api/intake/9/todos")
                        .principal(new UsernamePasswordAuthenticationToken("admin", "N/A"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"确认资方接口口径","content":"需要和资方确认还款状态映射。","assigneeUserName":"shihao","plannedAt":"2026-06-05T10:00:00"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1001))
                .andExpect(jsonPath("$.data.status").value("待处理"));
    }

    @Test
    void deleteTodo_shouldExposeTodoDeleteApi() throws Exception {
        IntakeService intakeService = mock(IntakeService.class);
        IntakeTodoService todoService = mock(IntakeTodoService.class);

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new IntakeController(
                intakeService,
                mock(DevelopmentAnalysisService.class),
                todoService
        )).build();

        mockMvc.perform(delete("/api/intake/9/todos/1001")
                        .principal(new UsernamePasswordAuthenticationToken("admin", "N/A")))
                .andExpect(status().isOk());

        verify(todoService).delete(9L, 1001L, "admin");
    }

    @Test
    void analyzeDevelopment_shouldReturnPendingResponseQuickly() throws Exception {
        IntakeService intakeService = mock(IntakeService.class);
        DevelopmentAnalysisService developmentAnalysisService = mock(DevelopmentAnalysisService.class);
        when(developmentAnalysisService.analyze(5L, "账单管理", "admin")).thenReturn(
                new DevelopmentAnalysisResponse(
                        9L,
                        5L,
                        "PENDING",
                        "任务评估已提交，系统正在后台处理中",
                        null,
                        LocalDateTime.of(2026, 4, 24, 12, 0),
                        LocalDateTime.of(2026, 4, 24, 12, 0)
                )
        );

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new IntakeController(intakeService, developmentAnalysisService)).build();

        mockMvc.perform(post("/api/intake/5/development-analysis")
                        .principal(new UsernamePasswordAuthenticationToken("admin", "N/A"))
                        .param("businessLine", "账单管理"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.message").value("任务评估已提交，系统正在后台处理中"));
    }

    @Test
    void updateBusinessLine_shouldExposeBusinessLineApi() throws Exception {
        IntakeService intakeService = mock(IntakeService.class);
        IntakeDetailResponse detail = simpleDetail(9L, "已完成");
        when(intakeService.updateBusinessLine(eq(9L), any(IntakeBusinessLineUpdateRequest.class), eq("admin"))).thenReturn(detail);

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller(intakeService)).build();

        mockMvc.perform(post("/api/intake/9/business-line")
                        .principal(new UsernamePasswordAuthenticationToken("admin", "N/A"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"businessLine":"资产业务"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(9));

        verify(intakeService).updateBusinessLine(eq(9L), any(IntakeBusinessLineUpdateRequest.class), eq("admin"));
    }

    @Test
    void list_shouldExposeStructuredColumnsInApiResponse() throws Exception {
        IntakeService intakeService = mock(IntakeService.class);
        when(intakeService.list(eq(null), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null))).thenReturn(List.of(
                new IntakeSummaryResponse(
                        1L,
                        "需求截图附件录入",
                        "需求截图录入",
                        "zhoutuo",
                        "石浩",
                        LocalDateTime.of(2026, 3, 31, 12, 0),
                        "已收录",
                        "周拓",
                        "202603250009",
                        "2026/3/25 16:17",
                        "研发需求",
                        null,
                        null,
                        "沃橙绑卡至嘉泰保理",
                        "供应链业务部",
                        "沃橙项目增加客户绑卡至嘉泰保理的需求0325",
                        "为避免单一支付通道暂停风险，需要补充嘉泰保理易宝商户号绑卡方案。",
                        "供应链科技",
                        null,
                        "涉及沃诚项目额度释放，最高优先级。",
                        "24h",
                        "20h",
                        "4h",
                        "2026/3/31",
                        "2026/4/1",
                        "2026/4/2",
                        "2026/4/5",
                        "2026/4/1",
                        "无",
                        "2026/4/2",
                        "无",
                        "无",
                        "无",
                        "无",
                        "无",
                        "无",
                        "供应链科技",
                        List.of("支付服务"),
                        "SUCCEEDED",
                        "待整理",
                        null,
                        2L
                )
        ));

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller(intakeService)).build();

        mockMvc.perform(get("/api/intake").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].demandStatus").value("已收录"))
                .andExpect(jsonPath("$.data.items[0].proposerName").value("周拓"))
                .andExpect(jsonPath("$.data.items[0].approvalCode").value("202603250009"))
                .andExpect(jsonPath("$.data.items[0].submittedTime").value("2026/3/25 16:17"))
                .andExpect(jsonPath("$.data.items[0].requirementType").value("研发需求"))
                .andExpect(jsonPath("$.data.items[0].requirementDigest").value("沃橙绑卡至嘉泰保理"))
                .andExpect(jsonPath("$.data.items[0].department").value("供应链业务部"))
                .andExpect(jsonPath("$.data.items[0].requirementName").value("沃橙项目增加客户绑卡至嘉泰保理的需求0325"))
                .andExpect(jsonPath("$.data.items[0].estimatedEffort").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].totalEstimatedEffort").value("24h"))
                .andExpect(jsonPath("$.data.items[0].developmentEstimatedEffort").value("20h"))
                .andExpect(jsonPath("$.data.items[0].testingEstimatedEffort").value("4h"))
                .andExpect(jsonPath("$.data.items[0].plannedTestingStartDate").value("2026/4/2"))
                .andExpect(jsonPath("$.data.items[0].plannedReleaseDate").value("2026/4/5"))
                .andExpect(jsonPath("$.data.items[0].developmentStartedDate").value("2026/4/1"))
                .andExpect(jsonPath("$.data.items[0].testingStartedDate").value("2026/4/2"))
                .andExpect(jsonPath("$.data.items[0].releasedTime").value("无"))
                .andExpect(jsonPath("$.data.items[0].activeTodoCount").value(2))
                .andExpect(jsonPath("$.data.items[0].enrichmentStatus").value("SUCCEEDED"));
    }

    @Test
    void list_shouldPassBusinessLineFilterToService() throws Exception {
        IntakeService intakeService = mock(IntakeService.class);
        when(intakeService.list(eq(null), eq(null), eq(null), eq(null), eq("资产业务"), eq(null), eq(null), eq(null), eq(null)))
                .thenReturn(List.of());

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller(intakeService)).build();

        mockMvc.perform(get("/api/intake")
                        .param("businessLine", "资产业务")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        verify(intakeService).list(eq(null), eq(null), eq(null), eq(null), eq("资产业务"), eq(null), eq(null), eq(null), eq(null));
    }

    @Test
    void list_shouldPassRequirementTypeFilterToService() throws Exception {
        IntakeService intakeService = mock(IntakeService.class);
        when(intakeService.list(eq(null), eq(null), eq(null), eq(null), eq(null), eq("研发需求"), eq(null), eq(null), eq(null)))
                .thenReturn(List.of());

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller(intakeService)).build();

        mockMvc.perform(get("/api/intake")
                        .param("requirementType", "研发需求")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        verify(intakeService).list(eq(null), eq(null), eq(null), eq(null), eq(null), eq("研发需求"), eq(null), eq(null), eq(null));
    }

    @Test
    void advanceStage_shouldExposeUpdatedDemandStatus() throws Exception {
        IntakeService intakeService = mock(IntakeService.class);
        when(intakeService.advanceStage(eq(1L), any(), eq("admin"))).thenReturn(new IntakeDetailResponse(
                1L,
                "需求截图附件录入",
                "需求录入",
                null,
                "zhoutuo",
                "石浩",
                LocalDateTime.of(2026, 4, 1, 12, 0),
                "待排期",
                "原始内容",
                new IntakeStructuredData(
                        "需求审批",
                        "周拓的系统开发2.0",
                        "周拓",
                        null,
                        "202603250009",
                        "2026/3/25 16:17",
                        "研发需求",
                        "feature/req-202603250009",
                        null,
                        "沃橙绑卡",
                        "沃橙绑卡需求",
                        "描述",
                        "供应链业务部",
                        "供应链科技",
                        "高优",
                        "2026/04/05",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "供应链科技",
                        List.of(),
                        List.of(),
                        null
                ),
                List.of("支付服务"),
                List.<IntakeHistoryResponse>of(),
                "待整理",
                "SUCCEEDED",
                null,
                LocalDateTime.of(2026, 4, 1, 12, 1),
                null,
                List.of(),
                null,
                LocalDateTime.of(2026, 4, 1, 12, 0),
                LocalDateTime.of(2026, 4, 1, 12, 1)
        ));

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller(intakeService)).build();

        mockMvc.perform(post("/api/intake/1/stage-actions")
                        .principal(new UsernamePasswordAuthenticationToken("admin", "N/A"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {"action":"COMPLETE_EVALUATION","estimatedEffort":"2d","plannedDueDate":"2026/04/05"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.demandStatus").value("待排期"))
                .andExpect(jsonPath("$.data.structuredData.estimatedEffort").doesNotExist())
                .andExpect(jsonPath("$.data.structuredData.plannedDueDate").value("2026/04/05"));
    }

    @Test
    void advanceStageWithFiles_shouldAcceptMultipartRequest() throws Exception {
        IntakeService intakeService = mock(IntakeService.class);
        when(intakeService.advanceStage(eq(2L), any(), any(), eq("admin"))).thenReturn(new IntakeDetailResponse(
                2L,
                "需求截图附件录入",
                "需求录入",
                null,
                "ops-user",
                "石浩",
                LocalDateTime.of(2026, 4, 2, 12, 0),
                "已完成",
                "原始内容",
                new IntakeStructuredData(
                        "需求审批",
                        "数据修复申请",
                        "运维同学",
                        null,
                        "OPS-001",
                        "2026/4/2 10:00",
                        "数据提取/运维",
                        null,
                        null,
                        "批量修复还款数据",
                        "批量修复还款数据",
                        "描述",
                        "运营支持部",
                        "资产业务",
                        "紧急处理",
                        "2026/04/02",
                        "2026/04/02",
                        "5h",
                        null,
                        "2026/04/03",
                        "2026/04/04",
                        null,
                        null,
                        null,
                        "资产业务",
                        List.of(),
                        List.of(),
                        null
                ),
                List.of(),
                List.of(),
                "待整理",
                "SUCCEEDED",
                null,
                LocalDateTime.of(2026, 4, 2, 12, 1),
                null,
                List.of(),
                null,
                LocalDateTime.of(2026, 4, 2, 12, 0),
                LocalDateTime.of(2026, 4, 2, 12, 1)
        ));

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller(intakeService)).build();
        MockMultipartFile dataFile = new MockMultipartFile(
                "dataFiles",
                "delivery-result.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "demo".getBytes()
        );

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/intake/2/stage-actions")
                        .file(dataFile)
                        .param("action", "CONFIRM_ACCEPTANCE")
                        .param("acceptanceTime", "2026/04/04")
                        .principal(new UsernamePasswordAuthenticationToken("admin", "N/A")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.demandStatus").value("已完成"))
                .andExpect(jsonPath("$.data.structuredData.acceptanceTime").value("2026/04/04"));
    }

    @Test
    void generateSqlDraft_shouldCallService() throws Exception {
        IntakeService intakeService = mock(IntakeService.class);
        when(intakeService.generateSqlDraft(eq(3L), eq("admin"))).thenReturn(new IntakeDetailResponse(
                3L,
                "需求截图附件录入",
                "需求录入",
                null,
                "ops-user",
                "石浩",
                LocalDateTime.of(2026, 4, 2, 12, 0),
                "已收录",
                "原始内容",
                null,
                List.of(),
                List.of(),
                "待整理",
                "SUCCEEDED",
                null,
                LocalDateTime.of(2026, 4, 2, 12, 1),
                null,
                List.of(),
                null,
                LocalDateTime.of(2026, 4, 2, 12, 0),
                LocalDateTime.of(2026, 4, 2, 12, 1)
        ));

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller(intakeService)).build();

        mockMvc.perform(post("/api/intake/3/sql-draft")
                        .principal(new UsernamePasswordAuthenticationToken("admin", "N/A")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(3));
        verify(intakeService).generateSqlDraft(3L, "admin");
    }

    @Test
    void appendAttachments_shouldAcceptMultipartRequest() throws Exception {
        IntakeService intakeService = mock(IntakeService.class);
        when(intakeService.appendAttachments(eq(5L), any(), any(), eq("admin"))).thenReturn(simpleDetail(5L));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller(intakeService)).build();

        MockMultipartFile screenshot = new MockMultipartFile(
                "screenshots",
                "需求截图.png",
                "image/png",
                "image".getBytes()
        );
        MockMultipartFile attachment = new MockMultipartFile(
                "attachments",
                "需求说明.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "doc".getBytes()
        );

        mockMvc.perform(multipart("/api/intake/5/attachments")
                        .file(screenshot)
                        .file(attachment)
                        .principal(new UsernamePasswordAuthenticationToken("admin", "N/A")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(5));

        verify(intakeService).appendAttachments(eq(5L), any(), any(), eq("admin"));
    }

    @Test
    void deleteAttachment_shouldCallService() throws Exception {
        IntakeService intakeService = mock(IntakeService.class);
        when(intakeService.deleteAttachment(5L, 11L, "admin")).thenReturn(simpleDetail(5L));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller(intakeService)).build();

        mockMvc.perform(delete("/api/intake/5/attachments/11")
                        .principal(new UsernamePasswordAuthenticationToken("admin", "N/A")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(5));

        verify(intakeService).deleteAttachment(5L, 11L, "admin");
    }

    @Test
    void replaceAttachment_shouldAcceptMultipartRequest() throws Exception {
        IntakeService intakeService = mock(IntakeService.class);
        when(intakeService.replaceAttachment(eq(5L), eq(11L), any(), eq("admin"))).thenReturn(simpleDetail(5L));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller(intakeService)).build();

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "新需求说明.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "doc".getBytes()
        );

        mockMvc.perform(multipart("/api/intake/5/attachments/11/replace")
                        .file(file)
                        .principal(new UsernamePasswordAuthenticationToken("admin", "N/A")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(5));

        verify(intakeService).replaceAttachment(eq(5L), eq(11L), any(), eq("admin"));
    }

    @Test
    void retryEnrichment_shouldCallService() throws Exception {
        IntakeService intakeService = mock(IntakeService.class);
        when(intakeService.retryEnrichment(5L, "admin")).thenReturn(simpleDetail(5L));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller(intakeService)).build();

        mockMvc.perform(post("/api/intake/5/enrichment/retry")
                        .principal(new UsernamePasswordAuthenticationToken("admin", "N/A")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(5));

        verify(intakeService).retryEnrichment(5L, "admin");
    }

    @Test
    void delete_shouldReturnOk() throws Exception {
        IntakeService intakeService = mock(IntakeService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller(intakeService)).build();

        mockMvc.perform(delete("/api/intake/9")
                        .principal(new UsernamePasswordAuthenticationToken("admin", "N/A")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("OK"));
    }

    private IntakeDetailResponse simpleDetail(Long id) {
        return simpleDetail(id, "已收录");
    }

    private IntakeDetailResponse simpleDetail(Long id, String demandStatus) {
        return new IntakeDetailResponse(
                id,
                "需求截图附件录入",
                "需求录入",
                null,
                "admin",
                "石浩",
                LocalDateTime.of(2026, 4, 2, 12, 0),
                demandStatus,
                "原始内容",
                null,
                List.of(),
                List.of(),
                "待整理",
                "SUCCEEDED",
                null,
                LocalDateTime.of(2026, 4, 2, 12, 1),
                null,
                List.of(),
                null,
                LocalDateTime.of(2026, 4, 2, 12, 0),
                LocalDateTime.of(2026, 4, 2, 12, 1)
        );
    }
}
