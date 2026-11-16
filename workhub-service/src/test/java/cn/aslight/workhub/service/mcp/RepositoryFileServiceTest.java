package cn.aslight.workhub.service.mcp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RepositoryFileServiceTest {

    @TempDir
    Path root;

    private RepositoryFileService serviceWithRoot() {
        return new RepositoryFileService(root.toString());
    }

    @Test
    void blankRootsDisableWholeService() {
        assertFalse(new RepositoryFileService("").enabled());
        assertFalse(new RepositoryFileService(null).enabled());
        assertTrue(serviceWithRoot().enabled());
    }

    @Test
    void listReadAndSearchInsideWhitelistedRoot() throws Exception {
        RepositoryFileService service = serviceWithRoot();
        Path module = root.resolve("biz-module");
        Files.createDirectories(module.resolve("src/main"));
        Files.writeString(module.resolve("src/main/App.java"), "class App {}\n");
        Files.writeString(module.resolve("README.md"), "一行说明\n需求关键词在此\n");

        Map<String, Object> listed = service.list(module.toString(), null, null);
        assertEquals(4, ((List<?>) listed.get("entries")).size()); // src、src/main、App.java、README.md

        Map<String, Object> read = service.readTextFile(module.resolve("README.md").toString(), null);
        assertTrue(((String) read.get("content")).contains("需求关键词"));

        Map<String, Object> searched = service.searchText(module.toString(), "需求关键词", null);
        assertEquals(1, ((List<?>) searched.get("matches")).size());
    }

    @Test
    void outsideRootAndRootItselfAreRejected() {
        RepositoryFileService service = serviceWithRoot();
        assertThrows(IllegalArgumentException.class, () -> service.readTextFile("/etc/passwd", null));
        assertThrows(IllegalArgumentException.class, () -> service.list(root.toString(), null, null));
        assertThrows(IllegalArgumentException.class, () -> service.searchText(root.resolve("../outside").normalize().toString(), "x", null));
    }

    @Test
    void symlinksAndBinaryAndOversizedContentAreRejected() throws Exception {
        RepositoryFileService service = serviceWithRoot();
        Path inner = root.resolve("inner");
        Files.createDirectories(inner);
        Path target = inner.resolve("target.txt");
        Files.writeString(target, "text");
        Files.createSymbolicLink(inner.resolve("link.txt"), target);

        assertThrows(Exception.class, () -> service.readTextFile(inner.resolve("link.txt").toString(), null));

        Path binary = inner.resolve("blob.bin");
        Files.write(binary, new byte[]{1, 0, 2, 3});
        assertThrows(IllegalArgumentException.class, () -> service.readTextFile(binary.toString(), null));

        Path big = inner.resolve("big.txt");
        Files.writeString(big, "x".repeat(2048));
        assertThrows(IllegalArgumentException.class, () -> service.readTextFile(big.toString(), 1024));
    }
}
