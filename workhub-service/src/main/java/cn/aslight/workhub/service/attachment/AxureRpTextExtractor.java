package cn.aslight.workhub.service.attachment;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * 读取已验证的 Axure 二进制 RP 字符串表及文本字段引用。
 * 不执行原型，不从任意二进制片段猜测正文，也不还原图片和交互。
 */
final class AxureRpTextExtractor {
    static final int MAX_INPUT_BYTES = 32 * 1024 * 1024;
    static final int MAX_BLOCK_BYTES = 16 * 1024 * 1024;
    static final int MAX_TOTAL_BYTES = 64 * 1024 * 1024;
    static final int MAX_BLOCKS = 1024;
    private static final int MAX_SECTION_CHARS = 16000;
    private static final int MAX_STRINGS = 100_000;
    private static final Set<String> CONTENT_TYPES = Set.of("Axure:Page", "Axure:PanelState", "Axure:Master");

    String extract(byte[] content) throws IOException {
        if (content.length > MAX_INPUT_BYTES) {
            throw new IOException("RP 文件超过 32 MiB 解析上限");
        }
        if (content.length < 4 || (content[0] & 255) != 0xac || (content[1] & 255) != 0xef
                || content[2] != 9 || content[3] != 0) {
            throw new IOException("未支持的 RP 文件结构，目前支持已验证的 Axure 二进制格式");
        }
        List<String> sections = new ArrayList<>();
        int blocks = 0;
        int total = 0;
        int pages = 0;
        int textCount = 0;
        for (int offset = 4; offset + 3 < content.length; offset++) {
            if ((content[offset] & 255) != 0x1f || (content[offset + 1] & 255) != 0x8b || content[offset + 2] != 8) {
                continue;
            }
            if (++blocks > MAX_BLOCKS) {
                throw new IOException("RP 压缩块数量超过上限");
            }
            byte[] block;
            try (GZIPInputStream input = new GZIPInputStream(new ByteArrayInputStream(content, offset, content.length - offset))) {
                block = input.readNBytes(Math.min(MAX_BLOCK_BYTES, MAX_TOTAL_BYTES - total) + 1);
            }
            total += block.length;
            if (block.length > MAX_BLOCK_BYTES || total > MAX_TOTAL_BYTES) {
                throw new IOException("RP 解压数据超过安全上限");
            }
            // 其他压缩资源（例如缩略图）不是 Axure 对象序列化块。
            if (block.length < 36 || integer(block, 0) != 27 || integer(block, 24) != 31) {
                continue;
            }
            Table table = readTable(block);
            String kind = table.strings().getFirst();
            if (CONTENT_TYPES.contains(kind)) {
                pages++;
                List<String> texts = values(block, table, Set.of("Text", "plain-text"));
                textCount += texts.size();
                sections.add("原型内容块 " + pages + "（" + kind.substring(6) + "）：\n"
                        + (texts.isEmpty() ? "[未提取到可编辑正文，可能仅包含图片]" : String.join("\n", texts)));
            } else if ("Axure:DesignDocument".equals(kind)) {
                List<String> names = values(block, table, Set.of("package-name"));
                if (!names.isEmpty()) {
                    sections.add("文档条目名称：\n" + String.join("\n", new LinkedHashSet<>(names)));
                }
            }
        }
        if (pages == 0) {
            throw new IOException("RP 未找到可识别的页面结构，文件可能损坏或版本未支持");
        }
        if (textCount == 0) {
            throw new IOException("RP 未提取到可编辑正文，图片、交互和部分备注未识别");
        }
        return "[RP 部分文本提取：图片、交互及部分备注未识别；内容块顺序不代表页面顺序，数字与字段对应关系需核对]\n\n"
                + String.join("\n\n", sections);
    }

    private Table readTable(byte[] block) throws IOException {
        int count = integer(block, 28);
        if (count < 1 || count > MAX_STRINGS) {
            throw new IOException("RP 字符串表数量无效");
        }
        List<String> strings = new ArrayList<>(count);
        int position = 32;
        for (int index = 0; index < count; index++) {
            if (position > block.length - 4) {
                throw new IOException("RP 字符串表不完整");
            }
            int length = integer(block, position);
            position += 4;
            if (length < 0 || length > block.length - position) {
                throw new IOException("RP 字符串长度无效");
            }
            try {
                strings.add(StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(block, position, length)).toString());
            } catch (CharacterCodingException ex) {
                throw new IOException("RP 字符串编码无效", ex);
            }
            position += length;
        }
        return new Table(strings, position);
    }

    private List<String> values(byte[] block, Table table, Set<String> keys) {
        Set<Integer> keyIds = new LinkedHashSet<>();
        for (int index = 0; index < table.strings().size(); index++) {
            if (keys.contains(table.strings().get(index))) {
                keyIds.add(index + 1);
            }
        }
        List<String> result = new ArrayList<>();
        int characters = 0;
        // 已验证格式：8 + 一基字符串索引表示字符串；属性和值均使用该引用。
        for (int offset = table.end(); offset <= block.length - 16; offset++) {
            if (integer(block, offset) != 8 || !keyIds.contains(integer(block, offset + 4))
                    || integer(block, offset + 8) != 8) {
                continue;
            }
            int valueId = integer(block, offset + 12);
            if (valueId > 0 && valueId <= table.strings().size()) {
                String value = table.strings().get(valueId - 1).strip();
                if (!value.isEmpty()) {
                    int remaining = MAX_SECTION_CHARS - characters;
                    if (value.length() > remaining) {
                        result.add(value.substring(0, remaining) + "\n[原型文字已截断]");
                        break;
                    }
                    result.add(value);
                    characters += value.length();
                }
            }
        }
        return result;
    }

    private static int integer(byte[] bytes, int offset) {
        return (bytes[offset] & 255) | ((bytes[offset + 1] & 255) << 8)
                | ((bytes[offset + 2] & 255) << 16) | ((bytes[offset + 3] & 255) << 24);
    }

    private record Table(List<String> strings, int end) { }
}
