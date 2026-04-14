package cn.aslight.workhub.controller.intake;

import cn.aslight.workhub.model.intake.IntakeDetailResponse;
import cn.aslight.workhub.model.intake.IntakeHistoryResponse;
import cn.aslight.workhub.model.intake.IntakeStructuredData;
import cn.aslight.workhub.model.intake.IntakeSummaryResponse;
import cn.aslight.workhub.service.intake.IntakeService;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IntakeControllerTest {

    @Test
    void list_shouldExposeStructuredColumnsInApiResponse() throws Exception {
        IntakeService intakeService = mock(IntakeService.class);
        when(intakeService.list(eq(null), eq(null), eq(null), eq(null), eq(null))).thenReturn(List.of(
                new IntakeSummaryResponse(
                        1L,
                        "需求截图附件录入",
                        "需求截图录入",
                        "zhoutuo",
                        LocalDateTime.of(2026, 3, 31, 12, 0),
                        "已收录",
                        "周拓",
                        "202603250009",
                        "2026/3/25 16:17",
                        "研发需求",
                        "沃橙绑卡至嘉泰保理",
                        "供应链业务部",
                        "沃橙项目增加客户绑卡至嘉泰保理的需求0325",
                        "为避免单一支付通道暂停风险，需要补充嘉泰保理易宝商户号绑卡方案。",
                        "供应链科技",
                        "涉及沃诚项目额度释放，最高优先级。",
                        "2d",
                        "2026/3/31",
                        "无",
                        "无",
                        "无",
                        "无",
                        "SUCCEEDED",
                        "待整理",
                        null
                )
        ));

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new IntakeController(intakeService)).build();

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
                .andExpect(jsonPath("$.data.items[0].estimatedEffort").value("2d"))
                .andExpect(jsonPath("$.data.items[0].releasedTime").value("无"))
                .andExpect(jsonPath("$.data.items[0].enrichmentStatus").value("SUCCEEDED"));
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
                LocalDateTime.of(2026, 4, 1, 12, 0),
                "已评估",
                "原始内容",
                new IntakeStructuredData(
                        "需求审批",
                        "周拓的系统开发2.0",
                        "周拓",
                        "dev-user",
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
                        "2d",
                        "2026/04/05",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "供应链科技",
                        List.of(),
                        List.of()
                ),
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

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new IntakeController(intakeService)).build();

        mockMvc.perform(post("/api/intake/1/stage-actions")
                        .principal(new UsernamePasswordAuthenticationToken("admin", "N/A"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"action":"EVALUATE_EFFORT","estimatedEffort":"2d","plannedDueDate":"2026/04/05"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.demandStatus").value("已评估"))
                .andExpect(jsonPath("$.data.structuredData.estimatedEffort").value("2d"))
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
                LocalDateTime.of(2026, 4, 2, 12, 0),
                "已上线",
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
                        "4h",
                        "2026/04/02",
                        "2026/04/02",
                        "5h",
                        null,
                        "2026/04/03",
                        null,
                        "2026/04/03",
                        "资产业务",
                        List.of(),
                        List.of()
                ),
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

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new IntakeController(intakeService)).build();
        MockMultipartFile dataFile = new MockMultipartFile(
                "dataFiles",
                "delivery-result.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "demo".getBytes()
        );

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/intake/2/stage-actions")
                        .file(dataFile)
                        .param("action", "COMPLETE_DELIVERY")
                        .param("actualEffort", "5h")
                        .param("actualCompletedTime", "2026/04/03")
                        .param("occurredAt", "2026/04/03")
                        .principal(new UsernamePasswordAuthenticationToken("admin", "N/A")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.demandStatus").value("已上线"))
                .andExpect(jsonPath("$.data.structuredData.actualEffort").value("5h"));
    }
}
