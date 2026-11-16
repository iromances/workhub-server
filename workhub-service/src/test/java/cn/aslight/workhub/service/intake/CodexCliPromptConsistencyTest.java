package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.model.intake.IntakeAttachmentSummary;
import cn.aslight.workhub.model.intake.IntakeRecordEntity;
import cn.aslight.workhub.model.intake.IntakeStructuredData;
import cn.aslight.workhub.model.intake.IntakeStructuredField;
import cn.aslight.workhub.model.project.ProjectDetailResponse;
import cn.aslight.workhub.model.system.UserOptionResponse;
import cn.aslight.workhub.service.ai.AiUseCaseDefinitions;
import cn.aslight.workhub.service.ai.TestPromptTemplates;
import cn.aslight.workhub.service.attachment.AttachmentService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 校验提示词迁入 AI 任务配置后，模板渲染出的最终提示词与迁移前 buildPrompt 逐字一致。
 * 期望值按迁移前的拼装逻辑独立重建，不读取迁移文件中的模板。
 */
class CodexCliPromptConsistencyTest {

    private static final String OLD_STRUCTURED_TEMPLATE = """
            你是 WorkHub 的需求结构化助手。
            请结合人工补充说明、截图、附件内容，抽取审批型需求字段，并严格按给定 JSON Schema 输出。
            要求：
            1. 只输出事实，不要猜测无法确认的信息。
            2. 能从截图或附件中确认的字段，优先写入标准字段。
            3. fields 必须保留你最终识别到的结构化字段清单。
            4. 如果图片或附件中能识别到更准确的值，可以覆盖人工说明里的模糊内容。
            5. 非图片附件已经预先提取为纯文本摘要，请优先基于这些摘要判断，不要猜测如何打开二进制文件。
            6. requirementType 只能输出“数据提取/运维”或“研发需求”。
            7. requirementDigest 输出给列表展示的极短摘要，尽量不超过 24 个字。
            8. 如果 requirementType 为“研发需求”，请在 developmentBranchName 中给出推荐研发分支名，格式优先使用 feature/summary_words_approvalCode；summary_words 使用英文单词或缩写，整体控制在 3 个英文单词以内，超过时使用常见缩写，所有单词和审批编号之间统一用下划线连接。非研发需求不要输出研发分支名。


            来源渠道：%s
            人工补充说明：
            %s
            """;

    private static final String OLD_SQL_DRAFT_TEMPLATE = """
            你是 WorkHub 的数据运维 SQL 草稿助手。
            当前任务：根据数据提取/运维类需求，生成给人工复制执行的 SQL 草稿。

            严格要求：
            1. 只生成 SQL 草稿，不要声称已经连接数据库或已经执行。
            2. 默认 SQL 方言为 MySQL。
            3. 只能生成只读 SELECT 查询，不要生成 INSERT、UPDATE、DELETE、DDL、CALL 或多语句。
            4. 如果能根据需求自行推断表名、字段名和筛选条件，可以直接生成 SQL，并把推断写入 assumptions。
            5. 如果关键表名、字段名或业务口径无法判断，请在 questions 中列出需要向需求方确认的问题；sql 可给出带 TODO 占位的草稿。
            6. 对时间范围、商户号、用户编号、状态值等可变条件，优先使用具名占位，例如 :start_time、:end_time、:merchant_no。
            7. riskWarnings 必须提示人工执行前确认库表环境、字段口径、数据权限和导出范围。
            8. 不要输出 Markdown，只返回符合 JSON Schema 的对象。

            需求主记录：
            - ID：%s
            - 来源：%s / %s
            - 发送人：%s
            - 接收时间：%s

            原始内容：
            %s
            """;

    private static final String OLD_CLARIFICATION_TEMPLATE = """
            你是 WorkHub 的系统架构师。要在进入设计系统和输出开发方案前，综合分析需求材料、项目知识库和代码仓库，判断有没有需求中不明确事项或者风险；

            待确认项（confirmationItems）准入标准——全部满足才允许提出：
            1. 综合需求材料、知识库和代码后，该问题仍无法确定唯一答案。能从证据中合理推断的，按推断口径实施，不要提问。
            2. "无法确定"必须基于已查证：说明已查过哪些需求条目、结构化字段、知识库内容和代码位置仍未找到可确定口径的依据；凭印象、凭行业惯例、凭"可能"的推测一律不得提问。
            3. 答案会实质影响实现行为（判定口径、数据范围、适用边界、数据结构或交互）才值得问；不影响实现的不要问。
            4. 每一项独立、可直接回复一个明确口径，不合并多个问题；evidence 写明已查证的来源位置与结论，不得以"证据不足"留白代替查证。

            风险项（riskItems）准入标准：
            1. 只列当前信息下可明确指出的风险：需求与代码现实冲突、依赖缺失、数据口径不一致、兼容性破坏等有具体位置的依据。
            2. 假设性风险（"如果数据不完整可能出错"一类）不列；能通过查证消除的疑虑先查证，不要列为风险。
            3. evidence 必须指向具体冲突点或依据位置。

            数量纪律：
            - 宁缺毋滥：未达准入标准就返回空数组，禁止为凑数输出低价值问题；确有必要才多列。
            - 不要输出研发任务，不要估算工时，不要修改代码。

            项目信息：
            - 项目：%s
            - 业务线：%s
            - 负责人：%s

            需求 Markdown 文件：
            %s

            需求信息：
            - 审批编号：%s
            - 需求名称：%s
            - 需求描述：%s
            - 业务线：%s
            - 备注：%s

            结构化识别字段：
            %s

            附件内容摘要：
            %s

            项目知识库参考：
            %s

            原始内容：
            %s""";

    private static final String OLD_DEVELOPMENT_TEMPLATE = """
            你是 WorkHub 的需求评估与交付方案架构师。
            输出必须符合 JSON Schema，只生成评估草稿，不修改代码、不创建工作项。

            工作流：
            1. 先读需求 Markdown 和补充材料，提炼业务需求点 requirementChangePoints。
            2. 再查业务线 Git 代码，确认现有系统、页面、接口、配置、表、定时任务和可复用逻辑；纯文案/运营需求可不查代码，但要在 evidenceRefs 说明。
            3. 最后按可排期研发任务拆 workItems，不按 Controller/Service/DAO 这类代码层级拆。

            任务要求：
            - title 用 Jira/禅道风格，格式类似“系统名 + 动词 + 业务对象 + 能力/规则”。
            - systemTags 只能写真实涉及系统，优先从“可选改造系统”中选择。
            - changePoints 表示“改地点”，必须按有序列表顺序列出，覆盖配置/SQL、接口/服务、任务调度、页面/权限、导出/核销、幂等/验证等真实工作。
            - relatedFiles/evidenceRefs 写代码或材料证据；涉及系统改造却没有代码证据时，confidence 置 LOW，并在 summary 说明。
            - 待确认项与风险由需求澄清阶段统一产出，本阶段不输出；代码调查中发现影响实现或验收的新问题，写入 summary 提示人工复核。
            - estimatedEffort 用小时，例如 1h、4h、8h；ownerUserName 只能从候选研发人员中选，无法判断填 null。

            项目信息：
            - 项目：%s
            - 业务线：%s
            - 负责人：%s

            候选研发人员：
            %s

            可选改造系统：
            %s

            需求 Markdown 文件：
            %s

            需求信息（仅用于和需求 Markdown 交叉校验，不作为替代输入）：
            - 审批编号：%s
            - 需求名称：%s
            - 需求描述：%s
            - 业务线：%s
            - 备注：%s

            结构化识别字段：
            %s

            附件内容摘要：
            %s

            项目知识库参考：
            %s

            原始内容：
            %s

            %s
            """;

    private static final String OLD_DEVELOPMENT_ADJUST_TEMPLATE = """
            你是 WorkHub 的研发拆解调整助手。
            根据用户反馈调整当前研发评估草稿，并重新输出完整 JSON 草稿；不要修改代码、不要创建工作项。

            调整规则：
            - 先修正 requirementChangePoints，再调整 workItems。
            - 任务按可排期交付项拆，不按 Controller/Service/DAO 等代码层拆。
            - title 用 Jira/禅道风格；systemTags 写真实涉及系统；changePoints 表示“改地点”，必须按有序列表顺序列出。
            - relatedFiles/evidenceRefs 保留或补充代码证据；删除无依据、过细或偏离需求的任务。
            - questions 只保留真正影响实现或验收的问题，不重复追问需求已明确的事实。

            候选研发人员：
            %s

            当前草稿：
            %s

            用户反馈：
            %s
            """;

    @Test
    void structuredExtract_promptStaysByteIdenticalAfterMigration() {
        CodexCliStructuredExtractor extractor = new CodexCliStructuredExtractor(null, new ObjectMapper());
        List<AttachmentService.AttachmentFileContext> attachments = List.of(
                new AttachmentService.AttachmentFileContext(1L, "截图", "approval.png", "/tmp/workhub-test/approval.png", "image/png"),
                new AttachmentService.AttachmentFileContext(2L, "附件", "requirement.docx", "/tmp/workhub-test/requirement.docx", "application/msword")
        );
        List<IntakeAttachmentSummary> summaries = List.of(
                new IntakeAttachmentSummary("requirement.docx", "DOCX", "需求名称：绑卡需求\n预估工时：2d")
        );
        String sourceChannel = "企业微信审批";
        String rawContent = "审批编号：202603250009";

        var variables = extractor.buildVariables(sourceChannel, rawContent, attachments, summaries);
        String rendered = TestPromptTemplates.render(AiUseCaseDefinitions.STRUCTURED_EXTRACT, variables);

        StringBuilder expected = new StringBuilder(OLD_STRUCTURED_TEMPLATE.formatted(sourceChannel, rawContent));
        if (!attachments.isEmpty()) {
            expected.append("\n上传文件清单：\n");
            for (AttachmentService.AttachmentFileContext attachment : attachments) {
                expected.append("- [").append(attachment.category()).append("] ").append(attachment.fileName())
                        .append(" (").append(attachment.contentType()).append(")\n");
            }
        }
        if (!summaries.isEmpty()) {
            expected.append("\n非图片附件文本摘要：\n");
            for (IntakeAttachmentSummary summary : summaries) {
                expected.append("### 文件：").append(summary.fileName()).append('\n')
                        .append("类型：").append(summary.fileType()).append('\n')
                        .append("正文摘要：\n").append(summary.summaryText()).append("\n\n");
            }
        }
        expected.append("请输出整理建议 JSON。");
        assertEquals(expected.toString(), rendered);
    }

    @Test
    void structuredExtract_emptySections_promptStaysByteIdentical() {
        CodexCliStructuredExtractor extractor = new CodexCliStructuredExtractor(null, new ObjectMapper());

        var variables = extractor.buildVariables("需求录入", "  ", List.of(), List.of());
        String rendered = TestPromptTemplates.render(AiUseCaseDefinitions.STRUCTURED_EXTRACT, variables);

        String expected = OLD_STRUCTURED_TEMPLATE.formatted("需求录入", "") + "请输出整理建议 JSON。";
        assertEquals(expected, rendered);
    }

    @Test
    void sqlDraft_promptStaysByteIdenticalAfterMigration() throws Exception {
        CodexCliSqlDraftGenerator generator = new CodexCliSqlDraftGenerator(null, new ObjectMapper());
        ObjectMapper objectMapper = new ObjectMapper();
        IntakeRecordEntity entity = new IntakeRecordEntity();
        entity.setId(1L);
        entity.setSourceType("需求截图附件录入");
        entity.setSourceChannel("需求录入");
        entity.setSenderName("ops-user");
        entity.setReceivedAt(LocalDateTime.of(2026, 4, 2, 10, 0));
        entity.setRawContent("导出 4 月支付失败订单");
        IntakeStructuredData structuredData = objectMapper.readValue("""
                {
                  "approvalCode": "OPS-001",
                  "proposerName": "运维同学",
                  "submittedTime": "2026/4/2 10:00",
                  "requirementType": "数据提取/运维",
                  "requirementName": "支付失败订单导出",
                  "requirementDigest": "导出支付失败订单",
                  "requirementSummary": "需要导出 4 月支付失败订单",
                  "businessLine": "资产业务",
                  "remark": "人工执行",
                  "fields": [],
                  "attachmentSummaries": [
                    {"fileName": "说明.docx", "fileType": "DOCX", "summaryText": "筛选条件：支付失败，时间为 4 月"}
                  ]
                }
                """, IntakeStructuredData.class);
        List<AttachmentService.AttachmentFileContext> attachments = List.of(
                new AttachmentService.AttachmentFileContext(1L, "截图", "demand.png", "/tmp/workhub-sql/demand.png", "image/png")
        );

        var variables = generator.buildVariables(entity, structuredData, attachments);
        String rendered = TestPromptTemplates.render(AiUseCaseDefinitions.SQL_DRAFT_GENERATE, variables);

        StringBuilder expected = new StringBuilder(OLD_SQL_DRAFT_TEMPLATE.formatted(
                entity.getId(),
                defaultValue(entity.getSourceType(), ""),
                defaultValue(entity.getSourceChannel(), ""),
                defaultValue(entity.getSenderName(), ""),
                entity.getReceivedAt(),
                defaultValue(entity.getRawContent(), "")));
        expected.append("\n结构化需求：\n");
        appendLine(expected, "审批编号", structuredData.approvalCode());
        appendLine(expected, "提出人", structuredData.proposerName());
        appendLine(expected, "提交时间", structuredData.submittedTime());
        appendLine(expected, "需求类型", structuredData.requirementType());
        appendLine(expected, "需求名称", structuredData.requirementName());
        appendLine(expected, "需求摘要", structuredData.requirementDigest());
        appendLine(expected, "需求描述", structuredData.requirementSummary());
        appendLine(expected, "业务线", structuredData.businessLine());
        appendLine(expected, "备注", structuredData.remark());
        List<IntakeAttachmentSummary> summaries = structuredData.attachmentSummaries();
        if (summaries != null && !summaries.isEmpty()) {
            expected.append("\n附件正文摘要：\n");
            for (IntakeAttachmentSummary summary : summaries) {
                expected.append("### ").append(defaultValue(summary.fileName(), "未命名附件"))
                        .append(" / ").append(defaultValue(summary.fileType(), "未知类型")).append('\n')
                        .append(defaultValue(summary.summaryText(), "")).append("\n\n");
            }
        }
        if (!attachments.isEmpty()) {
            expected.append("\n附件清单：\n");
            for (AttachmentService.AttachmentFileContext attachment : attachments) {
                expected.append("- ").append(defaultValue(attachment.fileName(), "未命名附件"))
                        .append(" / ").append(defaultValue(attachment.contentType(), "未知类型")).append('\n');
            }
        }
        expected.append("\n请生成 SQL 草稿 JSON。");
        assertEquals(expected.toString(), rendered);
    }

    @Test
    void clarification_promptStaysByteIdenticalAfterMigration() throws Exception {
        CodexCliClarificationAnalysisGenerator generator = new CodexCliClarificationAnalysisGenerator(null, new ObjectMapper());
        ObjectMapper objectMapper = new ObjectMapper();
        IntakeRecordEntity intake = new IntakeRecordEntity();
        intake.setRawContent("新增绑卡核验规则");
        IntakeStructuredData structuredData = objectMapper.readValue("""
                {
                  "approvalCode": "REQ-001",
                  "requirementType": "研发需求",
                  "requirementName": "绑卡核验规则",
                  "requirementSummary": "新增绑卡核验规则",
                  "businessLine": "供应链科技",
                  "remark": "重点需求",
                  "fields": [
                    {"label": "需求所属业务线", "value": "供应链科技"}
                  ],
                  "attachmentSummaries": [
                    {"fileName": "方案.docx", "fileType": "DOCX", "summaryText": "需要增加绑卡核验接口。"}
                  ]
                }
                """, IntakeStructuredData.class);
        ProjectDetailResponse project = new ProjectDetailResponse(
                1L, "SUPPLY", "供应链项目", "研发", "供应链科技", "owner", "ACTIVE", "供应链项目代码",
                LocalDateTime.now(), LocalDateTime.now());
        String markdown = "# 绑卡核验规则";
        String knowledge = "项目知识库";

        var variables = generator.buildVariables(intake, structuredData, project, markdown, knowledge);
        String rendered = TestPromptTemplates.render(AiUseCaseDefinitions.CLARIFICATION_ANALYZE, variables);

        String expected = OLD_CLARIFICATION_TEMPLATE.formatted(
                value(project.name()),
                value(project.businessLine()),
                value(project.ownerUserName()),
                value(markdown),
                value(structuredData.approvalCode()),
                value(structuredData.requirementNameOrTitle()),
                value(structuredData.requirementSummary()),
                value(structuredData.businessLine()),
                value(structuredData.remark()),
                formatStructuredFields(structuredData.fields()),
                formatAttachmentSummaries(structuredData.attachmentSummaries()),
                value(knowledge),
                value(intake.getRawContent())
        );
        assertEquals(expected, rendered);
    }

    @Test
    void developmentAnalyze_promptStaysByteIdenticalAfterMigration() throws Exception {
        CodexCliDevelopmentAnalysisGenerator generator = new CodexCliDevelopmentAnalysisGenerator(
                null, new ChinaWorkdayCalendar(), new ObjectMapper());
        ObjectMapper objectMapper = new ObjectMapper();
        IntakeRecordEntity entity = new IntakeRecordEntity();
        entity.setId(9L);
        entity.setRawContent("原始需求：需要新增绑卡核验规则");
        IntakeStructuredData structuredData = objectMapper.readValue("""
                {
                  "approvalCode": "202603250009",
                  "requirementType": "研发需求",
                  "requirementName": "绑卡核验规则",
                  "requirementSummary": "客户绑卡至嘉泰保理时增加核验规则。",
                  "businessLine": "供应链科技",
                  "remark": "最高优先级",
                  "fields": [
                    {"label": "需求所属业务线", "value": "供应链科技"}
                  ],
                  "attachmentSummaries": [
                    {"fileName": "方案.docx", "fileType": "DOCX", "summaryText": "附件正文摘要：需要增加绑卡核验接口和异常提示。"}
                  ]
                }
                """, IntakeStructuredData.class);
        ProjectDetailResponse project = new ProjectDetailResponse(
                1L, "SUPPLY", "供应链项目", "研发", "供应链科技", "owner", "ACTIVE", "供应链项目代码",
                LocalDateTime.now(), LocalDateTime.now());
        var repositoryBundle = new GitlabRepositoryService.GitlabRepositoryBundle(
                "workhub",
                Path.of("/tmp/workhub-demo"),
                List.of(
                        new GitlabRepositoryService.GitlabRepository("https://gitlab.example.com/workhub/demo.git", Path.of("/tmp/workhub-demo/demo")),
                        new GitlabRepositoryService.GitlabRepository("https://gitlab.example.com/workhub/settlement-service.git", Path.of("/tmp/workhub-demo/settlement-service"))
                )
        );
        List<UserOptionResponse> developers = List.of(new UserOptionResponse("dev-a", "研发A", "供应链科技"));
        String markdown = "知识库需求迭代文件：供应链科技/供应链项目/需求迭代/2026/202603250009-新增绑卡核验规则.md";
        String knowledge = "### 供应链/绑卡说明.md\n嘉泰保理绑卡需要遵循商户号路由规则。";

        var variables = generator.buildVariables(entity, structuredData, project, repositoryBundle, developers, markdown, knowledge);
        String rendered = TestPromptTemplates.render(AiUseCaseDefinitions.DEVELOPMENT_ANALYZE, variables);

        String expected = OLD_DEVELOPMENT_TEMPLATE.formatted(
                project.name(),
                project.businessLine(),
                project.ownerUserName(),
                formatDevelopers(developers),
                formatAvailableSystemTags(repositoryBundle),
                value(markdown),
                value(structuredData.approvalCode()),
                value(structuredData.requirementNameOrTitle()),
                value(structuredData.requirementSummary()),
                value(structuredData.businessLine()),
                value(structuredData.remark()),
                formatStructuredFields(structuredData.fields()),
                formatAttachmentSummaries(structuredData.attachmentSummaries()),
                value(knowledge),
                entity.getRawContent() == null ? "" : entity.getRawContent(),
                ""
        );
        assertEquals(expected, rendered);
    }

    @Test
    void developmentAdjust_promptStaysByteIdenticalAfterMigration() throws Exception {
        CodexCliDevelopmentAnalysisGenerator generator = new CodexCliDevelopmentAnalysisGenerator(
                null, new ChinaWorkdayCalendar(), new ObjectMapper());
        ObjectMapper objectMapper = new ObjectMapper();
        var currentDraft = objectMapper.readValue("""
                {"status": "DRAFT", "summary": "新增绑卡核验规则"}
                """, cn.aslight.workhub.model.intake.DevelopmentAnalysisDraft.class);
        List<UserOptionResponse> developers = List.of(new UserOptionResponse("dev-a", "研发A", "供应链科技"));
        String userMessage = "保留现有代码证据并调整任务描述";

        var variables = generator.buildAdjustmentVariables(currentDraft, userMessage, developers);
        String rendered = TestPromptTemplates.render(AiUseCaseDefinitions.DEVELOPMENT_ADJUST, variables);

        String expected = OLD_DEVELOPMENT_ADJUST_TEMPLATE.formatted(
                formatDevelopers(developers),
                objectMapper.writeValueAsString(currentDraft),
                userMessage
        );
        assertEquals(expected, rendered);
    }

    private String formatStructuredFields(List<IntakeStructuredField> fields) {
        if (fields == null || fields.isEmpty()) {
            return "- 无";
        }
        StringBuilder builder = new StringBuilder();
        for (IntakeStructuredField field : fields) {
            if (field == null || trimToNull(field.label()) == null) {
                continue;
            }
            builder.append("- ").append(field.label()).append("：").append(value(field.value())).append('\n');
        }
        return builder.isEmpty() ? "- 无" : builder.toString();
    }

    private String formatAttachmentSummaries(List<IntakeAttachmentSummary> summaries) {
        if (summaries == null || summaries.isEmpty()) {
            return "- 无";
        }
        StringBuilder builder = new StringBuilder();
        for (IntakeAttachmentSummary summary : summaries) {
            if (summary == null || trimToNull(summary.summaryText()) == null) {
                continue;
            }
            builder.append("- 来源文件：").append(value(summary.fileName()))
                    .append("（").append(value(summary.fileType())).append("）\n")
                    .append(summary.summaryText().trim()).append("\n");
        }
        return builder.isEmpty() ? "- 无" : builder.toString();
    }

    private String formatDevelopers(List<UserOptionResponse> developers) {
        if (developers == null || developers.isEmpty()) {
            return "- admin / WorkHub 管理员";
        }
        StringBuilder builder = new StringBuilder();
        for (UserOptionResponse developer : developers) {
            builder.append("- ").append(developer.userName()).append(" / ").append(developer.displayName()).append('\n');
        }
        return builder.toString();
    }

    private String formatAvailableSystemTags(GitlabRepositoryService.GitlabRepositoryBundle repositoryBundle) {
        List<String> tags = availableSystemTags(repositoryBundle);
        if (tags.isEmpty()) {
            return "- 无，请从仓库目录中识别微服务系统名";
        }
        return tags.stream().map(tag -> "- " + tag).reduce((left, right) -> left + "\n" + right).orElse("- 无");
    }

    private List<String> availableSystemTags(GitlabRepositoryService.GitlabRepositoryBundle repositoryBundle) {
        if (repositoryBundle == null || repositoryBundle.repositories().isEmpty()) {
            return List.of();
        }
        return repositoryBundle.repositories().stream()
                .map(repository -> inferRepositoryName(repository.repositoryUrl()))
                .map(this::trimToNull)
                .filter(value -> value != null)
                .distinct()
                .toList();
    }

    private String inferRepositoryName(String repositoryUrl) {
        String normalized = trimToNull(repositoryUrl);
        if (normalized == null) {
            return null;
        }
        int slashIndex = Math.max(normalized.lastIndexOf('/'), normalized.lastIndexOf(':'));
        String name = slashIndex >= 0 ? normalized.substring(slashIndex + 1) : normalized;
        if (name.endsWith(".git")) {
            name = name.substring(0, name.length() - 4);
        }
        return trimToNull(name);
    }

    private void appendLine(StringBuilder prompt, String label, String value) {
        String normalized = trimToNull(value);
        if (normalized != null) {
            prompt.append("- ").append(label).append("：").append(normalized).append('\n');
        }
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private String defaultValue(String value, String fallback) {
        String normalized = trimToNull(value);
        return normalized == null ? fallback : normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
