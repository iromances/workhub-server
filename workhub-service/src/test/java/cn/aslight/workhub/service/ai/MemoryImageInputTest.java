package cn.aslight.workhub.service.ai;

import cn.aslight.workhub.service.attachment.AttachmentService.AttachmentFileContext;
import cn.aslight.workhub.service.attachment.AttachmentTemporaryFileSet;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MemoryImageInputTest {
    @Test
    void apiUsesBytesAndCliRemovesItsWindowsSafeCopy() throws Exception {
        var image = new AttachmentFileContext(7L, "image", "截图:1?.png", null, "image/png", new byte[]{1, 2}, 2L, null);
        assertTrue(AiHttpPayloadSupport.chatContent("test", List.of(), List.of(image)).toString().contains("data:image/png;base64,AQI="));
        assertTrue(AiHttpPayloadSupport.responsesInput("test", List.of(), List.of(image)).toString().contains("data:image/png;base64,AQI="));
        Path copy;
        try (var files = AttachmentTemporaryFileSet.materialize(List.of(image))) {
            copy = Path.of(files.contexts().getFirst().storagePath());
            assertEquals("7-image.png", copy.getFileName().toString());
            assertArrayEquals(image.fileContent(), Files.readAllBytes(copy));
        }
        assertFalse(Files.exists(copy));
    }
}
