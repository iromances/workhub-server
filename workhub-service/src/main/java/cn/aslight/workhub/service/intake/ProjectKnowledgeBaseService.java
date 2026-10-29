package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.model.intake.IntakeStructuredData;
import cn.aslight.workhub.model.intake.IntakeRecordEntity;
import cn.aslight.workhub.model.intake.DevelopmentAnalysisDraft;
import cn.aslight.workhub.model.project.ProjectDetailResponse;
import cn.aslight.workhub.service.system.SysConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URLEncoder;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 项目知识库检索服务。
 *
 * <p>该服务只做本地确定性文本检索：根据需求和项目关键词从 Obsidian
 * Vault 中提取少量相关 Markdown 摘要。原始材料仅 raw，临时分析追加 notes，
 * 明确确认的评估才发布 wiki；写入保留历史版本。</p>
 */
@Service
public class ProjectKnowledgeBaseService {

    public static final String CONFIG_GROUP = "knowledge.project";
    public static final String CONFIG_KEY_VAULT_PATH = "vaultPath";

    private static final Logger log = LoggerFactory.getLogger(ProjectKnowledgeBaseService.class);
    private static final int MAX_SCAN_FILES = 800;
    private static final int MAX_SELECTED_FILES = 5;
    private static final int MAX_READ_CHARS = 120_000;
    private static final int MAX_SNIPPET_CHARS = 1_200;
    private static final int MAX_CONTEXT_CHARS = 6_000;

    private final SysConfigService sysConfigService;
    private final RequirementMaterialExportService materialExportService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate archiveTransaction;

    public ProjectKnowledgeBaseService(SysConfigService sysConfigService,
                                       RequirementMaterialExportService materialExportService,
                                       ObjectMapper objectMapper,
                                       PlatformTransactionManager transactionManager) {
        this.sysConfigService = sysConfigService;
        this.materialExportService = materialExportService;
        this.objectMapper = objectMapper;
        this.archiveTransaction = new TransactionTemplate(transactionManager);
        this.archiveTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 构建 AI 任务评估可用的知识库参考摘要。
     *
     * @param structuredData 结构化需求
     * @param project 匹配到的项目
     * @return 知识库摘要；未配置或未命中时返回提示文本
     */
    public String buildContext(IntakeStructuredData structuredData, ProjectDetailResponse project) {
        String configuredPath = trimToNull(sysConfigService.findPlainValue(CONFIG_GROUP, CONFIG_KEY_VAULT_PATH));
        if (configuredPath == null) {
            return "- 未配置项目知识库地址";
        }
        Path vaultRoot = Path.of(configuredPath).toAbsolutePath().normalize();
        if (!Files.isDirectory(vaultRoot)) {
            return "- 项目知识库目录不存在：" + vaultRoot;
        }

        KnowledgeQuery query = buildQuery(vaultRoot, structuredData, project);
        if (query.keywords().isEmpty()) {
            return "- 未提取到可用于检索知识库的关键词";
        }

        List<KnowledgeHit> hits = search(vaultRoot, query);
        if (hits.isEmpty()) {
            return "- 未在项目知识库中命中相关内容";
        }
        return formatHits(vaultRoot, hits);
    }

    /** 评估前仅归档原始材料；返回本轮不可变来源快照，供提示词与后续分析追溯。 */
    public RequirementKnowledgeNote upsertRequirementIterationNote(IntakeRecordEntity intake,
                                                                   IntakeStructuredData structuredData,
                                                                   ProjectDetailResponse project) {
        try {
            RequirementKnowledgeLocation location = resolveRequirementIterationLocation(structuredData, project, intake.getId());
            return KnowledgeArchiveFiles.locked(location.vaultRoot(), () -> {
                Path vault = location.vaultRoot();
                Path raw = location.rawPath();
                Path snapshot = raw.getParent().resolve("历史版本").resolve(UUID.randomUUID().toString());
                Path attachments = raw.getParent().resolve("附件");
                materialExportService.synchronizeVersioned(intake.getId(), vault, attachments, snapshot.resolve("附件"));
                String markdown = buildRawRequirementMarkdown(intake, structuredData, project, raw, vault);
                KnowledgeArchiveFiles.versionedWrite(vault, raw, markdown.getBytes(StandardCharsets.UTF_8));
                KnowledgeArchiveFiles.immutableWrite(vault, snapshot.resolve("source.md"), markdown.getBytes(StandardCharsets.UTF_8));
                Map<String, String> checksums = new LinkedHashMap<>();
                try (Stream<Path> files = Files.walk(snapshot)) {
                    for (Path file : files.filter(path -> Files.isRegularFile(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)).sorted().toList()) {
                        KnowledgeArchiveFiles.checked(vault, file);
                        checksums.put(snapshot.relativize(file).toString(), KnowledgeArchiveFiles.digest(Files.readAllBytes(file)));
                    }
                }
                String manifest = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of(
                        "intakeId", intake.getId(), "versionId", snapshot.getFileName().toString(),
                        "recordedAt", LocalDateTime.now().toString(), "filesSha256", checksums));
                KnowledgeArchiveFiles.immutableWrite(vault, snapshot.resolve("manifest.json"), manifest.getBytes(StandardCharsets.UTF_8));
                indexRawSource(location);
                return new RequirementKnowledgeNote(vault.relativize(snapshot.resolve("source.md")).toString(),
                        null, null, markdown, true);
            });
        } catch (IOException | IllegalStateException ex) {
            throw new IllegalStateException("原始材料归档失败，尚未执行 AI 分析：" + ex.getMessage(), ex);
        }
    }

    /** 只在业务事务提交后导出；失败单独记入需求历史，不把成功的 AI 结果改成失败。 */
    public void archiveAfterCommit(Runnable action, Consumer<Exception> onFailure) {
        Runnable export = () -> {
            try {
                archiveTransaction.executeWithoutResult(status -> action.run());
            } catch (Exception ex) {
                log.warn("知识库产出归档失败，可从已有分析结果重新归档", ex);
                try {
                    archiveTransaction.executeWithoutResult(status -> onFailure.accept(ex));
                } catch (Exception historyFailure) {
                    // 归档和错误通知均失败时保留业务结果，不能让异步调用方清空已保存的 AI 草稿。
                    log.error("归档错误写入需求历史失败，需独立重试归档", historyFailure);
                }
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { export.run(); }
            });
        } else {
            export.run();
        }
    }

    public void appendAnalysisNote(Long intakeId, Long analysisId, String phase, Object payload,
                                   String sourcePath, String operator) {
        Path vault = configuredVault();
        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
        try {
            KnowledgeArchiveFiles.locked(vault, () -> {
                // 来源绑定不是分析正文：用于同一澄清记录的后续回复和独立重试关联原输入。
                String family = phase.contains("澄清") ? "clarification" : "development";
                Path binding = KnowledgeArchiveFiles.checked(vault, vault.resolve("raw/公司项目/requirements/.workhub-analysis-sources")
                        .resolve(intakeId + "-" + analysisId + "-" + family + ".txt"));
                String resolvedSource = sourcePath;
                if ((resolvedSource == null || resolvedSource.isBlank()) && Files.exists(binding)) {
                    resolvedSource = Files.readString(binding);
                }
                String source = sourceLink(vault, resolvedSource);
                if (resolvedSource != null && !resolvedSource.isBlank()) {
                    KnowledgeArchiveFiles.versionedWrite(vault, binding, resolvedSource.getBytes(StandardCharsets.UTF_8));
                }
                String fingerprint = KnowledgeArchiveFiles.digest((intakeId + "|" + analysisId + "|" + family + "|" + resolvedSource + "|" + json)
                        .getBytes(StandardCharsets.UTF_8));
                String marker = "<!-- workhub-analysis:" + fingerprint + " -->";
                Path notes = KnowledgeArchiveFiles.checked(vault, vault.resolve("notes.md"));
                String previous = Files.exists(notes) ? Files.readString(notes) : "# 工作笔记\n";
                if (previous.contains(marker)) { return null; }
                String heading = "\n## 公司项目\n";
                if (!previous.contains(heading)) { previous += heading; }
                int start = previous.indexOf(heading) + heading.length();
                int end = previous.indexOf("\n## ", start);
                if (end < 0) { end = previous.length(); }
                String record = "\n\n### WorkHub 临时分析 · " + phase + " · 需求 " + intakeId
                        + "\n\n" + marker + "\n\n- 状态：待确认／过程记录，不代表正式审批\n- 分析 ID：" + analysisId
                        + "\n- 记录时间：" + LocalDateTime.now() + "\n- 操作者：" + operator
                        + "\n- 来源：" + source + "\n\n" + fenced(json, "json") + "\n";
                KnowledgeArchiveFiles.atomicWrite(vault, notes, (previous.substring(0, end) + record + previous.substring(end)).getBytes(StandardCharsets.UTF_8));
                return null;
            });
        } catch (IOException ex) {
            throw new IllegalStateException("临时分析追加 notes 失败", ex);
        }
    }

    public RequirementKnowledgeLocation publishConfirmedAnalysis(IntakeRecordEntity intake,
                                                                 IntakeStructuredData structuredData,
                                                                 ProjectDetailResponse project,
                                                                 Long analysisId, DevelopmentAnalysisDraft draft,
                                                                 String confirmedBy, String confirmedAt) {
        RequirementKnowledgeLocation location = resolveRequirementIterationLocation(structuredData, project, intake.getId());
        ObjectNode data = objectMapper.valueToTree(draft);
        data.remove("requirementWikiPath");
        data.remove("requirementWikiUrl");
        data.put("status", "CONFIRMED");
        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data);
        String fingerprint = KnowledgeArchiveFiles.digest((intake.getId() + "|" + analysisId + "|" + confirmedBy + "|" + json)
                .getBytes(StandardCharsets.UTF_8));
        String marker = "<!-- workhub-confirmed:" + fingerprint + " -->";
        try {
            KnowledgeArchiveFiles.locked(location.vaultRoot(), () -> {
                Path target = KnowledgeArchiveFiles.checked(location.vaultRoot(), location.wikiPath());
                // 同一确认结果重新归档保留首次记录时间，不生成伪修订。
                if (Files.exists(target) && Files.readString(target).contains(marker)) {
                    indexConfirmedReport(location);
                    return null;
                }
                String markdown = "# 研发评估：" + requirementTitleSegment(structuredData)
                        + "\n\n" + marker + "\n\n- 状态：已确认（不代表已开发或发布）\n- 需求 ID：" + intake.getId()
                        + "\n- 审批编号：" + requirementCodeSegment(structuredData, intake.getId()) + "\n- 分析 ID：" + analysisId
                        + "\n- 确认人：" + confirmedBy + "\n- 确认时间：" + confirmedAt
                        + "\n- 来源版本：" + sourceLink(location.vaultRoot(), draft.requirementRawPath())
                        + "\n- 临时分析：[[notes#公司项目]]\n\n## 评估摘要\n\n" + firstNonBlank(draft.summary(), "无")
                        + "\n\n## 经确认的评估数据\n\n" + fenced(json, "json") + "\n";
                KnowledgeArchiveFiles.versionedWrite(location.vaultRoot(), target, markdown.getBytes(StandardCharsets.UTF_8));
                indexConfirmedReport(location);
                return null;
            });
            return location;
        } catch (IOException ex) {
            throw new IllegalStateException("正式评估归档失败", ex);
        }
    }

    private void indexRawSource(RequirementKnowledgeLocation location) throws IOException {
        Path index = KnowledgeArchiveFiles.checked(location.vaultRoot(), location.vaultRoot().resolve("raw/公司项目/requirements/index.md"));
        String previous = Files.exists(index) ? Files.readString(index) : "# 公司项目原始需求材料\n";
        String relative = location.rawRelativePath().replace('\\', '/');
        String base = relative.substring(0, relative.length() - 3);
        if (!previous.contains("[[" + base)) {
            KnowledgeArchiveFiles.atomicWrite(location.vaultRoot(), index,
                    (previous + "\n- [[" + relative + "]]\n").getBytes(StandardCharsets.UTF_8));
        }
    }

    private void indexConfirmedReport(RequirementKnowledgeLocation location) throws IOException {
        Path index = KnowledgeArchiveFiles.checked(location.vaultRoot(), location.vaultRoot().resolve("wiki/公司项目/研发需求/index.md"));
        String previous = Files.exists(index) ? Files.readString(index) : "# 公司项目研发需求\n";
        String link = "[[" + location.wikiRelativePath().replace('\\', '/') + "]]";
        if (!previous.contains(link)) {
            String heading = "\n## 已确认自动评估\n";
            if (!previous.contains(heading)) { previous += heading; }
            int start = previous.indexOf(heading) + heading.length();
            int end = previous.indexOf("\n## ", start);
            if (end < 0) { end = previous.length(); }
            KnowledgeArchiveFiles.atomicWrite(location.vaultRoot(), index,
                    (previous.substring(0, end) + "\n- " + link + "\n" + previous.substring(end)).getBytes(StandardCharsets.UTF_8));
        }
    }

    private Path configuredVault() {
        String configured = trimToNull(sysConfigService.findPlainValue(CONFIG_GROUP, CONFIG_KEY_VAULT_PATH));
        if (configured == null) { throw new IllegalStateException("未配置项目知识库地址"); }
        return Path.of(configured).toAbsolutePath().normalize();
    }

    private String sourceLink(Path vault, String relative) throws IOException {
        if (relative == null || relative.isBlank()) { return "历史分析未记录输入版本，不能据此补造来源"; }
        String normalized = relative.replace("raw/requirements/", "raw/公司项目/requirements/");
        if (!normalized.startsWith("raw/公司项目/requirements/")) { throw new IOException("来源必须位于公司原始需求目录"); }
        Path target = KnowledgeArchiveFiles.checked(vault, vault.resolve(normalized));
        if (!target.startsWith(vault.resolve("raw/公司项目/requirements").normalize())) {
            throw new IOException("来源不能越出原始需求目录");
        }
        if (!Files.isRegularFile(target)) { throw new IOException("来源版本文件不存在：" + normalized); }
        return "[[" + vault.relativize(target).toString().replace('\\', '/') + "]]";
    }

    private String fenced(String content, String language) {
        int length = 3;
        while (content.contains("`".repeat(length))) { length++; }
        String fence = "`".repeat(length);
        return fence + language + "\n" + content + "\n" + fence + "\n";
    }

    public RequirementKnowledgeLocation resolveRequirementIterationLocation(IntakeStructuredData structuredData,
                                                                           ProjectDetailResponse project, Long intakeId) {
        String configuredPath = trimToNull(sysConfigService.findPlainValue(CONFIG_GROUP, CONFIG_KEY_VAULT_PATH));
        if (configuredPath == null) {
            throw new IllegalStateException("未配置项目知识库地址");
        }
        Path vaultRoot = Path.of(configuredPath).toAbsolutePath().normalize();
        Path rawPath = buildRawRequirementPath(vaultRoot, structuredData, project, intakeId);
        Path wikiPath = buildWikiRequirementPath(vaultRoot, structuredData, project, intakeId);
        String rawRelativePath = vaultRoot.relativize(rawPath).toString();
        String wikiRelativePath = vaultRoot.relativize(wikiPath).toString();
        return new RequirementKnowledgeLocation(
                vaultRoot,
                rawPath,
                wikiPath,
                rawRelativePath,
                wikiRelativePath,
                buildObsidianUrl(vaultRoot, wikiRelativePath)
        );
    }

    private Path buildRawRequirementPath(Path vaultRoot, IntakeStructuredData structuredData, ProjectDetailResponse project, Long intakeId) {
        return vaultRoot
                .resolve("raw")
                .resolve("公司项目")
                .resolve("requirements")
                .resolve(businessLineSegment(project))
                .resolve(projectNameSegment(project))
                .resolve(resolveRequirementYear(structuredData))
                .resolve(requirementCodeSegment(structuredData, intakeId))
                .resolve("source.md")
                .normalize();
    }

    private Path buildWikiRequirementPath(Path vaultRoot, IntakeStructuredData structuredData, ProjectDetailResponse project, Long intakeId) {
        return vaultRoot
                .resolve("wiki")
                .resolve("公司项目")
                .resolve("研发需求")
                .resolve(businessLineSegment(project))
                .resolve("需求迭代")
                .resolve(projectNameSegment(project))
                .resolve(resolveRequirementYear(structuredData))
                .resolve(requirementCodeSegment(structuredData, intakeId) + "-" + requirementTitleSegment(structuredData))
                .resolve("研发评估.md")
                .normalize();
    }

    private String businessLineSegment(ProjectDetailResponse project) {
        String businessLine = safePathSegment(firstNonBlank(project == null ? null : project.businessLine(), "未归类业务线"));
        return businessLine;
    }

    private String projectNameSegment(ProjectDetailResponse project) {
        String businessLine = businessLineSegment(project);
        String projectName = safePathSegment(firstNonBlank(project == null ? null : project.name(), businessLine));
        return projectName;
    }

    private String requirementCodeSegment(IntakeStructuredData structuredData, Long intakeId) {
        return safePathSegment(firstNonBlank(structuredData == null ? null : structuredData.approvalCode(), "INTAKE-" + intakeId));
    }

    private String requirementTitleSegment(IntakeStructuredData structuredData) {
        return safePathSegment(firstNonBlank(
                structuredData == null ? null : structuredData.requirementNameOrTitle(),
                structuredData == null ? null : structuredData.requirementDigest(),
                "未命名需求"
        ));
    }

    private String resolveRequirementYear(IntakeStructuredData structuredData) {
        String code = structuredData == null ? null : trimToNull(structuredData.approvalCode());
        if (code != null && code.matches("\\d{12,}")) {
            return code.substring(0, 4);
        }
        String submittedTime = structuredData == null ? null : trimToNull(structuredData.submittedTime());
        if (submittedTime != null && submittedTime.length() >= 4 && submittedTime.substring(0, 4).matches("\\d{4}")) {
            return submittedTime.substring(0, 4);
        }
        return String.valueOf(LocalDate.now().getYear());
    }

    private String buildRawRequirementMarkdown(IntakeRecordEntity intake,
                                               IntakeStructuredData structuredData,
                                               ProjectDetailResponse project,
                                               Path rawPath,
                                               Path vaultRoot) {
        String title = firstNonBlank(
                structuredData == null ? null : structuredData.requirementNameOrTitle(),
                structuredData == null ? null : structuredData.requirementDigest(),
                "未命名需求"
        );
        StringBuilder builder = new StringBuilder();
        builder.append("# ").append(title).append("\n\n");
        builder.append("> 本文件由 WorkHub 在 AI 任务评估前自动归档，用于保存散落的原始需求材料；本文件不总结需求点，不拆任务。\n\n");
        builder.append("## 元信息\n\n");
        appendMetadata(builder, "知识库路径", vaultRoot.relativize(rawPath).toString());
        appendMetadata(builder, "需求ID", intake == null ? null : String.valueOf(intake.getId()));
        appendMetadata(builder, "审批编号", structuredData == null ? null : structuredData.approvalCode());
        appendMetadata(builder, "业务线", project == null ? null : project.businessLine());
        appendMetadata(builder, "项目", project == null ? null : project.name());
        appendMetadata(builder, "需求类型", structuredData == null ? null : structuredData.requirementType());
        appendMetadata(builder, "提出人", structuredData == null ? null : structuredData.proposerName());
        appendMetadata(builder, "提交时间", structuredData == null ? null : structuredData.submittedTime());
        appendMetadata(builder, "业务线", structuredData == null ? null : structuredData.businessLine());
        appendMetadata(builder, "部门", structuredData == null ? null : structuredData.department());

        builder.append("\n## 原始内容\n\n");
        builder.append(fenced(intake.getRawContent() == null ? "" : intake.getRawContent(), "text"));
        builder.append("\n原始截图与附件保存在同级 `附件/`；来源解析与 AI 判断不代替原始文件。\n");
        return builder.toString();
    }

    private void appendMetadata(StringBuilder builder, String label, String value) {
        builder.append("- **")
                .append(label)
                .append("**：")
                .append(firstNonBlank(value, "-"))
                .append('\n');
    }

    private String buildObsidianUrl(Path vaultRoot, String relativePath) {
        String vaultName = vaultRoot.getFileName() == null ? null : vaultRoot.getFileName().toString();
        if (trimToNull(vaultName) == null || trimToNull(relativePath) == null) {
            return null;
        }
        return "obsidian://open?vault="
                + URLEncoder.encode(vaultName, StandardCharsets.UTF_8)
                + "&file="
                + URLEncoder.encode(relativePath, StandardCharsets.UTF_8);
    }

    private String safePathSegment(String value) {
        String normalized = firstNonBlank(value, "未命名")
                .replaceAll("[\\\\/:*?\"<>|#\\[\\]\\n\\r\\t]", "_")
                .replaceAll("\\s+", " ")
                .trim();
        if (normalized.length() > 80) {
            normalized = normalized.substring(0, 80).trim();
        }
        return normalized.isBlank() || normalized.equals(".") || normalized.equals("..") ? "未命名" : normalized;
    }

    private List<KnowledgeHit> search(Path vaultRoot, KnowledgeQuery query) {
        try (Stream<Path> stream = Files.walk(vaultRoot)) {
            List<KnowledgeHit> hits = stream
                    .filter(Files::isRegularFile)
                    .filter(this::isSupportedDocument)
                    .limit(MAX_SCAN_FILES)
                    .map(path -> scoreFile(vaultRoot, path, query))
                    .filter(hit -> hit.score() > 0)
                    .toList();
            int bestBusinessLineMatchCount = hits.stream()
                    .mapToInt(KnowledgeHit::businessLineMatchCount)
                    .max()
                    .orElse(0);
            Stream<KnowledgeHit> scopedHits = hits.stream();
            if (!query.businessLineScopeKeywords().isEmpty() && bestBusinessLineMatchCount > 0) {
                scopedHits = scopedHits.filter(hit -> hit.businessLineMatchCount() == bestBusinessLineMatchCount);
            }
            List<KnowledgeHit> businessLineScopedHits = scopedHits.toList();
            int bestScopeMatchCount = businessLineScopedHits.stream()
                    .mapToInt(KnowledgeHit::scopeMatchCount)
                    .max()
                    .orElse(0);
            scopedHits = businessLineScopedHits.stream();
            if (!query.pathScopeKeywords().isEmpty() && bestScopeMatchCount > 0) {
                scopedHits = scopedHits.filter(hit -> hit.scopeMatchCount() == bestScopeMatchCount);
            }
            return scopedHits
                    .sorted(Comparator.comparingInt(KnowledgeHit::score).reversed())
                    .limit(MAX_SELECTED_FILES)
                    .toList();
        } catch (IOException ex) {
            log.warn("Project knowledge base search failed. vaultRoot={}", vaultRoot, ex);
            return List.of();
        }
    }

    private KnowledgeHit scoreFile(Path vaultRoot, Path path, KnowledgeQuery query) {
        String fileName = path.getFileName().toString();
        String content = readText(path);
        String searchable = (fileName + "\n" + content).toLowerCase();
        String relativePath = vaultRoot.relativize(path).toString().toLowerCase();
        int score = 0;
        String bestKeyword = null;
        int businessLineMatchCount = 0;
        int scopeMatchCount = 0;
        for (String businessLineKeyword : query.businessLineScopeKeywords()) {
            String normalized = businessLineKeyword.toLowerCase();
            if (relativePath.contains(normalized)) {
                businessLineMatchCount++;
                score += 50;
                bestKeyword = businessLineKeyword;
            }
        }
        for (String scopeKeyword : query.pathScopeKeywords()) {
            String normalized = scopeKeyword.toLowerCase();
            if (relativePath.contains(normalized)) {
                scopeMatchCount++;
                score += 20;
                bestKeyword = scopeKeyword;
            }
        }
        for (String keyword : query.keywords()) {
            String normalized = keyword.toLowerCase();
            if (fileName.toLowerCase().contains(normalized)) {
                score += 5;
                bestKeyword = keyword;
            }
            if (searchable.contains(normalized)) {
                score += Math.max(1, Math.min(4, normalized.length() / 3));
                if (bestKeyword == null) {
                    bestKeyword = keyword;
                }
            }
        }
        return new KnowledgeHit(path, score, businessLineMatchCount, scopeMatchCount, extractSnippet(content, bestKeyword));
    }

    private String readText(Path path) {
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            if (content.length() <= MAX_READ_CHARS) {
                return content;
            }
            return content.substring(0, MAX_READ_CHARS);
        } catch (Exception ex) {
            log.debug("Skip unreadable knowledge file. path={}", path, ex);
            return "";
        }
    }

    private String extractSnippet(String content, String keyword) {
        String normalizedContent = trimToNull(content);
        if (normalizedContent == null) {
            return "";
        }
        if (normalizedContent.length() <= MAX_SNIPPET_CHARS) {
            return normalizedContent;
        }
        int start = 0;
        String normalizedKeyword = trimToNull(keyword);
        if (normalizedKeyword != null) {
            int index = normalizedContent.toLowerCase().indexOf(normalizedKeyword.toLowerCase());
            if (index >= 0) {
                start = Math.max(0, index - 240);
            }
        }
        int end = Math.min(normalizedContent.length(), start + MAX_SNIPPET_CHARS);
        return normalizedContent.substring(start, end);
    }

    private String formatHits(Path vaultRoot, List<KnowledgeHit> hits) {
        StringBuilder builder = new StringBuilder();
        builder.append("以下内容来自项目知识库，仅用于辅助判断不确定事项；如与需求或代码冲突，以需求和代码为准。\n");
        for (KnowledgeHit hit : hits) {
            builder.append("\n### ")
                    .append(vaultRoot.relativize(hit.path()))
                    .append("\n")
                    .append(hit.snippet())
                    .append('\n');
            if (builder.length() >= MAX_CONTEXT_CHARS) {
                break;
            }
        }
        if (builder.length() > MAX_CONTEXT_CHARS) {
            return builder.substring(0, MAX_CONTEXT_CHARS);
        }
        return builder.toString();
    }

    private KnowledgeQuery buildQuery(Path vaultRoot, IntakeStructuredData structuredData, ProjectDetailResponse project) {
        List<String> keywords = buildKeywords(structuredData, project);
        List<String> businessLineScopeKeywords = buildBusinessLineScopeKeywords(vaultRoot, project);
        List<String> pathScopeKeywords = buildPathScopeKeywords(vaultRoot, structuredData);
        return new KnowledgeQuery(keywords, businessLineScopeKeywords, pathScopeKeywords);
    }

    private List<String> buildKeywords(IntakeStructuredData structuredData, ProjectDetailResponse project) {
        Set<String> keywords = new LinkedHashSet<>();
        if (project != null) {
            addKeyword(keywords, project.name());
            addKeyword(keywords, project.businessLine());
            addKeyword(keywords, project.description());
        }
        if (structuredData != null) {
            addKeyword(keywords, structuredData.projectHint());
            addKeyword(keywords, structuredData.businessLine());
            addKeyword(keywords, structuredData.department());
            addKeyword(keywords, structuredData.requirementNameOrTitle());
            addKeyword(keywords, structuredData.requirementDigest());
            addKeyword(keywords, structuredData.requirementSummary());
            addKeyword(keywords, structuredData.remark());
            if (structuredData.fields() != null) {
                for (var field : structuredData.fields()) {
                    if (field == null) {
                        continue;
                    }
                    addKeyword(keywords, field.value());
                }
            }
        }
        return new ArrayList<>(keywords);
    }

    private List<String> buildPathScopeKeywords(Path vaultRoot, IntakeStructuredData structuredData) {
        String demandCorpus = buildDemandCorpus(structuredData);
        if (demandCorpus.isBlank()) {
            return List.of();
        }
        Set<String> vocabulary = collectPathVocabulary(vaultRoot);
        return vocabulary.stream()
                .filter(term -> demandCorpus.contains(term))
                .sorted(Comparator.comparingInt(String::length).reversed())
                .limit(6)
                .toList();
    }

    private List<String> buildBusinessLineScopeKeywords(Path vaultRoot, ProjectDetailResponse project) {
        String businessLineName = project == null ? null : trimToNull(project.businessLine());
        if (businessLineName == null) {
            return List.of();
        }
        Set<String> vocabulary = collectPathVocabulary(vaultRoot);
        List<String> matchedTerms = vocabulary.stream()
                .filter(term -> businessLineName.contains(term))
                .sorted(Comparator.comparingInt(String::length).reversed())
                .limit(4)
                .toList();
        if (!matchedTerms.isEmpty()) {
            return matchedTerms;
        }
        return List.of(businessLineName);
    }

    private Set<String> collectPathVocabulary(Path vaultRoot) {
        Set<String> vocabulary = new LinkedHashSet<>();
        try (Stream<Path> stream = Files.walk(vaultRoot)) {
            stream
                    .limit(MAX_SCAN_FILES)
                    .forEach(path -> addPathTerms(vocabulary, vaultRoot.relativize(path).toString()));
        } catch (IOException ex) {
            log.debug("Project knowledge base path vocabulary scan failed. vaultRoot={}", vaultRoot, ex);
        }
        return vocabulary;
    }

    private void addPathTerms(Set<String> vocabulary, String relativePath) {
        String normalizedPath = relativePath.replace('\\', '/');
        for (String part : normalizedPath.split("[/\\s,，。；;、()（）\\[\\]【】._-]+")) {
            String token = trimToNull(part);
            if (token != null && token.length() >= 2 && token.length() <= 32) {
                vocabulary.add(token);
            }
        }
    }

    private String buildDemandCorpus(IntakeStructuredData structuredData) {
        if (structuredData == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        appendDemandText(builder, structuredData.projectHint());
        appendDemandText(builder, structuredData.businessLine());
        appendDemandText(builder, structuredData.department());
        appendDemandText(builder, structuredData.requirementNameOrTitle());
        appendDemandText(builder, structuredData.requirementDigest());
        appendDemandText(builder, structuredData.requirementSummary());
        appendDemandText(builder, structuredData.remark());
        if (structuredData.fields() != null) {
            for (var field : structuredData.fields()) {
                if (field != null) {
                    appendDemandText(builder, field.value());
                }
            }
        }
        return builder.toString();
    }

    private void appendDemandText(StringBuilder builder, String value) {
        String normalized = trimToNull(value);
        if (normalized != null) {
            builder.append(normalized).append('\n');
        }
    }

    private void addKeyword(Set<String> keywords, String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return;
        }
        if (normalized.length() <= 40) {
            keywords.add(normalized);
        }
        for (String part : normalized.split("[\\s,，。；;、/\\\\()（）\\[\\]【】]+")) {
            String token = trimToNull(part);
            if (token != null && token.length() >= 2 && token.length() <= 32) {
                keywords.add(token);
            }
        }
    }

    private boolean isSupportedDocument(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        if (fileName.equals("notes.md") || fileName.startsWith(".workhub-")) { return false; }
        for (Path segment : path) {
            if (segment.toString().equals("历史版本") || segment.toString().equals(".obsidian") || segment.toString().startsWith(".workhub-")) { return false; }
        }
        return fileName.endsWith(".md") || fileName.endsWith(".txt");
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = trimToNull(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private record KnowledgeQuery(List<String> keywords,
                                  List<String> businessLineScopeKeywords,
                                  List<String> pathScopeKeywords) {
    }

    private record KnowledgeHit(Path path, int score, int businessLineMatchCount, int scopeMatchCount, String snippet) {
    }

    public record RequirementKnowledgeNote(String rawRelativePath,
                                           String wikiRelativePath,
                                           String wikiObsidianUrl,
                                           String markdown,
                                           boolean written) {
        public String asPromptContext() {
            return rawRelativePath == null ? markdown : "知识库原始材料版本：" + rawRelativePath + "\n\n" + markdown;
        }
    }

    public record RequirementKnowledgeLocation(Path vaultRoot,
                                               Path rawPath,
                                               Path wikiPath,
                                               String rawRelativePath,
                                               String wikiRelativePath,
                                               String wikiObsidianUrl) {
        public Path wikiFolderPath() {
            return wikiPath.getParent();
        }
    }
}
