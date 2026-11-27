package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.model.intake.IntakeStructuredData;
import cn.aslight.workhub.model.intake.IntakeRecordEntity;
import cn.aslight.workhub.model.intake.IntakeSddDocuments;
import cn.aslight.workhub.model.intake.DevelopmentAnalysisDraft;
import cn.aslight.workhub.model.project.ProjectDetailResponse;
import cn.aslight.workhub.service.attachment.AttachmentService;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 项目知识库检索服务。
 *
 * <p>该服务只做本地确定性文本检索：根据需求和项目关键词从 Obsidian
 * Vault 和项目 docs 中提取少量相关 Markdown 摘要。研发产出仅写入项目
 * docs；知识库只读，写入保留历史版本。</p>
 */
@Service
public class ProjectKnowledgeBaseService {

    public static final String CONFIG_GROUP = "knowledge.project";
    public static final String CONFIG_KEY_VAULT_PATH = "vaultPath";
    public static final String CONFIG_KEY_SDD_PROCESS_PATH = "sddProcessPath";
    public static final String DEFAULT_SDD_PROCESS_PATH = "schema/研发/SDD/流程.md";
    /** 仓库 docs 内的 SDD 流程规范路径，优先于知识库配置读取。 */
    public static final String REPOSITORY_SDD_PROCESS_PATH = "研发规范/SDD/流程.md";

    private static final Logger log = LoggerFactory.getLogger(ProjectKnowledgeBaseService.class);
    private static final int MAX_SCAN_FILES = 800;
    private static final int MAX_SELECTED_FILES = 5;
    private static final int MAX_READ_CHARS = 120_000;
    private static final int MAX_SNIPPET_CHARS = 1_200;
    private static final int MAX_CONTEXT_CHARS = 6_000;
    /** 需求目录下输入版本的来源路径；版本 ID 之前的相对前缀不作解析依据。 */
    private static final Pattern VERSIONED_SOURCE = Pattern.compile("(?:.*/)?原始材料/历史版本/([A-Za-z0-9_-]+)/source\\.md");

    private final SysConfigService sysConfigService;
    private final AttachmentService attachmentService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate archiveTransaction;

    public ProjectKnowledgeBaseService(SysConfigService sysConfigService,
                                       AttachmentService attachmentService,
                                       ObjectMapper objectMapper,
                                       PlatformTransactionManager transactionManager) {
        this.sysConfigService = sysConfigService;
        this.attachmentService = attachmentService;
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
        Set<Path> roots = new LinkedHashSet<>();
        try {
            Path documents = RepositoryDocumentPaths.resolve(sysConfigService);
            if (Files.isDirectory(documents)) { roots.add(documents); }
        } catch (IllegalStateException ex) {
            log.warn("项目文档检索目录不可用：{}", ex.getMessage());
        }
        String configuredPath = trimToNull(sysConfigService.findPlainValue(CONFIG_GROUP, CONFIG_KEY_VAULT_PATH));
        if (configuredPath != null) {
            Path knowledge = Path.of(configuredPath).toAbsolutePath().normalize();
            if (Files.isDirectory(knowledge)) { roots.add(knowledge); }
        }
        if (roots.isEmpty()) { return "- 未找到可读取的项目文档或知识库目录"; }
        List<String> contexts = new ArrayList<>();
        for (Path root : roots) {
            KnowledgeQuery query = buildQuery(root, structuredData, project);
            if (query.keywords().isEmpty()) { continue; }
            List<KnowledgeHit> hits = search(root, query);
            if (!hits.isEmpty()) { contexts.add(formatHits(root, hits)); }
        }
        if (contexts.isEmpty()) { return "- 未在项目文档或知识库中命中相关内容"; }
        int budget = (MAX_CONTEXT_CHARS - (contexts.size() - 1) * 2) / contexts.size();
        return String.join("\n\n", contexts.stream().map(text -> text.substring(0, Math.min(text.length(), budget))).toList());
    }

    /**
     * 定向读取 SDD 流程规范全文，供 SDD 模式设计评估使用。优先读取 WorkHub
     * 仓库 {@code docs/研发规范/SDD/流程.md}；仓库目录不可用或文件不存在时，回落到
     * 知识库 {@code vaultPath + sddProcessPath} 配置（历史兼容）。
     *
     * @return 规范文本（超长按读取上限截断）
     * @throws IllegalStateException 仓库与知识库均未提供可读取的规范文件
     */
    public String readSddProcessSpec() {
        Path repositorySpec = resolveRepositorySddSpec();
        if (repositorySpec != null) {
            return readSpecFile(repositorySpec);
        }
        return readVaultSddSpec();
    }

    /** 解析仓库 docs 内的 SDD 流程规范；仓库目录不可用或文件不存在时返回 {@code null}，由调用方回落知识库配置。 */
    private Path resolveRepositorySddSpec() {
        try {
            Path documents = RepositoryDocumentPaths.resolve(sysConfigService);
            Path specPath = documents.resolve(REPOSITORY_SDD_PROCESS_PATH).normalize();
            if (specPath.startsWith(documents) && Files.isRegularFile(specPath)) {
                return specPath;
            }
        } catch (IllegalStateException ex) {
            log.warn("仓库 SDD 流程规范不可用，回落知识库配置：{}", ex.getMessage());
        }
        return null;
    }

    /** 按知识库配置解析并读取 SDD 流程规范，保留历史路径与防越界约束。 */
    private String readVaultSddSpec() {
        String configuredPath = trimToNull(sysConfigService.findPlainValue(CONFIG_GROUP, CONFIG_KEY_VAULT_PATH));
        if (configuredPath == null) {
            throw new IllegalStateException("未找到 SDD 流程规范：仓库 docs/" + REPOSITORY_SDD_PROCESS_PATH
                    + " 不存在，且未配置知识库根目录（knowledge.project.vaultPath）");
        }
        Path vaultRoot = Path.of(configuredPath).toAbsolutePath().normalize();
        String relative = trimToNull(sysConfigService.findPlainValue(CONFIG_GROUP, CONFIG_KEY_SDD_PROCESS_PATH));
        Path specPath = vaultRoot.resolve(relative == null ? DEFAULT_SDD_PROCESS_PATH : relative).normalize();
        if (!specPath.startsWith(vaultRoot) || !Files.isRegularFile(specPath)) {
            throw new IllegalStateException("知识库中未找到 SDD 流程规范文件：" + specPath);
        }
        return readSpecFile(specPath);
    }

    private String readSpecFile(Path specPath) {
        try {
            String content = Files.readString(specPath);
            return content.length() <= MAX_READ_CHARS ? content : content.substring(0, MAX_READ_CHARS);
        } catch (Exception ex) {
            throw new IllegalStateException("读取 SDD 流程规范失败：" + ex.getMessage(), ex);
        }
    }

    /**
     * 评估前归档本轮输入版本：需求原文与附件清单写入需求目录 原始材料/，附件内容只保留在数据库。
     * 返回本轮不可变来源快照，供提示词与后续分析追溯；输入与最近版本一致时复用该版本。
     * 归档失败不阻断 AI 分析：来源路径留空并携带失败原因，由调用方记入需求历史。
     */
    public RequirementKnowledgeNote upsertRequirementIterationNote(IntakeRecordEntity intake,
                                                                   IntakeStructuredData structuredData,
                                                                   ProjectDetailResponse project) {
        try {
            RequirementKnowledgeLocation location = resolveRequirementIterationLocation(structuredData, project, intake.getId());
            String markdown = buildRawRequirementMarkdown(intake, structuredData, project, location.rawRelativePath());
            return KnowledgeArchiveFiles.locked(location.wikiRoot(), () -> archiveInputVersion(location, intake.getId(), markdown));
        } catch (IOException | RuntimeException ex) {
            log.warn("输入版本归档失败，评估继续。intakeId={}", intake.getId(), ex);
            return new RequirementKnowledgeNote(null, null, null,
                    buildRawRequirementMarkdown(intake, structuredData, project, null), false,
                    firstNonBlank(ex.getMessage(), ex.getClass().getSimpleName()));
        }
    }

    private RequirementKnowledgeNote archiveInputVersion(RequirementKnowledgeLocation location, Long intakeId,
                                                         String markdown) throws IOException {
        Path history = location.rawFolder().resolve("历史版本");
        String sourceSha256 = KnowledgeArchiveFiles.digest(markdown.getBytes(StandardCharsets.UTF_8));
        List<AttachmentService.AttachmentDigest> attachments = attachmentService.listIntakeMaterialDigests(intakeId);
        Path version = reusableVersion(location.wikiRoot(), history, sourceSha256, attachments);
        if (version == null) {
            version = writeInputVersion(location.wikiRoot(), history, intakeId, markdown, sourceSha256, attachments);
        }
        // 历次输入由不可变版本目录保留，最近一轮输入直接覆盖，不再逐次留存旧文件。
        KnowledgeArchiveFiles.atomicWrite(location.wikiRoot(), location.rawPath(), markdown.getBytes(StandardCharsets.UTF_8));
        return new RequirementKnowledgeNote(location.repositoryRelative(version.resolve("source.md")),
                null, null, markdown, true, null);
    }

    private Path writeInputVersion(Path root, Path history, Long intakeId, String markdown, String sourceSha256,
                                   List<AttachmentService.AttachmentDigest> attachments) throws IOException {
        Path version = history.resolve(UUID.randomUUID().toString());
        KnowledgeArchiveFiles.immutableWrite(root, version.resolve("source.md"), markdown.getBytes(StandardCharsets.UTF_8));
        String manifest = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(new InputVersionManifest(
                intakeId, version.getFileName().toString(), LocalDateTime.now().toString(), sourceSha256, attachments));
        KnowledgeArchiveFiles.immutableWrite(root, version.resolve("manifest.json"), manifest.getBytes(StandardCharsets.UTF_8));
        return version;
    }

    /** 最近一个版本的原文哈希与附件清单都相同时复用该版本；没有可读的版本清单时返回 null。 */
    private Path reusableVersion(Path root, Path history, String sourceSha256,
                                 List<AttachmentService.AttachmentDigest> attachments) throws IOException {
        if (!Files.isDirectory(history)) { return null; }
        Path latest = null;
        InputVersionManifest latestManifest = null;
        try (Stream<Path> versions = Files.list(history)) {
            for (Path version : versions.filter(Files::isDirectory).sorted().toList()) {
                InputVersionManifest manifest = readVersionManifest(root, version);
                if (manifest == null || manifest.recordedAt() == null) { continue; }
                if (latestManifest == null || manifest.recordedAt().compareTo(latestManifest.recordedAt()) > 0) {
                    latest = version;
                    latestManifest = manifest;
                }
            }
        }
        if (latestManifest == null
                || !sourceSha256.equals(latestManifest.sourceSha256())
                || !attachments.equals(latestManifest.attachments())) {
            return null;
        }
        return latest;
    }

    /** 版本清单缺失或不可解析时返回 null，按不可复用处理。 */
    private InputVersionManifest readVersionManifest(Path root, Path version) {
        try {
            Path file = KnowledgeArchiveFiles.checked(root, version.resolve("manifest.json"));
            if (!Files.isRegularFile(file)) { return null; }
            return objectMapper.readValue(file.toFile(), InputVersionManifest.class);
        } catch (IOException | RuntimeException ex) {
            log.warn("输入版本清单不可读，按不可复用处理。path={}", version, ex);
            return null;
        }
    }

    private record InputVersionManifest(Long intakeId, String versionId, String recordedAt, String sourceSha256,
                                        List<AttachmentService.AttachmentDigest> attachments) {
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
            KnowledgeArchiveFiles.locked(location.wikiRoot(), () -> {
                Path target = KnowledgeArchiveFiles.checked(location.wikiRoot(), location.wikiPath());
                // 同一确认结果重新归档保留首次记录时间，不生成伪修订。
                if (Files.exists(target) && Files.readString(target).contains(marker)) {
                    indexConfirmedReport(location);
                    return null;
                }
                String markdown = "# 研发评估：" + requirementTitleSegment(structuredData)
                        + "\n\n" + marker + "\n\n- 状态：已确认（不代表已开发或发布）\n- 需求 ID：" + intake.getId()
                        + "\n- 审批编号：" + requirementCodeSegment(structuredData, intake.getId()) + "\n- 分析 ID：" + analysisId
                        + "\n- 确认人：" + confirmedBy + "\n- 确认时间：" + confirmedAt
                        + "\n- 来源版本：" + sourceLink(location, draft.requirementRawPath())
                        + "\n\n## 评估摘要\n\n" + firstNonBlank(draft.summary(), "无")
                        + "\n\n## 经确认的评估数据\n\n" + fenced(json, "json") + "\n";
                KnowledgeArchiveFiles.versionedWrite(location.wikiRoot(), target, markdown.getBytes(StandardCharsets.UTF_8));
                indexConfirmedReport(location);
                return null;
            });
            return location;
        } catch (IOException ex) {
            throw new IllegalStateException("正式评估归档失败", ex);
        }
    }

    private void indexConfirmedReport(RequirementKnowledgeLocation location) throws IOException {
        Path index = KnowledgeArchiveFiles.checked(location.wikiRoot(), location.wikiRoot().resolve("index.md"));
        String previous = Files.exists(index) ? Files.readString(index) : "# 公司项目研发需求\n";
        String link = "[" + location.wikiRelativePath().replace('\\', '/') + "](<" + location.wikiPath() + ">)";
        if (!previous.contains(location.wikiPath().toString())) {
            String heading = "\n## 已确认自动评估\n";
            if (!previous.contains(heading)) { previous += heading; }
            int start = previous.indexOf(heading) + heading.length();
            int end = previous.indexOf("\n## ", start);
            if (end < 0) { end = previous.length(); }
            KnowledgeArchiveFiles.atomicWrite(location.wikiRoot(), index,
                    (previous.substring(0, end) + "\n- " + link + "\n" + previous.substring(end)).getBytes(StandardCharsets.UTF_8));
        }
    }

    /** 来源版本链接：新布局按需求目录定位，旧 requirement-sources、raw 路径只读解析；文件不存在时注明未留存。 */
    private String sourceLink(RequirementKnowledgeLocation location, String relative) {
        if (relative == null || relative.isBlank()) { return "未留存（历史分析未记录输入版本，不能据此补造来源）"; }
        try {
            Path target = resolveSourceVersion(location, relative);
            return target != null && Files.isRegularFile(target)
                    ? "[来源版本](<" + target + ">)"
                    : "未留存（来源版本文件不存在：" + relative + "）";
        } catch (IOException ex) {
            throw new IllegalStateException("来源版本路径不合法：" + relative, ex);
        }
    }

    private Path resolveSourceVersion(RequirementKnowledgeLocation location, String relative) throws IOException {
        String normalized = RepositoryDocumentPaths.migrateRelativePath(relative);
        Matcher versioned = VERSIONED_SOURCE.matcher(normalized);
        if (versioned.matches()) {
            return KnowledgeArchiveFiles.checked(location.wikiRoot(),
                    location.rawFolder().resolve("历史版本").resolve(versioned.group(1)).resolve("source.md"));
        }
        if (!normalized.startsWith(RepositoryDocumentPaths.SOURCES + "/")) { return null; }
        return KnowledgeArchiveFiles.checked(location.vaultRoot(), location.vaultRoot().resolve(normalized));
    }

    private String fenced(String content, String language) {
        int length = 3;
        while (content.contains("`".repeat(length))) { length++; }
        String fence = "`".repeat(length);
        return fence + language + "\n" + content + "\n" + fence + "\n";
    }

    public RequirementKnowledgeLocation resolveRequirementIterationLocation(IntakeStructuredData structuredData,
                                                                           ProjectDetailResponse project, Long intakeId) {
        Path vaultRoot = RepositoryDocumentPaths.resolve(sysConfigService);
        Path wikiRoot = RepositoryDocumentPaths.wikiArchiveRoot(sysConfigService, vaultRoot, businessLineCode(project));
        Path wikiPath = buildWikiRequirementPath(wikiRoot, structuredData, project, intakeId);
        // 原始材料与已确认评估同在需求目录；相对路径以仓库根为基准，兼容 docs/业务线 与 agents-playbook 两个归档根
        Path rawPath = wikiPath.getParent().resolve("原始材料").resolve("source.md");
        return new RequirementKnowledgeLocation(
                vaultRoot,
                wikiRoot,
                rawPath,
                wikiPath,
                vaultRoot.getParent().relativize(rawPath).toString(),
                vaultRoot.getParent().relativize(wikiPath).toString(),
                null
        );
    }

    private Path buildWikiRequirementPath(Path wikiRoot, IntakeStructuredData structuredData, ProjectDetailResponse project, Long intakeId) {
        Path requirementDir = wikiRoot.resolve(businessLineDirSegment(project));
        if (RepositoryDocumentPaths.WORKHUB_BUSINESS_LINE_CODE.equals(businessLineCode(project))) {
            // WorkHub 平台自身保留既有结构：需求迭代/{项目名称}/{年份}/{审批编号}-{需求名称}
            requirementDir = requirementDir
                    .resolve("需求迭代")
                    .resolve(projectNameSegment(project))
                    .resolve(resolveRequirementYear(structuredData));
        } else {
            // 其余业务线归档 agents-playbook：需求/{审批编号}-{需求名称}
            requirementDir = requirementDir.resolve("需求");
        }
        return requirementDir
                .resolve(requirementCodeSegment(structuredData, intakeId) + "-" + requirementTitleSegment(structuredData))
                .resolve("研发评估.md")
                .normalize();
    }

    /**
     * SDD 模式确认后，将 AI 草稿中的三件套内容发布为需求归档目录下的 proposal/design/tasks Markdown。
     * 逐份独立写入：某一份内容为空时跳过该份，已存在同指纹文件时不重复写。
     */
    public void publishSddDocuments(IntakeRecordEntity intake,
                                    IntakeStructuredData structuredData,
                                    ProjectDetailResponse project,
                                    Long analysisId,
                                    DevelopmentAnalysisDraft draft,
                                    String confirmedBy,
                                    String confirmedAt) {
        if (draft == null || draft.sddDocuments() == null || !draft.sddDocuments().hasAny()) {
            return;
        }
        RequirementKnowledgeLocation location = resolveRequirementIterationLocation(structuredData, project, intake.getId());
        Path folder = location.wikiPath().getParent();
        IntakeSddDocuments documents = draft.sddDocuments();
        writeSddDocument(location.wikiRoot(), folder, "proposal.md", "Proposal", documents.sddProposal(),
                intake, analysisId, confirmedBy, confirmedAt);
        writeSddDocument(location.wikiRoot(), folder, "design.md", "Design", documents.sddDesign(),
                intake, analysisId, confirmedBy, confirmedAt);
        writeSddDocument(location.wikiRoot(), folder, "tasks.md", "Tasks", documents.sddTasks(),
                intake, analysisId, confirmedBy, confirmedAt);
    }

    private void writeSddDocument(Path vaultRoot, Path folder, String fileName, String label, String content,
                                  IntakeRecordEntity intake, Long analysisId, String confirmedBy, String confirmedAt) {
        if (content == null || content.isBlank()) {
            return;
        }
        String fingerprint = KnowledgeArchiveFiles.digest((intake.getId() + "|" + analysisId + "|" + label + "|" + content)
                .getBytes(StandardCharsets.UTF_8));
        String marker = "<!-- workhub-sdd:" + fingerprint + " -->";
        try {
            KnowledgeArchiveFiles.locked(vaultRoot, () -> {
                Path target = KnowledgeArchiveFiles.checked(vaultRoot, folder.resolve(fileName));
                if (Files.exists(target) && Files.readString(target).contains(marker)) {
                    return null;
                }
                String body = content.strip();
                String markdown = "# " + label + "（SDD 设计评估产出）\n\n"
                        + marker + "\n\n"
                        + "- 需求 ID：" + intake.getId() + "\n"
                        + "- 分析 ID：" + analysisId + "\n"
                        + "- 确认人：" + confirmedBy + "\n"
                        + "- 确认时间：" + confirmedAt + "\n"
                        + "- 状态：AI 草稿经人工确认发布，后续修订以人工版本为准\n\n---\n\n"
                        + body + "\n";
                KnowledgeArchiveFiles.versionedWrite(vaultRoot, target, markdown.getBytes(StandardCharsets.UTF_8));
                return null;
            });
        } catch (IOException ex) {
            throw new IllegalStateException("SDD 三件套发布失败：" + fileName, ex);
        }
    }

    private String businessLineSegment(ProjectDetailResponse project) {
        String businessLine = safePathSegment(firstNonBlank(project == null ? null : project.businessLine(), "未归类业务线"));
        return businessLine;
    }

    private String businessLineCode(ProjectDetailResponse project) {
        return project == null ? null : trimToNull(project.businessLineCode());
    }

    /** 业务线归档目录统一为 {业务线编码}-{业务线名称}；无编码或编码与名称相同时退回名称。 */
    private String businessLineDirSegment(ProjectDetailResponse project) {
        String code = trimToNull(project == null ? null : project.businessLineCode());
        String name = businessLineSegment(project);
        return code == null || code.equals(name) ? name : safePathSegment(code + "-" + name);
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
                                               String rawRelativePath) {
        String title = firstNonBlank(
                structuredData == null ? null : structuredData.requirementNameOrTitle(),
                structuredData == null ? null : structuredData.requirementDigest(),
                "未命名需求"
        );
        StringBuilder builder = new StringBuilder();
        builder.append("# ").append(title).append("\n\n");
        builder.append("> 本文件由系统在 AI 任务评估前自动归档，用于保存散落的原始需求材料；本文件不总结需求点，不拆任务。\n\n");
        builder.append("## 元信息\n\n");
        appendMetadata(builder, "项目文档路径", rawRelativePath);
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
        builder.append("\n原始截图与附件保存在数据库，清单见 `manifest.json`；来源解析与 AI 判断不代替原始文件。\n");
        return builder.toString();
    }

    private void appendMetadata(StringBuilder builder, String label, String value) {
        builder.append("- **")
                .append(label)
                .append("**：")
                .append(firstNonBlank(value, "-"))
                .append('\n');
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
        builder.append("以下内容来自 ").append(vaultRoot)
                .append("，仅用于辅助判断不确定事项；如与需求或代码冲突，以需求和代码为准。\n");
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
                                           boolean written,
                                           String failureReason) {
        public RequirementKnowledgeNote(String rawRelativePath, String wikiRelativePath, String wikiObsidianUrl,
                                        String markdown, boolean written) {
            this(rawRelativePath, wikiRelativePath, wikiObsidianUrl, markdown, written, null);
        }

        public String asPromptContext() {
            return rawRelativePath == null ? markdown : "项目文档原始材料版本：" + rawRelativePath + "\n\n" + markdown;
        }
    }

    public record RequirementKnowledgeLocation(Path vaultRoot,
                                               Path wikiRoot,
                                               Path rawPath,
                                               Path wikiPath,
                                               String rawRelativePath,
                                               String wikiRelativePath,
                                               String wikiObsidianUrl) {
        public Path wikiFolderPath() {
            return wikiPath.getParent();
        }

        /** 需求目录下保存输入版本的 原始材料/ 目录。 */
        public Path rawFolder() {
            return rawPath.getParent();
        }

        /** 以 WorkHub 仓库根为基准的相对路径，与 wikiRelativePath、rawRelativePath 同一基准。 */
        public String repositoryRelative(Path target) {
            return vaultRoot.getParent().relativize(target).toString();
        }
    }
}
