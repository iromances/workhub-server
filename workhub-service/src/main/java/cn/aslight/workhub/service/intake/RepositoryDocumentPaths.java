package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.service.system.SysConfigService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** WorkHub 研发产出的仓库目录；知识库地址仅用于读取知识和规范。 */
public final class RepositoryDocumentPaths {
    public static final String CONFIG_KEY_REPOSITORY_PATH = "repositoryPath";
    public static final String CONFIG_KEY_PLAYBOOK_PATH = "playbookPath";
    public static final String SOURCES = "requirement-sources";
    public static final String REQUIREMENTS = "requirements";
    public static final String BUSINESS_LINES = "业务线";
    public static final String PLAYBOOK = "agents-playbook";
    /** WorkHub 平台自身的业务线编码，归档保留在 docs/业务线；其余业务线归档到 agents-playbook 仓库。 */
    public static final String WORKHUB_BUSINESS_LINE_CODE = "BL000012";

    private RepositoryDocumentPaths() { }

    /** 业务线 wiki 归档根：WorkHub 平台走 docs/业务线，其余业务线走 agents-playbook 仓库的 docs/业务线。 */
    public static Path wikiArchiveRoot(SysConfigService config, Path documentsRoot, String businessLineCode) {
        String code = businessLineCode == null ? null : businessLineCode.trim();
        if (WORKHUB_BUSINESS_LINE_CODE.equals(code)) {
            return documentsRoot.resolve(BUSINESS_LINES);
        }
        return playbookArchiveRoot(config, documentsRoot);
    }

    /** agents-playbook 仓库的业务线归档根（其 docs/业务线）；未配置 playbookPath 时默认取与本仓库同级的 agents-playbook 仓库。 */
    public static Path playbookArchiveRoot(SysConfigService config, Path documentsRoot) {
        String configured = config.findPlainValue(ProjectKnowledgeBaseService.CONFIG_GROUP, CONFIG_KEY_PLAYBOOK_PATH);
        Path repository = configured != null && !configured.isBlank()
                ? Path.of(configured.trim()).toAbsolutePath().normalize()
                : documentsRoot.getParent().resolve(PLAYBOOK);
        Path root = repository.resolve("docs").resolve(BUSINESS_LINES);
        Path documents = documentsRoot.toAbsolutePath().normalize();
        if (root.startsWith(documents) || documents.startsWith(root)) {
            throw new IllegalStateException("agents-playbook 归档根不能位于 WorkHub 仓库 docs 内");
        }
        return root;
    }

    public static Path resolve(SysConfigService config) {
        String configured = config.findPlainValue(ProjectKnowledgeBaseService.CONFIG_GROUP, CONFIG_KEY_REPOSITORY_PATH);
        Path repository;
        if (configured != null && !configured.isBlank()) {
            repository = Path.of(configured.trim()).toAbsolutePath().normalize();
            if (!isRepository(repository)) {
                throw new IllegalStateException("研发归档 repositoryPath 必须指向 WorkHub 源码仓库");
            }
        } else {
            repository = findRepository(Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize());
        }
        try {
            Path documents = KnowledgeArchiveFiles.checked(repository, repository.resolve("docs"));
            String vault = config.findPlainValue(ProjectKnowledgeBaseService.CONFIG_GROUP,
                    ProjectKnowledgeBaseService.CONFIG_KEY_VAULT_PATH);
            if (vault != null && !vault.isBlank()) {
                Path knowledge = Path.of(vault.trim()).toAbsolutePath().normalize();
                Path realKnowledge = canonicalPath(knowledge);
                Path realDocuments = repository.toRealPath().resolve("docs");
                if (realDocuments.startsWith(realKnowledge) || realKnowledge.startsWith(realDocuments)) {
                    throw new IllegalStateException("研发产出不能归档到知识库内，请检查 repositoryPath 与 vaultPath");
                }
            }
            return documents;
        } catch (IOException ex) {
            throw new IllegalStateException("研发归档目录不可用", ex);
        }
    }

    static Path findRepository(Path start) {
        for (Path current = start; current != null; current = current.getParent()) {
            if (isRepository(current)) { return current; }
        }
        throw new IllegalStateException("未找到 WorkHub 仓库，请配置 knowledge.project/repositoryPath");
    }

    private static boolean isRepository(Path path) {
        return Files.isRegularFile(path.resolve("pom.xml"))
                && Files.isDirectory(path.resolve("workhub-service"))
                && Files.isDirectory(path.resolve("workhub-bootstrap"));
    }

    private static Path canonicalPath(Path path) throws IOException {
        Path existing = path;
        while (!Files.exists(existing)) { existing = existing.getParent(); }
        return existing.toRealPath().resolve(existing.relativize(path));
    }

    /** 将已迁移的历史来源和正式评估前缀映射到仓库 docs，不改写历史正文。 */
    public static String migrateRelativePath(String path) {
        if (path == null) { return null; }
        String normalized = path.replace('\\', '/');
        for (String prefix : new String[]{"raw/公司项目/requirements/", "raw/requirements/"}) {
            if (normalized.startsWith(prefix)) { return SOURCES + "/" + normalized.substring(prefix.length()); }
        }
        String oldRequirements = "wiki/公司项目/研发需求/";
        return normalized.startsWith(oldRequirements)
                ? BUSINESS_LINES + "/" + normalized.substring(oldRequirements.length()) : normalized;
    }
}
