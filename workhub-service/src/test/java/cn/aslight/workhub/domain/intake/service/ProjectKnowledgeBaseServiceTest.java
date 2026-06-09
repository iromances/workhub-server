package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.model.intake.IntakeAttachmentSummary;
import cn.aslight.workhub.model.intake.IntakeRecordEntity;
import cn.aslight.workhub.model.intake.IntakeStructuredData;
import cn.aslight.workhub.model.intake.IntakeStructuredField;
import cn.aslight.workhub.model.project.ProjectDetailResponse;
import cn.aslight.workhub.service.system.SysConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectKnowledgeBaseServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void buildContext_shouldReadRelevantMarkdownFromConfiguredVault() throws Exception {
        Path vault = tempDir.resolve("Company Obsidian Vault");
        Files.createDirectories(vault.resolve("供应链"));
        Files.writeString(vault.resolve("供应链").resolve("绑卡路由规则.md"), """
                # 绑卡路由规则

                嘉泰保理绑卡需要优先使用合作方汇付通道。
                涉及供应链科技业务线时需要关注扣款通道一致性。
                """);
        Files.writeString(vault.resolve("无关文档.md"), "这是无关内容。");

        SysConfigService sysConfigService = mock(SysConfigService.class);
        when(sysConfigService.findPlainValue(ProjectKnowledgeBaseService.CONFIG_GROUP, ProjectKnowledgeBaseService.CONFIG_KEY_VAULT_PATH))
                .thenReturn(vault.toString());
        ProjectKnowledgeBaseService service = new ProjectKnowledgeBaseService(sysConfigService);

        String context = service.buildContext(structuredData(), project());

        assertTrue(context.contains("供应链/绑卡路由规则.md"));
        assertTrue(context.contains("嘉泰保理绑卡需要优先使用合作方汇付通道"));
    }

    @Test
    void buildContext_shouldPreferDemandScopedPathWhenVaultContainsSiblingBusinessDocs() throws Exception {
        Path vault = tempDir.resolve("Scoped Vault");
        Files.createDirectories(vault.resolve("账单管理").resolve("趣学呗"));
        Files.createDirectories(vault.resolve("账单管理").resolve("保费分期"));
        Files.writeString(vault.resolve("账单管理").resolve("趣学呗").resolve("平台账户.md"), """
                # 趣学呗平台账户

                趣学呗平台账户切换时只需要维护 Nacos 配置和 account 账户数据。
                """);
        Files.writeString(vault.resolve("账单管理").resolve("保费分期").resolve("平台账户.md"), """
                # 保费分期平台账户

                保费分期平台账户涉及额外支付路由和分期规则。
                """);

        SysConfigService sysConfigService = mock(SysConfigService.class);
        when(sysConfigService.findPlainValue(ProjectKnowledgeBaseService.CONFIG_GROUP, ProjectKnowledgeBaseService.CONFIG_KEY_VAULT_PATH))
                .thenReturn(vault.toString());
        ProjectKnowledgeBaseService service = new ProjectKnowledgeBaseService(sysConfigService);

        String context = service.buildContext(quxuebeiStructuredData(), billingProject());

        assertTrue(context.contains("账单管理/趣学呗/平台账户.md"));
        assertTrue(context.contains("只需要维护 Nacos 配置和 account 账户数据"));
        assertFalse(context.contains("账单管理/保费分期/平台账户.md"));
        assertFalse(context.contains("额外支付路由和分期规则"));
    }

    @Test
    void buildContext_shouldUseBusinessLineChineseNameAsKnowledgeScope() throws Exception {
        Path vault = tempDir.resolve("Project Group Scoped Vault");
        Files.createDirectories(vault.resolve("保费分期"));
        Files.createDirectories(vault.resolve("账单管理"));
        Files.writeString(vault.resolve("保费分期").resolve("线下还款.md"), """
                # 保费分期线下还款

                保费分期渠道端线下还款需要生成线下还款申请单并按附言码核销。
                """);
        Files.writeString(vault.resolve("账单管理").resolve("线下还款.md"), """
                # 账单管理线下还款

                趣学呗线下还款当前不需要读取保费分期资料。
                """);

        SysConfigService sysConfigService = mock(SysConfigService.class);
        when(sysConfigService.findPlainValue(ProjectKnowledgeBaseService.CONFIG_GROUP, ProjectKnowledgeBaseService.CONFIG_KEY_VAULT_PATH))
                .thenReturn(vault.toString());
        ProjectKnowledgeBaseService service = new ProjectKnowledgeBaseService(sysConfigService);

        String context = service.buildContext(premiumInstallmentStructuredData(), premiumInstallmentProject());

        assertTrue(context.contains("保费分期/线下还款.md"));
        assertTrue(context.contains("按附言码核销"));
        assertFalse(context.contains("账单管理/线下还款.md"));
    }

    @Test
    void upsertRequirementIterationNote_shouldWriteMergedDemandMarkdownIntoProjectIterationFolder() throws Exception {
        Path vault = tempDir.resolve("Requirement Vault");

        SysConfigService sysConfigService = mock(SysConfigService.class);
        when(sysConfigService.findPlainValue(ProjectKnowledgeBaseService.CONFIG_GROUP, ProjectKnowledgeBaseService.CONFIG_KEY_VAULT_PATH))
                .thenReturn(vault.toString());
        ProjectKnowledgeBaseService service = new ProjectKnowledgeBaseService(sysConfigService);

        IntakeRecordEntity intake = new IntakeRecordEntity();
        intake.setId(220007L);
        intake.setRawContent("原始审批内容：趣学呗平台账户切换为新主体。");
        IntakeStructuredData structuredData = quxuebeiStructuredDataWithMaterials();

        ProjectKnowledgeBaseService.RequirementKnowledgeNote note =
                service.upsertRequirementIterationNote(intake, structuredData, billingProject());

        assertTrue(note.written());
        assertTrue(note.rawRelativePath().contains("raw/requirements/账单管理/趣学呗/2026/202604220007/source.md"));
        assertTrue(note.wikiRelativePath().contains("wiki/projects/账单管理/需求迭代/趣学呗/2026/202604220007-趣学呗平台账户展示切换.md"));
        assertTrue(note.wikiObsidianUrl().startsWith("obsidian://open?"));
        Path rawPath = vault.resolve(note.rawRelativePath());
        Path wikiPath = vault.resolve(note.wikiRelativePath());
        assertTrue(Files.exists(rawPath));
        assertTrue(Files.exists(wikiPath));
        String markdown = Files.readString(wikiPath);
        assertTrue(markdown.contains("# 趣学呗平台账户展示切换"));
        assertTrue(markdown.contains("raw/requirements/账单管理/趣学呗/2026/202604220007/source.md"));
        assertTrue(markdown.contains("截图.png"));
        assertTrue(markdown.contains("页面截图显示平台方账户字段需要切换。"));
        assertTrue(markdown.contains("原始审批内容：趣学呗平台账户切换为新主体。"));
        assertTrue(note.asPromptContext().contains("知识库需求迭代文件："));
    }

    private IntakeStructuredData structuredData() {
        return new IntakeStructuredData(
                "需求审批",
                "绑卡核验规则",
                "周拓",
                null,
                "202603250009",
                "2026/3/25 16:17",
                "研发需求",
                null,
                null,
                "绑卡核验",
                "新增绑卡核验规则",
                "供应链科技业务线需要增加嘉泰保理绑卡路由规则。",
                "供应链业务部",
                "供应链科技",
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
                "供应链科技",
                List.of(),
                List.of(),
                null
        );
    }

    private ProjectDetailResponse project() {
        return new ProjectDetailResponse(
                1L,
                "SUPPLY",
                "供应链项目",
                "研发",
                "供应链科技",
                "owner",
                "ACTIVE",
                "供应链科技相关系统",
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }

    private IntakeStructuredData quxuebeiStructuredData() {
        return new IntakeStructuredData(
                "需求审批",
                "趣学呗平台账户展示切换",
                "周拓",
                null,
                "202604220007",
                "2026/4/22 10:00",
                "研发需求",
                null,
                null,
                "趣学呗平台账户切换",
                "趣学呗平台账户展示切换",
                "账单管理下趣学呗平台账户展示切换为新主体账户。",
                "账单管理",
                "账单管理",
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
                "账单管理",
                List.of(),
                List.of(),
                null
        );
    }

    private IntakeStructuredData quxuebeiStructuredDataWithMaterials() {
        return new IntakeStructuredData(
                "需求审批",
                "趣学呗平台账户展示切换",
                "周拓",
                null,
                "202604220007",
                "2026/4/22 10:00",
                "研发需求",
                null,
                null,
                "趣学呗平台账户切换",
                "趣学呗平台账户展示切换",
                "账单管理下趣学呗平台账户展示切换为新主体账户。",
                "账单管理",
                "账单管理",
                "人工确认只改配置和账户数据。",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "账单管理",
                List.of(new IntakeStructuredField("平台方", "越学贝")),
                List.of(new IntakeAttachmentSummary("截图.png", "image/png", "页面截图显示平台方账户字段需要切换。")),
                null
        );
    }

    private ProjectDetailResponse billingProject() {
        return new ProjectDetailResponse(
                2L,
                "QXB",
                "趣学呗",
                "研发",
                "账单管理",
                "owner",
                "ACTIVE",
                "账单管理相关系统",
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }

    private IntakeStructuredData premiumInstallmentStructuredData() {
        return new IntakeStructuredData(
                "需求审批",
                "新增渠道端线下还款",
                "周拓",
                null,
                "202604300001",
                "2026/4/30 10:00",
                "研发需求",
                null,
                null,
                "线下还款",
                "新增渠道端线下还款",
                "渠道端客户账单新增线下还款按钮，生成还款申请并核销。",
                "供应链金融",
                "供应链金融",
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
                "供应链金融",
                List.of(),
                List.of(),
                null
        );
    }

    private ProjectDetailResponse premiumInstallmentProject() {
        return new ProjectDetailResponse(
                3L,
                "BF-FQ",
                "保费分期",
                "研发",
                "保费分期",
                "owner",
                "ACTIVE",
                "保费分期相关系统",
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }
}
