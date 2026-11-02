package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.service.system.SysConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RepositoryDocumentPathsTest {
    @TempDir Path temporary;

    static Path repository(Path root) throws Exception {
        Files.createDirectories(root.resolve("workhub-service"));
        Files.createDirectories(root.resolve("workhub-bootstrap"));
        Files.writeString(root.resolve("pom.xml"), "<project/>");
        return root;
    }

    @Test
    void locatesTheRepositoryFromAModuleAndRejectsUnrelatedWorkingDirectories() throws Exception {
        Path root = repository(temporary.resolve("workhub-server"));
        assertEquals(root, RepositoryDocumentPaths.findRepository(root.resolve("workhub-service")));
        assertThrows(IllegalStateException.class, () -> RepositoryDocumentPaths.findRepository(temporary));
    }

    @Test
    void configuredRepositoryIsIndependentFromKnowledgeVault() throws Exception {
        Path root = repository(temporary.resolve("workhub-server"));
        SysConfigService config = mock(SysConfigService.class);
        when(config.findPlainValue("knowledge.project", "repositoryPath")).thenReturn(root.toString());
        when(config.findPlainValue("knowledge.project", "vaultPath")).thenReturn(temporary.resolve("knowledge").toString());
        assertEquals(root.resolve("docs"), RepositoryDocumentPaths.resolve(config));
        assertFalse(Files.exists(root.resolve("docs")), "仅解析路径不得写入文件");
        when(config.findPlainValue("knowledge.project", "repositoryPath")).thenReturn(temporary.resolve("missing").toString());
        assertThrows(IllegalStateException.class, () -> RepositoryDocumentPaths.resolve(config));
    }

    @Test
    void rejectsKnowledgeVaultDestinationsAndLinkedDocs() throws Exception {
        Path root = repository(temporary.resolve("knowledge/workhub-server"));
        SysConfigService config = mock(SysConfigService.class);
        when(config.findPlainValue("knowledge.project", "repositoryPath")).thenReturn(root.toString());
        when(config.findPlainValue("knowledge.project", "vaultPath")).thenReturn(temporary.resolve("knowledge").toString());
        assertThrows(IllegalStateException.class, () -> RepositoryDocumentPaths.resolve(config));
        when(config.findPlainValue("knowledge.project", "vaultPath")).thenReturn(root.resolve("docs/requirement-sources").toString());
        assertThrows(IllegalStateException.class, () -> RepositoryDocumentPaths.resolve(config));
        when(config.findPlainValue("knowledge.project", "vaultPath")).thenReturn(null);
        Files.createSymbolicLink(root.resolve("docs"), temporary);
        assertThrows(IllegalStateException.class, () -> RepositoryDocumentPaths.resolve(config));
    }
}
