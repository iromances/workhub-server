package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.model.project.ProjectDetailResponse;
import cn.aslight.workhub.dao.project.BusinessLineMapper;
import cn.aslight.workhub.model.project.BusinessLineEntity;
import cn.aslight.workhub.service.system.SysConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GitlabRepositoryServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void chooseBestProject_shouldPreferProjectCodeAndNameMatch() {
        GitlabRepositoryService service = new GitlabRepositoryService(
                mock(SysConfigService.class),
                mock(BusinessLineMapper.class),
                new ObjectMapper()
        );
        ProjectDetailResponse project = new ProjectDetailResponse(
                8L,
                "WCH-PAY",
                "沃橙支付项目",
                "研发",
                "供应链科技",
                "admin",
                "ACTIVE",
                null,
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        GitlabRepositoryService.GitlabProject selected = service.chooseBestProject(
                project,
                List.of(
                        new GitlabRepositoryService.GitlabProject("1", "公共组件", "common", "platform/common", "http://gitlab/common.git", "http://gitlab/common"),
                        new GitlabRepositoryService.GitlabProject("2", "沃橙支付项目", "wch-pay", "supply/wch-pay", "http://gitlab/supply/wch-pay.git", "http://gitlab/supply/wch-pay")
                )
        );

        assertEquals("http://gitlab/supply/wch-pay.git", selected.httpUrlToRepo());
    }

    @Test
    void repositoryDirectoryName_shouldKeepGitlabProjectPathAsIs() {
        GitlabRepositoryService service = new GitlabRepositoryService(
                mock(SysConfigService.class),
                mock(BusinessLineMapper.class),
                new ObjectMapper()
        );
        GitlabRepositoryService.GitlabProject project = new GitlabRepositoryService.GitlabProject(
                "3",
                "Foo Service",
                "foo service (new)",
                "platform/foo service (new)",
                "http://gitlab/platform/foo-service.git",
                "http://gitlab/platform/foo-service"
        );

        assertEquals("foo service (new)", service.repositoryDirectoryName(project, project.httpUrlToRepo()));
    }

    @Test
    void repositoryDirectoryName_shouldKeepInferredRepositoryNameAsIs() {
        GitlabRepositoryService service = new GitlabRepositoryService(
                mock(SysConfigService.class),
                mock(BusinessLineMapper.class),
                new ObjectMapper()
        );
        GitlabRepositoryService.GitlabProject project = new GitlabRepositoryService.GitlabProject(
                "4",
                "Foo Service",
                null,
                "platform/foo service",
                "http://gitlab/platform/foo service.git",
                "http://gitlab/platform/foo service"
        );

        assertEquals("foo service", service.repositoryDirectoryName(project, project.httpUrlToRepo()));
    }

    @Test
    void groupWorkspaceRoot_shouldUseGitlabGroupNameAsDirectoryName() {
        GitlabRepositoryService service = new GitlabRepositoryService(
                mock(SysConfigService.class),
                mock(BusinessLineMapper.class),
                new ObjectMapper(),
                Path.of("/Users/aslight/IDEAWorkspace")
        );

        assertEquals(
                Path.of("/Users/aslight/IDEAWorkspace/ca-assets"),
                service.groupWorkspaceRoot("ca-assets")
        );
    }

    @Test
    void groupWorkspaceRoot_shouldRejectPathTraversal() {
        GitlabRepositoryService service = new GitlabRepositoryService(
                mock(SysConfigService.class),
                mock(BusinessLineMapper.class),
                new ObjectMapper(),
                Path.of("/Users/aslight/IDEAWorkspace")
        );

        assertThrows(IllegalArgumentException.class, () -> service.groupWorkspaceRoot("../outside"));
    }

    @Test
    void repositoryPath_shouldRejectNestedOrParentPath() {
        GitlabRepositoryService service = new GitlabRepositoryService(
                mock(SysConfigService.class),
                mock(BusinessLineMapper.class),
                new ObjectMapper(),
                Path.of("/Users/aslight/IDEAWorkspace")
        );
        Path groupRoot = service.groupWorkspaceRoot("ca-assets");

        assertThrows(IllegalArgumentException.class, () -> service.repositoryPath(groupRoot, "../outside"));
        assertThrows(IllegalArgumentException.class, () -> service.repositoryPath(groupRoot, "subgroup/repository"));
    }

    @Test
    void fetchCommand_shouldUnshallowExistingShallowRepository() {
        GitlabRepositoryService service = new GitlabRepositoryService(
                mock(SysConfigService.class),
                mock(BusinessLineMapper.class),
                new ObjectMapper(),
                Path.of("/Users/aslight/IDEAWorkspace")
        );

        List<String> command = service.fetchCommand(
                Path.of("/Users/aslight/IDEAWorkspace/ca-assets/assets-payment"),
                "token",
                true
        );

        assertTrue(command.contains("--unshallow"));
        assertFalse(command.contains("--depth"));
        assertEquals("origin", command.get(command.size() - 1));
    }

    @Test
    void fetchCommand_shouldUseNormalFetchForCompleteRepository() {
        GitlabRepositoryService service = new GitlabRepositoryService(
                mock(SysConfigService.class),
                mock(BusinessLineMapper.class),
                new ObjectMapper(),
                Path.of("/Users/aslight/IDEAWorkspace")
        );

        List<String> command = service.fetchCommand(
                Path.of("/Users/aslight/IDEAWorkspace/ca-assets/assets-payment"),
                "token",
                false
        );

        assertFalse(command.contains("--unshallow"));
        assertFalse(command.contains("--depth"));
        assertEquals("origin", command.get(command.size() - 1));
    }

    @Test
    void cloneCommand_shouldCloneCompleteRepository() {
        GitlabRepositoryService service = new GitlabRepositoryService(
                mock(SysConfigService.class),
                mock(BusinessLineMapper.class),
                new ObjectMapper(),
                Path.of("/Users/aslight/IDEAWorkspace")
        );

        List<String> command = service.cloneCommand(
                "http://gitlab/ca-assets/assets-payment.git",
                "token",
                Path.of("/Users/aslight/IDEAWorkspace/ca-assets/assets-payment")
        );

        assertFalse(command.contains("--depth"));
        assertEquals("clone", command.get(3));
        assertEquals("http://gitlab/ca-assets/assets-payment.git", command.get(4));
    }

    @Test
    void updateCurrentBranch_shouldKeepNonConflictingChangesAndRejectConflictingChanges() throws Exception {
        Path origin = tempDir.resolve("origin.git");
        Path source = tempDir.resolve("source");
        Path local = tempDir.resolve("local");
        git(tempDir, "init", "--bare", origin.toString());
        git(tempDir, "clone", origin.toString(), source.toString());
        git(source, "config", "user.name", "WorkHub Test");
        git(source, "config", "user.email", "workhub-test@example.com");
        Files.writeString(source.resolve("shared.txt"), "base\n");
        git(source, "add", "shared.txt");
        git(source, "commit", "-m", "base");
        git(source, "push", "-u", "origin", "HEAD");
        git(tempDir, "clone", origin.toString(), local.toString());

        Files.writeString(local.resolve("local-only.txt"), "local change\n");
        Files.writeString(source.resolve("remote-only.txt"), "remote change\n");
        git(source, "add", "remote-only.txt");
        git(source, "commit", "-m", "remote non-conflicting change");
        git(source, "push");
        git(local, "fetch", "origin");

        GitlabRepositoryService service = new GitlabRepositoryService(null, null, null, tempDir);
        service.updateCurrentBranch(local);

        assertEquals(git(source, "rev-parse", "HEAD"), git(local, "rev-parse", "HEAD"));
        assertEquals("local change\n", Files.readString(local.resolve("local-only.txt")));
        assertFalse(Files.exists(local.resolve(".git/MERGE_HEAD")));

        Files.writeString(local.resolve("shared.txt"), "local conflicting change\n");
        Files.writeString(source.resolve("shared.txt"), "remote conflicting change\n");
        git(source, "add", "shared.txt");
        git(source, "commit", "-m", "remote conflicting change");
        git(source, "push");
        git(local, "fetch", "origin");
        String headBeforeConflict = git(local, "rev-parse", "HEAD");

        assertThrows(IllegalStateException.class, () -> service.updateCurrentBranch(local));
        assertEquals(headBeforeConflict, git(local, "rev-parse", "HEAD"));
        assertEquals("local conflicting change\n", Files.readString(local.resolve("shared.txt")));
        assertFalse(Files.exists(local.resolve(".git/MERGE_HEAD")));
    }

    @Test
    void findGitlabGroupName_shouldResolveBusinessLineCode() throws Exception {
        BusinessLineMapper businessLineMapper = mock(BusinessLineMapper.class);
        BusinessLineEntity businessLine = new BusinessLineEntity();
        businessLine.setBusinessLineCode("BL000003");
        businessLine.setBusinessLineName("嘉泰资产平台");
        businessLine.setGitlabGroupName("th-jiatai-amp");
        businessLine.setEnabled(true);
        when(businessLineMapper.findByCode("BL000003")).thenReturn(businessLine);

        GitlabRepositoryService service = new GitlabRepositoryService(
                mock(SysConfigService.class),
                businessLineMapper,
                new ObjectMapper()
        );

        Method method = GitlabRepositoryService.class.getDeclaredMethod("findGitlabGroupName", String.class);
        method.setAccessible(true);

        assertEquals("th-jiatai-amp", method.invoke(service, "BL000003"));
    }

    @Test
    void freezesBothSubgroupsAtSelectedRevisionsAndKeepsUncommittedMaterials() throws Exception {
        Path cache = Files.createDirectories(tempDir.resolve("含 空格/repos"));
        var service = new GitlabRepositoryService(mock(SysConfigService.class), mock(BusinessLineMapper.class),
                new ObjectMapper(), cache);
        var sources = new ArrayList<GitlabRepositoryService.GitlabRepository>();
        for (String name : List.of("一组", "二组")) {
            Path source = Files.createDirectories(cache.resolve("company").resolve(name).resolve("api"));
            git(source, "init", "-b", "main");
            Files.writeString(source.resolve("version.txt"), name);
            git(source, "add", "version.txt");
            git(source, "-c", "user.name=test", "-c", "user.email=test@example.invalid", "commit", "-m", "initial");
            git(source, "update-ref", "refs/remotes/origin/test", "HEAD");
            Files.writeString(source.resolve("version.txt"), "未提交材料");
            sources.add(new GitlabRepositoryService.GitlabRepository("https://example.invalid/" + name, source));
        }
        var group = new GitlabRepositoryService.GitlabRepositoryBundle("company", cache.resolve("company"), sources);
        var snapshot = service.snapshot(group, "test");
        assertEquals(snapshot, service.snapshot(group, "test"));
        for (var repo : snapshot.repositories()) {
            assertEquals(repo.localPath().getParent().getFileName().toString(), Files.readString(repo.localPath().resolve("version.txt")));
            assertEquals(repo.commit(), git(repo.localPath(), "rev-parse", "HEAD"));
        }
        assertEquals("未提交材料", Files.readString(sources.getFirst().localPath().resolve("version.txt")));
        assertThrows(IllegalStateException.class, () -> service.snapshot(group, "missing"));
    }

    @Test
    void readsRemoteStandardsFromOneCommitAndKeepsManualOverrides() throws Exception {
        var config = mock(SysConfigService.class);
        var http = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        String sha = "f".repeat(40);
        http.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String text = path.contains("/commits/") ? "{\"id\":\"" + sha + "\"}"
                    : path.endsWith("/流程.md/raw") ? "远端流程" : path.endsWith("/design.md/raw") ? "远端模板" : null;
            byte[] bytes = text == null ? new byte[0] : text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(text == null ? 404 : 200, bytes.length);
            try (var body = exchange.getResponseBody()) { body.write(bytes); }
        });
        http.start();
        try {
            when(config.requirePlainValue("gitlab.global", "webApiUrl")).thenReturn("http://127.0.0.1:" + http.getAddress().getPort());
            when(config.requirePlainValue("gitlab.global", "accessToken")).thenReturn("test");
            var service = new GitlabRepositoryService(config, mock(BusinessLineMapper.class), new ObjectMapper(), tempDir);
            var synced = service.syncStandards(tempDir, "company/policy", "release/v1", "docs/研发规范/SDD");
            var knowledge = new ProjectKnowledgeBaseService(config, null, new ObjectMapper(),
                    mock(org.springframework.transaction.PlatformTransactionManager.class));
            Path override = tempDir.resolve("研发规范/SDD/design.md");
            Files.createDirectories(override.getParent());
            Files.writeString(override, "本地模板 V1");
            String first = knowledge.readSddSnapshot(tempDir, synced);
            Files.writeString(override, "本地模板 V2");
            assertTrue(first.contains("远端流程") && first.contains("本地模板 V1"));
            assertFalse(first.contains("远端模板"));
            assertTrue(knowledge.readSddSnapshot(tempDir, synced).contains("本地模板 V2"));
            assertEquals("远端模板", Files.readString(synced.resolve("design.md")));
        } finally {
            http.stop(0);
        }
    }

    private String git(Path workingDirectory, String... arguments) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command)
                .directory(workingDirectory.toFile())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes()).trim();
        assertEquals(0, process.waitFor(), output);
        return output;
    }
}
