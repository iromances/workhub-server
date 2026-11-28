package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.service.runtime.HostTools;
import cn.aslight.workhub.mcp.runtime.WorkhubPaths;

import cn.aslight.workhub.dao.project.BusinessLineMapper;
import cn.aslight.workhub.model.project.BusinessLineEntity;
import cn.aslight.workhub.service.system.SysConfigService;
import cn.aslight.workhub.model.project.ProjectDetailResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * GitLab 仓库只读拉取服务。
 */
@Service
public class GitlabRepositoryService {

    private static final Logger log = LoggerFactory.getLogger(GitlabRepositoryService.class);
    private static final Duration GIT_TIMEOUT = Duration.ofMinutes(3);
    private static final Duration GITLAB_API_TIMEOUT = Duration.ofSeconds(20);
    private static final String GLOBAL_CONFIG_GROUP = "gitlab.global";

    private final SysConfigService sysConfigService;
    private final BusinessLineMapper businessLineMapper;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Path codeWorkspaceRoot;

    @Autowired
    public GitlabRepositoryService(SysConfigService sysConfigService,
                                   BusinessLineMapper businessLineMapper,
                                   ObjectMapper objectMapper) {
        this(sysConfigService, businessLineMapper, objectMapper, WorkhubPaths.repositories());
    }

    GitlabRepositoryService(SysConfigService sysConfigService,
                            BusinessLineMapper businessLineMapper,
                            ObjectMapper objectMapper,
                            Path codeWorkspaceRoot) {
        this.sysConfigService = sysConfigService;
        this.businessLineMapper = businessLineMapper;
        this.objectMapper = objectMapper;
        this.codeWorkspaceRoot = Objects.requireNonNull(codeWorkspaceRoot, "代码工作区根目录不能为空")
                .toAbsolutePath()
                .normalize();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(GITLAB_API_TIMEOUT)
                .build();
    }

    private Path configuredCheckoutPath(String url, String group) {
        URI uri = URI.create(url);
        String path = uri.getPath();
        if (path == null) throw new IllegalArgumentException("GitLab 项目 URL 缺少路径");
        String marker = "/" + group + "/";
        int offset = path.indexOf(marker);
        if (offset < 0) throw new IllegalArgumentException("GitLab 项目 URL 不属于已配置的组：" + group);
        String namespace = path.substring(offset + 1).replaceFirst("\\.git$", "");
        return WorkhubPaths.namespace(codeWorkspaceRoot, namespace);
    }

    /** 归档仓库只在首次写入前克隆，之后复用检出并保留未提交文档。 */
    public synchronized Path ensureCheckout(String namespace) {
        Path path = WorkhubPaths.namespace(codeWorkspaceRoot, namespace);
        if (Files.exists(path.resolve(".git"), java.nio.file.LinkOption.NOFOLLOW_LINKS)) return path;
        String base = trimTrailingSlash(sysConfigService.requirePlainValue(GLOBAL_CONFIG_GROUP, "webApiUrl"));
        String token = sysConfigService.requirePlainValue(GLOBAL_CONFIG_GROUP, "accessToken");
        fetchRepository(base + "/" + namespace + ".git", token, path);
        return path;
    }

    /** 锁定远端提交，再读取同一提交中的流程与模板。每次分析持有独立的文本快照。 */
    Path syncStandards(Path standards, String project, String ref, String directory) {
        Path cache = WorkhubPaths.namespace(standards.resolve(".gitlab"), project);
        if (ref == null || ref.isBlank() || directory == null || directory.isBlank()) {
            throw new IllegalArgumentException("GitLab 规范分支和目录不能为空");
        }
        Path relative = Path.of(directory);
        if (relative.isAbsolute() || directory.contains("\\") || directory.startsWith("/")
                || !relative.normalize().equals(relative) || directory.startsWith("..")) {
            throw new IllegalArgumentException("GitLab 规范目录必须是仓库内的相对目录");
        }
        String token = sysConfigService.requirePlainValue(GLOBAL_CONFIG_GROUP, "accessToken");
        String base = trimTrailingSlash(sysConfigService.requirePlainValue(GLOBAL_CONFIG_GROUP, "webApiUrl"))
                + "/api/v4/projects/" + encode(project) + "/repository/";
        try {
            var commit = standardsResponse(base + "commits/" + encode(ref), token);
            if (commit.statusCode() != 200) throw new java.io.IOException("查询规范版本失败 HTTP " + commit.statusCode());
            String sha = objectMapper.readTree(commit.body()).path("id").asText();
            if (!sha.matches("[0-9a-f]{40}|[0-9a-f]{64}")) throw new java.io.IOException("GitLab 规范版本无效");
            Path snapshot = cache.resolve(sha).resolve(relative).normalize();
            Path ready = KnowledgeArchiveFiles.checked(standards, snapshot.resolve(".ready"));
            if (Files.isRegularFile(ready)) return snapshot;
            for (String name : ProjectKnowledgeBaseService.SDD_FILES) {
                var response = standardsResponse(base + "files/" + encode(directory + "/" + name) + "/raw?ref=" + sha, token);
                if (response.statusCode() == 404 && !name.equals("流程.md")) continue;
                if (response.statusCode() != 200) throw new java.io.IOException("读取规范 " + name + " 失败 HTTP " + response.statusCode());
                KnowledgeArchiveFiles.atomicWrite(standards, snapshot.resolve(name), response.body());
            }
            KnowledgeArchiveFiles.atomicWrite(standards, ready, sha.getBytes(StandardCharsets.US_ASCII));
            return snapshot;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("规范同步已中断", ex);
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("无法同步 GitLab 规范：" + ex.getMessage(), ex);
        }
    }

    private static String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private HttpResponse<byte[]> standardsResponse(String url, String token) throws java.io.IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(GITLAB_API_TIMEOUT)
                .header("PRIVATE-TOKEN", token).GET().build();
        return httpClient.send(request, HttpResponse.BodyHandlers.limiting(HttpResponse.BodyHandlers.ofByteArray(), 512 * 1024));
    }

    public GitlabRepository resolveAndFetch(String businessLine) {
        String group = requireText(businessLine, "业务线不能为空");
        ConfiguredRepository repository = resolveConfiguredRepository(group);
        if (repository == null) {
            throw new IllegalArgumentException("系统配置不存在或未启用：gitlab." + group + ".repoUrl");
        }
        String gitlabGroupName = findGitlabGroupName(group);
        if (gitlabGroupName == null) {
            throw new IllegalArgumentException("业务线未维护 GitLab 组名，请在项目管理中维护业务线对应的 gitlabGroupName：" + group);
        }
        Path repoPath = configuredCheckoutPath(repository.repositoryUrl(), gitlabGroupName);
        fetchRepository(repository.repositoryUrl(), repository.accessToken(), repoPath);
        return new GitlabRepository(repository.repositoryUrl(), repoPath);
    }

    public GitlabRepository resolveAndFetch(ProjectDetailResponse project) {
        if (project == null) {
            throw new IllegalArgumentException("项目不能为空");
        }
        String group = requireText(project.businessLine(), "业务线不能为空");
        ConfiguredRepository repository = resolveConfiguredRepository(group);
        if (repository == null) {
            repository = discoverRepository(project);
        }
        String gitlabGroupName = findGitlabGroupName(group);
        if (gitlabGroupName == null) {
            throw new IllegalArgumentException("业务线未维护 GitLab 组名，请在项目管理中维护业务线对应的 gitlabGroupName：" + group);
        }
        Path repoPath = configuredCheckoutPath(repository.repositoryUrl(), gitlabGroupName);
        fetchRepository(repository.repositoryUrl(), repository.accessToken(), repoPath);
        return new GitlabRepository(repository.repositoryUrl(), repoPath);
    }

    /**
     * 按业务线拉取 GitLab 命名空间下全部可访问仓库，用于跨微服务任务评估。
     *
     * @param project 需求归属项目
     * @return 业务线仓库集合
     */
    public GitlabRepositoryBundle analysisSnapshot(ProjectDetailResponse project, String branch) {
        if (project == null) throw new IllegalArgumentException("项目不能为空");
        return snapshot(syncGroup(project.businessLine(), false), trimToNull(branch));
    }

    /** 按所有仓库提交号标识只读分析目录，同时进行的不同分支分析各自读取固定版本。 */
    synchronized GitlabRepositoryBundle snapshot(GitlabRepositoryBundle group, String branch) {
        try {
            if (branch != null) runGit(List.of("git", "check-ref-format", "refs/heads/" + branch), group.localRoot());
            var revisions = new java.util.TreeMap<String, String>();
            for (GitlabRepository repository : group.repositories()) {
                String ref = branch == null ? "refs/remotes/origin/HEAD" : "refs/remotes/origin/" + branch;
                String sha = runGit(List.of("git", "rev-parse", "--verify", "--end-of-options", ref + "^{commit}"),
                        repository.localPath()).strip();
                if (!sha.matches("[0-9a-f]{40}|[0-9a-f]{64}")) throw new IllegalStateException("Git 提交号无效");
                revisions.put(codeWorkspaceRoot.relativize(repository.localPath()).toString().replace('\\', '/'), sha);
            }
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(objectMapper.writeValueAsBytes(revisions));
            Path root = codeWorkspaceRoot.resolve(".workhub-analysis");
            KnowledgeArchiveFiles.directory(codeWorkspaceRoot, root);
            Path version = KnowledgeArchiveFiles.checked(codeWorkspaceRoot, root.resolve(java.util.HexFormat.of().formatHex(digest)));
            if (!Files.isDirectory(version)) {
                Path staging = Files.createTempDirectory(root, ".snapshot-");
                try {
                    for (var entry : revisions.entrySet()) {
                        Path source = WorkhubPaths.namespace(codeWorkspaceRoot, entry.getKey());
                        Path dest = WorkhubPaths.namespace(staging, entry.getKey());
                        Files.createDirectories(dest);
                        runGit(List.of("git", "init", "--quiet"), dest);
                        runGit(List.of("git", "fetch", "--no-tags", "--depth=1", source.toString(), entry.getValue()), dest);
                        runGit(List.of("git", "checkout", "--detach", entry.getValue()), dest);
                    }
                    Files.write(staging.resolve("revisions.json"), objectMapper.writeValueAsBytes(revisions));
                    try {
                        Files.move(staging, version);
                    } catch (java.nio.file.FileSystemException ex) {
                        if (!Files.isDirectory(version)) throw ex;
                    }
                } finally {
                    removeStaging(staging);
                }
            }
            var repositories = group.repositories().stream().map(repo -> {
                String project = codeWorkspaceRoot.relativize(repo.localPath()).toString().replace('\\', '/');
                return new GitlabRepository(repo.repositoryUrl(), WorkhubPaths.namespace(version, project), revisions.get(project));
            }).toList();
            return new GitlabRepositoryBundle(group.gitlabGroupName(), WorkhubPaths.namespace(version, group.gitlabGroupName()), repositories);
        } catch (Exception ex) {
            throw new IllegalStateException("无法创建只读分析快照，分支：" + (branch == null ? "远端默认分支" : branch)
                    + "，" + summarizeException(ex), ex);
        }
    }

    private void removeStaging(Path staging) throws java.io.IOException {
        if (!Files.exists(staging)) return;
        try (var paths = Files.walk(staging)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    /**
     * 按业务线拉取 GitLab 命名空间下全部可访问仓库。
     *
     * @param businessLine 业务线名称
     * @return 业务线仓库集合
     */
    public GitlabRepositoryBundle resolveAndFetchGroup(String businessLine) {
        return syncGroup(businessLine, true);
    }

    private GitlabRepositoryBundle syncGroup(String businessLine, boolean updateWorkingTree) {
        String normalizedBusinessLine = requireText(businessLine, "业务线不能为空");
        String gitlabGroupName = findGitlabGroupName(normalizedBusinessLine);
        if (gitlabGroupName == null) {
            throw new IllegalArgumentException("业务线未维护 GitLab 组名，请在项目管理中维护业务线对应的 gitlabGroupName：" + normalizedBusinessLine);
        }
        String webApiUrl = sysConfigService.requirePlainValue(GLOBAL_CONFIG_GROUP, "webApiUrl");
        String accessToken = sysConfigService.requirePlainValue(GLOBAL_CONFIG_GROUP, "accessToken");
        List<GitlabProject> projects = listGroupProjects(webApiUrl, accessToken, gitlabGroupName).stream()
                .filter(item -> item != null && firstNonBlank(item.httpUrlToRepo(), item.webUrl()) != null)
                .toList();
        if (projects.isEmpty()) {
            throw new IllegalArgumentException("GitLab 业务线下未找到可访问仓库：" + gitlabGroupName);
        }

        Path groupRoot = groupWorkspaceRoot(gitlabGroupName);
        List<GitlabRepository> repositories = new ArrayList<>();
        for (GitlabProject gitlabProject : projects) {
            String repositoryUrl = firstNonBlank(
                    gitlabProject.httpUrlToRepo(),
                    gitlabProject.webUrl() == null ? null : gitlabProject.webUrl() + ".git"
            );
            Path repoPath = WorkhubPaths.namespace(codeWorkspaceRoot, gitlabProject.pathWithNamespace());
            if (!gitlabProject.pathWithNamespace().startsWith(gitlabGroupName + "/") || !repoPath.startsWith(groupRoot) || repoPath.equals(groupRoot)) {
                throw new IllegalArgumentException("GitLab 项目不属于所选组");
            }
            fetchRepository(repositoryUrl, accessToken, repoPath, updateWorkingTree);
            repositories.add(new GitlabRepository(repositoryUrl, repoPath));
        }
        log.info("GitLab group repositories synced. businessLine={}, gitlabGroupName={}, repositoryCount={}",
                normalizedBusinessLine,
                gitlabGroupName,
                repositories.size());
        return new GitlabRepositoryBundle(gitlabGroupName, groupRoot, repositories);
    }

    /**
     * 查询业务线 GitLab 命名空间下的仓库，用作业务线涉及系统清单来源。
     *
     * @param businessLine 业务线名称
     * @return GitLab 仓库系统候选项
     */
    public List<GitlabProjectSystem> listGroupProjectSystems(String businessLine) {
        String group = requireText(businessLine, "业务线不能为空");
        String gitlabGroupName = findGitlabGroupName(group);
        if (gitlabGroupName == null) {
            throw new IllegalArgumentException("业务线未维护 GitLab 组名，请在项目管理中维护业务线对应的 gitlabGroupName：" + group);
        }
        String webApiUrl = sysConfigService.requirePlainValue(GLOBAL_CONFIG_GROUP, "webApiUrl");
        String accessToken = sysConfigService.requirePlainValue(GLOBAL_CONFIG_GROUP, "accessToken");
        List<GitlabProjectSystem> systems = listGroupProjects(webApiUrl, accessToken, gitlabGroupName).stream()
                .filter(Objects::nonNull)
                .map(item -> new GitlabProjectSystem(
                        firstNonBlank(item.name(), item.path()),
                        item.path(),
                        item.pathWithNamespace(),
                        item.webUrl()
                ))
                .filter(item -> trimToNull(item.systemName()) != null)
                .toList();
        if (systems.isEmpty()) {
            throw new IllegalArgumentException("GitLab 业务线下未找到可访问仓库：" + gitlabGroupName);
        }
        return systems;
    }

    private ConfiguredRepository resolveConfiguredRepository(String businessLine) {
        String configGroup = "gitlab." + requireText(businessLine, "业务线不能为空");
        String repoUrl = sysConfigService.findPlainValue(configGroup, "repoUrl");
        if (repoUrl == null) {
            return null;
        }
        String accessToken = firstNonBlank(
                sysConfigService.findPlainValue(configGroup, "accessToken"),
                sysConfigService.findPlainValue(GLOBAL_CONFIG_GROUP, "accessToken")
        );
        if (accessToken == null) {
            throw new IllegalArgumentException("系统配置不存在或未启用：" + configGroup + ".accessToken 或 " + GLOBAL_CONFIG_GROUP + ".accessToken");
        }
        return new ConfiguredRepository(repoUrl, accessToken);
    }

    private ConfiguredRepository discoverRepository(ProjectDetailResponse project) {
        String webApiUrl = sysConfigService.requirePlainValue(GLOBAL_CONFIG_GROUP, "webApiUrl");
        String accessToken = sysConfigService.requirePlainValue(GLOBAL_CONFIG_GROUP, "accessToken");
        List<GitlabProject> candidates = new ArrayList<>();
        for (String keyword : searchKeywords(project)) {
            candidates.addAll(searchGitlabProjects(webApiUrl, accessToken, keyword));
        }
        GitlabProject selected = chooseBestProject(project, candidates);
        if (selected == null) {
            throw new IllegalArgumentException("GitLab 未找到匹配项目仓库，请维护 gitlab." + project.businessLine() + ".repoUrl 或检查项目编码/名称/业务线");
        }
        String repoUrl = firstNonBlank(selected.httpUrlToRepo(), selected.webUrl() == null ? null : selected.webUrl() + ".git");
        if (repoUrl == null) {
            throw new IllegalArgumentException("GitLab 项目缺少 HTTP 仓库地址：" + selected.pathWithNamespace());
        }
        log.info("GitLab repository discovered. businessLine={}, projectCode={}, projectName={}, repository={}, pathWithNamespace={}",
                project.businessLine(),
                project.code(),
                project.name(),
                repoUrl,
                selected.pathWithNamespace());
        return new ConfiguredRepository(repoUrl, accessToken);
    }

    private List<String> searchKeywords(ProjectDetailResponse project) {
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        addKeyword(keywords, project.code());
        addKeyword(keywords, project.name());
        addKeyword(keywords, project.businessLine());
        addKeyword(keywords, findGitlabGroupName(project.businessLine()));
        return new ArrayList<>(keywords);
    }

    private String findGitlabGroupName(String businessLine) {
        String group = trimToNull(businessLine);
        if (group == null) {
            return null;
        }
        BusinessLineEntity entity = businessLineMapper.findByCode(group);
        if (entity == null) {
            entity = businessLineMapper.findByName(group);
        }
        return entity == null || Boolean.FALSE.equals(entity.getEnabled()) ? null : trimToNull(entity.getGitlabGroupName());
    }

    private void addKeyword(LinkedHashSet<String> keywords, String value) {
        String normalized = trimToNull(value);
        if (normalized != null) {
            keywords.add(normalized);
        }
    }

    List<GitlabProject> searchGitlabProjects(String webApiUrl, String accessToken, String keyword) {
        try {
            String baseUrl = trimTrailingSlash(requireText(webApiUrl, "GitLab Web/API 地址不能为空"));
            String encodedKeyword = URLEncoder.encode(requireText(keyword, "GitLab 搜索关键字不能为空"), StandardCharsets.UTF_8);
            URI uri = URI.create(baseUrl + "/api/v4/projects?simple=true&per_page=20&search=" + encodedKeyword);
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(GITLAB_API_TIMEOUT)
                    .header("PRIVATE-TOKEN", requireText(accessToken, "GitLab Access Token 不能为空"))
                    .GET()
                    .build();
            log.info("GitLab project search started. keyword={}, url={}", keyword, uri);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("GitLab API 返回状态码 " + response.statusCode() + ": " + truncate(response.body(), 300));
            }
            List<?> projects = objectMapper.readValue(response.body(), List.class);
            return toGitlabProjects(projects);
        } catch (Exception ex) {
            throw new IllegalStateException("GitLab 项目搜索失败：" + summarizeException(ex), ex);
        }
    }

    List<GitlabProject> listGroupProjects(String webApiUrl, String accessToken, String gitlabGroupName) {
        try {
            String baseUrl = trimTrailingSlash(requireText(webApiUrl, "GitLab Web/API 地址不能为空"));
            String encodedBusinessLineName = URLEncoder.encode(requireText(gitlabGroupName, "GitLab 组名不能为空"), StandardCharsets.UTF_8)
                    .replace("+", "%20");
            List<GitlabProject> projects = new ArrayList<>();
            int page = 1;
            while (true) {
                URI uri = URI.create(baseUrl + "/api/v4/groups/" + encodedBusinessLineName + "/projects?include_subgroups=true&simple=true&per_page=100&page=" + page);
                HttpRequest request = HttpRequest.newBuilder(uri)
                        .timeout(GITLAB_API_TIMEOUT)
                        .header("PRIVATE-TOKEN", requireText(accessToken, "GitLab Access Token 不能为空"))
                        .GET()
                        .build();
                log.info("GitLab group project list started. gitlabGroupName={}, page={}, url={}", gitlabGroupName, page, uri);
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IllegalStateException("GitLab API 返回状态码 " + response.statusCode() + ": " + truncate(response.body(), 300));
                }
                List<?> pageItems = objectMapper.readValue(response.body(), List.class);
                projects.addAll(toGitlabProjects(pageItems));
                String nextPage = trimToNull(response.headers().firstValue("X-Next-Page").orElse(null));
                if (nextPage == null) {
                    return projects;
                }
                page = Integer.parseInt(nextPage);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("GitLab 业务线仓库查询失败：" + summarizeException(ex), ex);
        }
    }

    private List<GitlabProject> toGitlabProjects(List<?> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<GitlabProject> projects = new ArrayList<>();
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> values)) {
                continue;
            }
            projects.add(new GitlabProject(
                    stringValue(values.get("id")),
                    stringValue(values.get("name")),
                    stringValue(values.get("path")),
                    stringValue(values.get("path_with_namespace")),
                    stringValue(values.get("http_url_to_repo")),
                    stringValue(values.get("web_url"))
            ));
        }
        return projects;
    }

    GitlabProject chooseBestProject(ProjectDetailResponse project, List<GitlabProject> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        return candidates.stream()
                .filter(item -> item != null && firstNonBlank(item.httpUrlToRepo(), item.webUrl()) != null)
                .max(Comparator.comparingInt(item -> score(project, item)))
                .orElse(null);
    }

    private int score(ProjectDetailResponse project, GitlabProject candidate) {
        int score = 0;
        String combined = normalizeSearchText(String.join(" ",
                value(candidate.name()),
                value(candidate.path()),
                value(candidate.pathWithNamespace()),
                value(candidate.webUrl())
        ));
        score += scoreToken(combined, project.code(), 80);
        score += scoreToken(combined, project.name(), 60);
        score += scoreToken(combined, project.businessLine(), 30);
        score += scoreToken(combined, findGitlabGroupName(project.businessLine()), 50);
        if (normalizeSearchText(value(candidate.path())).equals(normalizeSearchText(value(project.code())))) {
            score += 100;
        }
        if (normalizeSearchText(value(candidate.name())).equals(normalizeSearchText(value(project.name())))) {
            score += 80;
        }
        return score;
    }

    private int scoreToken(String combined, String token, int weight) {
        String normalized = normalizeSearchText(token);
        return normalized.isEmpty() || !combined.contains(normalized) ? 0 : weight;
    }

    private void fetchRepository(String repoUrl, String accessToken, Path repoPath) {
        fetchRepository(repoUrl, accessToken, repoPath, true);
    }

    private synchronized void fetchRepository(String repoUrl, String accessToken, Path repoPath, boolean updateWorkingTree) {
        try {
            Files.createDirectories(repoPath.getParent());
            if (Files.exists(repoPath.resolve(".git"))) {
                String origin = runGit(List.of("git", "remote", "get-url", "origin"), repoPath).strip();
                if (!repoUrl.equals(origin)) throw new IllegalStateException("缓存仓库 origin 与已配置 GitLab 项目不一致");
                boolean shallowRepository = isShallowRepository(repoPath);
                runGit(fetchCommand(repoPath, accessToken, shallowRepository), repoPath.getParent());
                if (updateWorkingTree) updateCurrentBranch(repoPath);
                return;
            }
            if (Files.exists(repoPath)) {
                throw new IllegalStateException("目标路径已存在但不是 Git 仓库：" + repoPath);
            }
            Path staging = Files.createTempDirectory(repoPath.getParent(), ".workhub-clone-");
            try {
                runGit(cloneCommand(repoUrl, accessToken, staging), repoPath.getParent());
                Files.move(staging, repoPath);
            } finally {
                removeStaging(staging);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("GitLab 工作区仓库更新失败：" + summarizeException(ex), ex);
        }
    }

    private boolean isShallowRepository(Path repoPath) throws Exception {
        String output = runGit(
                List.of("git", "-C", repoPath.toString(), "rev-parse", "--is-shallow-repository"),
                repoPath.getParent()
        );
        return Boolean.parseBoolean(output.trim());
    }

    List<String> fetchCommand(Path repoPath, String accessToken, boolean shallowRepository) {
        List<String> command = new ArrayList<>(List.of(
                "git",
                "-C",
                repoPath.toString(),
                "-c",
                "http.extraHeader=PRIVATE-TOKEN: " + accessToken,
                "fetch"
        ));
        if (shallowRepository) {
            command.add("--unshallow");
        }
        command.add("origin");
        return List.copyOf(command);
    }

    List<String> cloneCommand(String repoUrl, String accessToken, Path repoPath) {
        return List.of(
                "git",
                "-c",
                "http.extraHeader=PRIVATE-TOKEN: " + accessToken,
                "clone",
                repoUrl,
                repoPath.toString()
        );
    }

    void updateCurrentBranch(Path repoPath) throws Exception {
        String currentBranch = runGit(
                List.of("git", "-C", repoPath.toString(), "branch", "--show-current"),
                repoPath.getParent()
        ).trim();
        if (currentBranch.isEmpty()) {
            log.warn("GitLab repository local update skipped: detached HEAD. repository={}", repoPath);
            return;
        }

        String remoteBranch = "origin/" + currentBranch;
        String remoteRef = runGit(
                List.of(
                        "git",
                        "-C",
                        repoPath.toString(),
                        "for-each-ref",
                        "--format=%(refname)",
                        "refs/remotes/" + remoteBranch
                ),
                repoPath.getParent()
        ).trim();
        if (remoteRef.isEmpty()) {
            log.warn(
                    "GitLab repository local update skipped: matching remote branch does not exist. repository={}, remoteBranch={}",
                    repoPath,
                    remoteBranch
            );
            return;
        }

        try {
            runGit(mergeCommand(repoPath, remoteBranch), repoPath.getParent());
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "远端代码未更新到本地，当前分支无法安全快进，可能存在本地改动冲突或分支分叉："
                            + repoPath + " [" + currentBranch + "]",
                    ex
            );
        }
    }

    List<String> mergeCommand(Path repoPath, String remoteBranch) {
        return List.of(
                "git",
                "-C",
                repoPath.toString(),
                "merge",
                "--ff-only",
                remoteBranch
        );
    }

    private String runGit(List<String> command, Path workingDirectory) throws Exception {
        log.info("GitLab repository sync started. command={}, workingDirectory={}", sanitizeCommand(command), workingDirectory);
        var args = new ArrayList<>(command);
        args.set(0, HostTools.git());
        String credential = null;
        for (int index = 1; index < args.size(); index++) {
            if (args.get(index).startsWith("http.extraHeader=PRIVATE-TOKEN: ")) {
                credential = args.get(index).substring("http.extraHeader=".length());
                args.set(index - 1, "-c");
                args.set(index, "http.followRedirects=false");
                args.add(index + 1, "--config-env=http.extraHeader=WORKHUB_GIT_HTTP_HEADER");
                break;
            }
        }
        ProcessBuilder processBuilder = new ProcessBuilder(args);
        if (credential != null) processBuilder.environment().put("WORKHUB_GIT_HTTP_HEADER", credential);
        processBuilder.environment().put("GIT_TERMINAL_PROMPT", "0");
        processBuilder.directory(workingDirectory.toFile());
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        var buffer = new java.io.ByteArrayOutputStream();
        Thread reader = Thread.ofVirtual().start(() -> {
            try (var input = process.getInputStream()) {
                byte[] bytes = new byte[8192];
                for (int n; (n = input.read(bytes)) >= 0;) {
                    buffer.write(bytes, 0, Math.min(n, 65536 - buffer.size()));
                }
            } catch (java.io.IOException ignored) { /* 进程退出时关闭输出流 */ }
        });
        try {
            if (!process.waitFor(GIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("git 命令超时");
            }
            reader.join(1000);
            if (reader.isAlive()) throw new IllegalStateException("git 输出等待超时");
        } finally {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            if (process.isAlive()) process.destroyForcibly();
            process.getInputStream().close();
        }
        String output = buffer.toString(StandardCharsets.UTF_8);
        if (process.exitValue() != 0) {
            throw new IllegalStateException("git 命令失败: " + truncate(output, 1000));
        }
        log.info("GitLab repository sync completed. outputPreview={}", truncate(output, 300));
        return output;
    }

    private List<String> sanitizeCommand(List<String> command) {
        return command.stream()
                .map(item -> item.startsWith("http.extraHeader=PRIVATE-TOKEN: ") ? "http.extraHeader=PRIVATE-TOKEN: ***" : item)
                .toList();
    }

    private String trimTrailingSlash(String value) {
        String normalized = requireText(value, "value");
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String firstNonBlank(String first, String second) {
        String normalized = trimToNull(first);
        return normalized == null ? trimToNull(second) : normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeSearchText(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? "" : normalized.toLowerCase().replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}\\p{IsHan}]+", "");
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
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

    String repositoryDirectoryName(GitlabProject gitlabProject, String repositoryUrl) {
        String repositoryName = firstNonBlank(
                gitlabProject == null ? null : gitlabProject.path(),
                inferRepositoryName(repositoryUrl)
        );
        return requireText(repositoryName, "仓库名称不能为空");
    }

    Path groupWorkspaceRoot(String gitlabGroupName) {
        return WorkhubPaths.namespace(codeWorkspaceRoot, gitlabGroupName);
    }

    Path repositoryPath(Path groupRoot, String repositoryName) {
        String directoryName = requireText(repositoryName, "仓库名称不能为空");
        Path repositoryPath = groupRoot.resolve(directoryName).normalize();
        if (!groupRoot.equals(repositoryPath.getParent())) {
            throw new IllegalArgumentException("仓库名称不能越过业务线工作区：" + directoryName);
        }
        return repositoryPath;
    }

    private String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String summarizeException(Exception ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? ex.getClass().getSimpleName() : truncate(message, 160);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    public record GitlabRepository(String repositoryUrl, Path localPath, String commit) {
        public GitlabRepository(String repositoryUrl, Path localPath) { this(repositoryUrl, localPath, null); }
    }

    public record GitlabProjectSystem(String systemName,
                                      String path,
                                      String pathWithNamespace,
                                      String webUrl) {
    }

    public record GitlabRepositoryBundle(String gitlabGroupName, Path localRoot, List<GitlabRepository> repositories) {

        public GitlabRepositoryBundle {
            repositories = repositories == null ? List.of() : repositories.stream()
                    .filter(Objects::nonNull)
                    .toList();
        }

        public String repositorySummary() {
            String summary = gitlabGroupName + " (" + repositories.size() + " repositories)";
            if (repositories.stream().noneMatch(repo -> repo.commit() != null)) return summary;
            Path version = localRoot;
            for (String ignored : gitlabGroupName.split("/")) version = version.getParent();
            return summary + " @" + version.getFileName();

        }
    }

    record ConfiguredRepository(String repositoryUrl, String accessToken) {
    }

    record GitlabProject(String id,
                         String name,
                         String path,
                         String path_with_namespace,
                         String http_url_to_repo,
                         String web_url) {

        String pathWithNamespace() {
            return path_with_namespace;
        }

        String httpUrlToRepo() {
            return http_url_to_repo;
        }

        String webUrl() {
            return web_url;
        }
    }
}
