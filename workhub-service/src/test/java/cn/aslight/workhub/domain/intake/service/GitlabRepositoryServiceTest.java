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
