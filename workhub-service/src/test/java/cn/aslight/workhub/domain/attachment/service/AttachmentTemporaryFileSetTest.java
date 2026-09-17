package cn.aslight.workhub.service.attachment;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttachmentTemporaryFileSetTest {

    @Test
    void materializeShouldCreateAndCleanDatabaseAttachmentFiles() throws Exception {
        byte[] content = "image-content".getBytes();
        AttachmentService.AttachmentFileContext context = new AttachmentService.AttachmentFileContext(
                7L,
                "截图",
                "approval.png",
                null,
                "image/png",
                content,
                (long) content.length,
                null
        );
        Path temporaryDirectory;
        Path temporaryFile;

        try (AttachmentTemporaryFileSet files = AttachmentTemporaryFileSet.materialize(List.of(context))) {
            temporaryDirectory = files.temporaryDirectory();
            temporaryFile = Path.of(files.contexts().getFirst().storagePath());
            assertNotNull(temporaryDirectory);
            assertTrue(Files.isRegularFile(temporaryFile));
            assertArrayEquals(content, Files.readAllBytes(temporaryFile));
        }

        assertFalse(Files.exists(temporaryFile));
        assertFalse(Files.exists(temporaryDirectory));
    }
}
