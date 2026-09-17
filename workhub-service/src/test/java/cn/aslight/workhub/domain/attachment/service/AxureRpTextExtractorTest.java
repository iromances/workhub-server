package cn.aslight.workhub.service.attachment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class AxureRpTextExtractorTest {
    @TempDir Path tempDir;
    private final AxureRpTextExtractor extractor = new AxureRpTextExtractor();

    @Test
    void readsReferencedTextAndPreservesNumbersButExcludesUnusedMetadata() throws Exception {
        String result = extractor.extract(sample("逾期账单未还总额：", "100.00"));
        assertTrue(result.contains("逾期账单未还总额：\n100.00"));
        assertFalse(result.contains("不应进入正文的元数据"));
    }

    @Test
    void refusesUnknownEmptyAndImageOnlyFiles() throws Exception {
        assertThrows(IOException.class, () -> extractor.extract(new byte[0]));
        assertThrows(IOException.class, () -> extractor.extract("fake.rp".getBytes(StandardCharsets.UTF_8)));
        assertTrue(assertThrows(IOException.class, () -> extractor.extract(sample())).getMessage().contains("未提取到可编辑正文"));
    }

    @Test
    void refusesTruncatedOrCorruptCompression() throws Exception {
        byte[] valid = sample("正文");
        assertThrows(IOException.class, () -> extractor.extract(java.util.Arrays.copyOf(valid, valid.length - 5)));
        valid[valid.length - 8] ^= 1;
        assertThrows(IOException.class, () -> extractor.extract(valid));
    }

    @Test
    void refusesOversizedInputAndDecompressedBlocks() throws Exception {
        assertThrows(IOException.class, () -> extractor.extract(new byte[AxureRpTextExtractor.MAX_INPUT_BYTES + 1]));
        assertTrue(assertThrows(IOException.class, () -> extractor.extract(wrap(new byte[AxureRpTextExtractor.MAX_BLOCK_BYTES + 1])))
                .getMessage().contains("上限"));
    }

    @Test
    void refusesMalformedStringTable() throws Exception {
        ByteArrayOutputStream block = header();
        number(block, 1);
        number(block, Integer.MAX_VALUE);
        assertThrows(IOException.class, () -> extractor.extract(wrap(block.toByteArray())));
    }

    @Test
    void integratesUppercaseDatabaseAndLegacyPathWithOtherAttachments() throws Exception {
        byte[] bytes = sample("回购限制");
        Path path = tempDir.resolve("prototype.rp");
        Files.write(path, bytes);
        var service = new AttachmentTextExtractionService();
        var batch = service.extractSummaries(List.of(
                context("prototype.RP", bytes),
                new AttachmentService.AttachmentFileContext(2L, "附件", "prototype.rp", path.toString(), "application/octet-stream"),
                context("broken.rp", new byte[0]),
                context("requirement.txt", "需求名称：提前买断".getBytes(StandardCharsets.UTF_8))));
        assertEquals(3, batch.summaries().size());
        assertEquals("RP", batch.summaries().getFirst().fileType());
        assertEquals(batch.summaries().get(0).summaryText(), batch.summaries().get(1).summaryText());
        assertEquals(3, batch.warnings().size());
        assertTrue(batch.warnings().getFirst().contains("图片、交互及部分备注未识别"));
        assertTrue(batch.summaries().getLast().summaryText().contains("提前买断"));
    }

    @Test
    @EnabledIfSystemProperty(named = "workhub.rp.sample", matches = ".+")
    void validatesLocalBuyoutPrototypeWithoutCommittingPrivateAttachment() throws Exception {
        byte[] bytes = Files.readAllBytes(Path.of(System.getProperty("workhub.rp.sample")));
        String result = extractor.extract(bytes);
        assertTrue(result.contains("逾期账单未还总额："));
        assertTrue(result.contains("回购限制"));
        assertTrue(result.contains("项目支持的回购应回收价款的计算方式"));
        assertTrue(result.contains("后台-【提前买断】功能"));
        assertFalse(result.contains("ColorSaturation"));
        assertFalse(result.contains("Typeface"));
    }

    public static byte[] sample(String... texts) throws IOException {
        ByteArrayOutputStream block = header();
        number(block, texts.length + 3);
        string(block, "Axure:Page");
        string(block, "Text");
        string(block, "不应进入正文的元数据");
        for (String text : texts) string(block, text);
        for (int index = 0; index < texts.length; index++) {
            number(block, 8); number(block, 2); number(block, 8); number(block, index + 4);
        }
        return wrap(block.toByteArray());
    }

    private static ByteArrayOutputStream header() {
        ByteArrayOutputStream block = new ByteArrayOutputStream();
        for (int value : new int[]{27, 9, 0, 1, 0, 50001, 31}) number(block, value);
        return block;
    }

    private static byte[] wrap(byte[] block) throws IOException {
        ByteArrayOutputStream file = new ByteArrayOutputStream();
        file.write(new byte[]{(byte) 0xac, (byte) 0xef, 9, 0});
        try (GZIPOutputStream gzip = new GZIPOutputStream(file)) { gzip.write(block); }
        return file.toByteArray();
    }

    private static void number(ByteArrayOutputStream out, int value) {
        for (int shift = 0; shift < 32; shift += 8) out.write(value >>> shift);
    }

    private static void string(ByteArrayOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        number(out, bytes.length); out.write(bytes);
    }

    private static AttachmentService.AttachmentFileContext context(String name, byte[] bytes) {
        return new AttachmentService.AttachmentFileContext(1L, "附件", name, null, "application/octet-stream", bytes, (long) bytes.length, null);
    }
}
