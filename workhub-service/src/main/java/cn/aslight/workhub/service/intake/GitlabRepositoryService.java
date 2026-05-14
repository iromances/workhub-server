package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.dao.project.ProjectGroupMapper;
import cn.aslight.workhub.model.project.ProjectGroupEntity;
import cn.aslight.workhub.service.system.SysConfigService;
import cn.aslight.workhub.model.project.ProjectDetailResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.HexFormat;
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
    private final ProjectGroupMapper projectGroupMapper;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public GitlabRepositoryService(SysConfigService sysConfigService,
                                   ProjectGroupMapper projectGroupMapper,
                                   ObjectMapper objectMapper) {
        this.sysConfigService = sysConfigService;
        this.projectGroupMapper = projectGroupMapper;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(GITLAB_API_TIMEOUT)
                .build();
    }

    public GitlabRepository resolveAndFetch(String projectGroup) {
        String group = requireText(projectGroup, "项目组不能为空");
        ConfiguredRepository repository = resolveConfiguredRepository(group);
        if (repository == null) {
            throw new IllegalArgumentException("系统配置不存在或未启用：gitlab." + group + ".repoUrl");
        }
        Path repoPath = Path.of("data", "git-cache", safeHash(group + "|" + repository.repositoryUrl())).toAbsolutePath().normalize();
        fetchRepository(repository.repositoryUrl(), repository.accessToken(), repoPath);
        return new GitlabRepository(repository.repositoryUrl(), repoPath);
    }

    public GitlabRepository resolveAndFetch(ProjectDetailResponse project) {
        if (project == null) {
            throw new IllegalArgumentException("项目不能为空");
        }
        String group = requireText(project.group(), "项目组不能为空");
        ConfiguredRepository repository = resolveConfiguredRepository(group);
        if (repository == null) {
            repository = discoverRepository(project);
        }
        Path repoPath = Path.of("data", "git-cache", safeHash(project.id() + "|" + repository.repositoryUrl())).toAbsolutePath().normalize();
        fetchRepository(repository.repositoryUrl(), repository.accessToken(), repoPath);
        return new GitlabRepository(repository.repositoryUrl(), repoPath);
    }

    /**
     * 按项目组拉取 GitLab 命名空间下全部可访问仓库，用于跨微服务任务评估。
     *
     * @param project 需求归属项目
     * @return 项目组仓库集合
     */
    public GitlabRepositoryBundle resolveAndFetchGroup(ProjectDetailResponse project) {
        if (project == null) {
            throw new IllegalArgumentException("项目不能为空");
        }
        String projectGroup = requireText(project.group(), "项目组不能为空");
        String gitlabGroupName = findGitlabGroupName(projectGroup);
        if (gitlabGroupName == null) {
            throw new IllegalArgumentException("项目组未维护 GitLab 组名，请在项目管理中维护项目组对应的 gitlabGroupName：" + projectGroup);
        }
        String webApiUrl = sysConfigService.requirePlainValue(GLOBAL_CONFIG_GROUP, "webApiUrl");
        String accessToken = sysConfigService.requirePlainValue(GLOBAL_CONFIG_GROUP, "accessToken");
        List<GitlabProject> projects = listGroupProjects(webApiUrl, accessToken, gitlabGroupName).stream()
                .filter(item -> item != null && firstNonBlank(item.httpUrlToRepo(), item.webUrl()) != null)
                .toList();
        if (projects.isEmpty()) {
            throw new IllegalArgumentException("GitLab 项目组下未找到可访问仓库：" + gitlabGroupName);
        }

        Path groupRoot = Path.of("data", "git-cache", "group-" + safeHash(gitlabGroupName)).toAbsolutePath().normalize();
        List<GitlabRepository> repositories = new ArrayList<>();
        for (GitlabProject gitlabProject : projects) {
            String repositoryUrl = firstNonBlank(
                    gitlabProject.httpUrlToRepo(),
                    gitlabProject.webUrl() == null ? null : gitlabProject.webUrl() + ".git"
            );
            String repositoryName = firstNonBlank(gitlabProject.path(), inferRepositoryName(repositoryUrl));
            Path repoPath = groupRoot.resolve(safePathName(repositoryName)).normalize();
            fetchRepository(repositoryUrl, accessToken, repoPath);
            repositories.add(new GitlabRepository(repositoryUrl, repoPath));
        }
        log.info("GitLab group repositories synced. projectGroup={}, gitlabGroupName={}, repositoryCount={}",
                projectGroup,
                gitlabGroupName,
                repositories.size());
        return new GitlabRepositoryBundle(gitlabGroupName, groupRoot, repositories);
    }

    private ConfiguredRepository resolveConfiguredRepository(String projectGroup) {
        String configGroup = "gitlab." + requireText(projectGroup, "项目组不能为空");
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
            throw new IllegalArgumentException("GitLab 未找到匹配项目仓库，请维护 gitlab." + project.group() + ".repoUrl 或检查项目编码/名称/项目组");
        }
        String repoUrl = firstNonBlank(selected.httpUrlToRepo(), selected.webUrl() == null ? null : selected.webUrl() + ".git");
        if (repoUrl == null) {
            throw new IllegalArgumentException("GitLab 项目缺少 HTTP 仓库地址：" + selected.pathWithNamespace());
        }
        log.info("GitLab repository discovered. projectGroup={}, projectCode={}, projectName={}, repository={}, pathWithNamespace={}",
                project.group(),
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
        addKeyword(keywords, project.group());
        addKeyword(keywords, findGitlabGroupName(project.group()));
        return new ArrayList<>(keywords);
    }

    private String findGitlabGroupName(String projectGroup) {
        String group = trimToNull(projectGroup);
        if (group == null) {
            return null;
        }
        ProjectGroupEntity entity = projectGroupMapper.findByName(group);
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
            String encodedGroupName = URLEncoder.encode(requireText(gitlabGroupName, "GitLab 组名不能为空"), StandardCharsets.UTF_8)
                    .replace("+", "%20");
            List<GitlabProject> projects = new ArrayList<>();
            int page = 1;
            while (true) {
                URI uri = URI.create(baseUrl + "/api/v4/groups/" + encodedGroupName + "/projects?include_subgroups=true&simple=true&per_page=100&page=" + page);
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
            throw new IllegalStateException("GitLab 项目组仓库查询失败：" + summarizeException(ex), ex);
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
        score += scoreToken(combined, project.group(), 30);
        score += scoreToken(combined, findGitlabGroupName(project.group()), 50);
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
        try {
            Files.createDirectories(repoPath.getParent());
            if (Files.exists(repoPath.resolve(".git"))) {
                runGit(List.of("git", "-C", repoPath.toString(), "-c", "http.extraHeader=PRIVATE-TOKEN: " + accessToken, "remote", "set-url", "origin", repoUrl), repoPath.getParent());
                runGit(List.of("git", "-C", repoPath.toString(), "-c", "http.extraHeader=PRIVATE-TOKEN: " + accessToken, "fetch", "--depth", "1", "origin"), repoPath.getParent());
                runGit(List.of("git", "-C", repoPath.toString(), "-c", "http.extraHeader=PRIVATE-TOKEN: " + accessToken, "remote", "set-head", "origin", "-a"), repoPath.getParent());
                runGit(List.of("git", "-C", repoPath.toString(), "reset", "--hard", "origin/HEAD"), repoPath.getParent());
                runGit(List.of("git", "-C", repoPath.toString(), "clean", "-fdx"), repoPath.getParent());
                return;
            }
            runGit(List.of("git", "-c", "http.extraHeader=PRIVATE-TOKEN: " + accessToken, "clone", "--depth", "1", repoUrl, repoPath.toString()), repoPath.getParent());
        } catch (Exception ex) {
            throw new IllegalStateException("GitLab 仓库拉取失败：" + summarizeException(ex), ex);
        }
    }

    private void runGit(List<String> command, Path workingDirectory) throws Exception {
        log.info("GitLab repository sync started. command={}, workingDirectory={}", sanitizeCommand(command), workingDirectory);
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(workingDirectory.toFile());
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        boolean finished = process.waitFor(GIT_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        String output = new String(process.getInputStream().readAllBytes());
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("git 命令超时");
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("git 命令失败: " + truncate(output, 1000));
        }
        log.info("GitLab repository sync completed. outputPreview={}", truncate(output, 300));
    }

    private List<String> sanitizeCommand(List<String> command) {
        return command.stream()
                .map(item -> item.startsWith("http.extraHeader=PRIVATE-TOKEN: ") ? "http.extraHeader=PRIVATE-TOKEN: ***" : item)
                .toList();
    }

    private String safeHash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes());
            return HexFormat.of().formatHex(digest).substring(0, 24);
        } catch (Exception ex) {
            throw new IllegalStateException("仓库缓存路径生成失败", ex);
        }
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

    private String safePathName(String value) {
        String normalized = requireText(value, "仓库名称不能为空")
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}._-]+", "-");
        return normalized.isBlank() ? safeHash(value) : normalized;
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

    public record GitlabRepository(String repositoryUrl, Path localPath) {
    }

    public record GitlabRepositoryBundle(String gitlabGroupName, Path localRoot, List<GitlabRepository> repositories) {

        public GitlabRepositoryBundle {
            repositories = repositories == null ? List.of() : repositories.stream()
                    .filter(Objects::nonNull)
                    .toList();
        }

        public String repositorySummary() {
            return gitlabGroupName + " (" + repositories.size() + " repositories)";
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
