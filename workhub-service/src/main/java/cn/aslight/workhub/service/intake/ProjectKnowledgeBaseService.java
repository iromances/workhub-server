package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.model.intake.IntakeStructuredData;
import cn.aslight.workhub.model.intake.IntakeRecordEntity;
import cn.aslight.workhub.model.project.ProjectDetailResponse;
import cn.aslight.workhub.service.system.SysConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URLEncoder;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 项目知识库检索服务。
 *
 * <p>该服务只做本地确定性文本检索：根据需求和项目关键词从 Obsidian
 * Vault 中提取少量相关 Markdown 摘要，并把单个需求的散落材料归档为项目需求迭代 Markdown，
 * 再交给 AI 任务评估使用。</p>
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

    public ProjectKnowledgeBaseService(SysConfigService sysConfigService) {
        this.sysConfigService = sysConfigService;
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

    /**
     * 将单个需求的结构化字段、原始内容和附件正文摘要分别归档到 raw 与 wiki。
     *
     * <p>raw 保存散落原始材料，wiki 保存面向项目需求迭代的整理版需求文件。
     * 两处文件每次任务评估前覆盖更新，不在此步骤总结需求点；
     * AI 后续再基于最新需求材料做需求点梳理。</p>
     */
    public RequirementKnowledgeNote upsertRequirementIterationNote(IntakeRecordEntity intake,
                                                                    IntakeStructuredData structuredData,
                                                                    ProjectDetailResponse project) {
        try {
            RequirementKnowledgeLocation location = resolveRequirementIterationLocation(structuredData, project);
            Files.createDirectories(location.vaultRoot());
            Path rawPath = location.rawPath();
            Path wikiPath = location.wikiPath();
            Path vaultRoot = location.vaultRoot();
            String rawMarkdown = buildRawRequirementMarkdown(intake, structuredData, project, rawPath, vaultRoot);
            Files.createDirectories(rawPath.getParent());
            Files.writeString(rawPath, rawMarkdown, StandardCharsets.UTF_8);

            String wikiMarkdown = buildWikiRequirementMarkdown(intake, structuredData, project, rawPath, wikiPath, vaultRoot);
            Files.createDirectories(wikiPath.getParent());
            Files.writeString(wikiPath, wikiMarkdown, StandardCharsets.UTF_8);
            return new RequirementKnowledgeNote(
                    location.rawRelativePath(),
                    location.wikiRelativePath(),
                    location.wikiObsidianUrl(),
                    wikiMarkdown,
                    true
            );
        } catch (IllegalStateException ex) {
            return new RequirementKnowledgeNote(null, null, null, "- " + ex.getMessage(), false);
        } catch (IOException ex) {
            log.warn("Project requirement note write failed.", ex);
            return new RequirementKnowledgeNote(null, null, null, "- 写入项目需求迭代 Markdown 失败：" + ex.getMessage(), false);
        }
    }

    public RequirementKnowledgeLocation resolveRequirementIterationLocation(IntakeStructuredData structuredData,
                                                                           ProjectDetailResponse project) {
        String configuredPath = trimToNull(sysConfigService.findPlainValue(CONFIG_GROUP, CONFIG_KEY_VAULT_PATH));
        if (configuredPath == null) {
            throw new IllegalStateException("未配置项目知识库地址");
        }
        Path vaultRoot = Path.of(configuredPath).toAbsolutePath().normalize();
        Path rawPath = buildRawRequirementPath(vaultRoot, structuredData, project);
        Path wikiPath = buildWikiRequirementPath(vaultRoot, structuredData, project);
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

    private Path buildRawRequirementPath(Path vaultRoot, IntakeStructuredData structuredData, ProjectDetailResponse project) {
        return vaultRoot
                .resolve("raw")
                .resolve("requirements")
                .resolve(businessLineSegment(project))
                .resolve(projectNameSegment(project))
                .resolve(resolveRequirementYear(structuredData))
                .resolve(requirementCodeSegment(structuredData))
                .resolve("source.md")
                .normalize();
    }

    private Path buildWikiRequirementPath(Path vaultRoot, IntakeStructuredData structuredData, ProjectDetailResponse project) {
        return vaultRoot
                .resolve("wiki")
                .resolve("projects")
                .resolve(businessLineSegment(project))
                .resolve("需求迭代")
                .resolve(projectNameSegment(project))
                .resolve(resolveRequirementYear(structuredData))
                .resolve(requirementCodeSegment(structuredData) + "-" + requirementTitleSegment(structuredData) + ".md")
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

    private String requirementCodeSegment(IntakeStructuredData structuredData) {
        return safePathSegment(firstNonBlank(structuredData == null ? null : structuredData.approvalCode(), "REQ"));
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

        builder.append("\n## 需求原始摘要与说明\n\n");
        appendParagraph(builder, structuredData == null ? null : structuredData.requirementDigest());
        appendParagraph(builder, structuredData == null ? null : structuredData.requirementSummary());

        builder.append("\n## 备注\n\n");
        appendParagraph(builder, structuredData == null ? null : structuredData.remark());

        builder.append("\n## 结构化字段\n\n");
        if (structuredData == null || structuredData.fields() == null || structuredData.fields().isEmpty()) {
            builder.append("- 无\n");
        } else {
            structuredData.fields().stream()
                    .filter(field -> field != null && trimToNull(field.label()) != null)
                    .forEach(field -> builder.append("- **")
                            .append(field.label())
                            .append("**：")
                            .append(firstNonBlank(field.value(), "-"))
                            .append('\n'));
        }

        builder.append("\n## 附件与截图内容\n\n");
        if (structuredData == null || structuredData.attachmentSummaries() == null || structuredData.attachmentSummaries().isEmpty()) {
            builder.append("- 无\n");
        } else {
            structuredData.attachmentSummaries().stream()
                    .filter(summary -> summary != null)
                    .forEach(summary -> builder.append("### ")
                            .append(firstNonBlank(summary.fileName(), "未命名附件"))
                            .append("\n\n")
                            .append("- 类型：")
                            .append(firstNonBlank(summary.fileType(), "-"))
                            .append("\n\n")
                            .append(firstNonBlank(summary.summaryText(), "-"))
                            .append("\n\n"));
        }

        builder.append("\n## 原始内容\n\n");
        appendCodeBlock(builder, intake == null ? null : intake.getRawContent());
        return builder.toString();
    }

    private String buildWikiRequirementMarkdown(IntakeRecordEntity intake,
                                                IntakeStructuredData structuredData,
                                                ProjectDetailResponse project,
                                                Path rawPath,
                                                Path wikiPath,
                                                Path vaultRoot) {
        String title = firstNonBlank(
                structuredData == null ? null : structuredData.requirementNameOrTitle(),
                structuredData == null ? null : structuredData.requirementDigest(),
                "未命名需求"
        );
        StringBuilder builder = new StringBuilder();
        builder.append("# ").append(title).append("\n\n");
        builder.append("> 本文件由 WorkHub 在 AI 任务评估前根据 raw 原始材料整理，用于归档项目需求迭代材料；本文件不总结需求点，需求点由后续 AI 评估步骤提炼。\n\n");
        builder.append("## 材料来源\n\n");
        appendMetadata(builder, "raw 原始材料", vaultRoot.relativize(rawPath).toString());
        appendMetadata(builder, "wiki 路径", vaultRoot.relativize(wikiPath).toString());
        builder.append("\n");
        builder.append(buildRawRequirementMarkdown(intake, structuredData, project, rawPath, vaultRoot)
                .replaceFirst("^# .+\\n\\n", ""));
        return builder.toString();
    }

    private void appendMetadata(StringBuilder builder, String label, String value) {
        builder.append("- **")
                .append(label)
                .append("**：")
                .append(firstNonBlank(value, "-"))
                .append('\n');
    }

    private void appendParagraph(StringBuilder builder, String value) {
        builder.append(firstNonBlank(value, "-")).append("\n\n");
    }

    private void appendCodeBlock(StringBuilder builder, String value) {
        builder.append("```text\n")
                .append(firstNonBlank(value, ""))
                .append("\n```\n");
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
            return normalized.substring(0, 80).trim();
        }
        return normalized.isBlank() ? "未命名" : normalized;
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
            if (wikiRelativePath == null) {
                return markdown;
            }
            return "知识库需求迭代文件：" + wikiRelativePath + "\n\n" + markdown;
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
