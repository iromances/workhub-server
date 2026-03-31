package cn.aslight.workhub.domain.attachment.service;

import cn.aslight.workhub.domain.intake.dto.IntakeAttachmentSummary;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

@Service
public class AttachmentTextExtractionService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentTextExtractionService.class);
    private static final int MAX_TEXT_LENGTH = 8000;
    private static final int MAX_SHEET_ROWS = 120;
    private static final int MAX_COLUMNS = 24;

    public AttachmentExtractionBatch extractSummaries(List<AttachmentService.AttachmentFileContext> attachments) {
        List<IntakeAttachmentSummary> summaries = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (attachments == null || attachments.isEmpty()) {
            return new AttachmentExtractionBatch(List.of(), List.of());
        }
        for (AttachmentService.AttachmentFileContext attachment : attachments) {
            if (attachment == null || isImage(attachment)) {
                continue;
            }
            try {
                String extractedText = extractText(attachment);
                String normalized = normalizeText(extractedText);
                if (normalized == null) {
                    warnings.add(attachment.fileName() + " 未提取到可读正文");
                    continue;
                }
                summaries.add(new IntakeAttachmentSummary(
                        attachment.fileName(),
                        detectFileType(attachment),
                        truncate(normalized)
                ));
            } catch (Exception ex) {
                log.warn("Failed to extract attachment text. file={}", attachment.fileName(), ex);
                warnings.add(attachment.fileName() + " 提取失败: " + summarizeException(ex));
            }
        }
        return new AttachmentExtractionBatch(summaries, warnings);
    }

    String extractText(AttachmentService.AttachmentFileContext attachment) throws IOException, InvalidFormatException {
        String extension = fileExtension(attachment.fileName());
        Path path = Path.of(attachment.storagePath());
        return switch (extension) {
            case "pdf" -> extractPdf(path);
            case "docx" -> extractDocx(path);
            case "doc" -> extractDoc(path);
            case "xlsx", "xls" -> extractWorkbook(path);
            case "txt", "csv", "md", "json", "xml", "yaml", "yml", "log" -> Files.readString(path, StandardCharsets.UTF_8);
            default -> throw new IllegalArgumentException("暂不支持的附件类型");
        };
    }

    private String extractPdf(Path path) throws IOException {
        try (PDDocument document = Loader.loadPDF(path.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    private String extractDocx(Path path) throws IOException {
        try (InputStream inputStream = Files.newInputStream(path);
             XWPFDocument document = new XWPFDocument(inputStream);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    private String extractDoc(Path path) throws IOException {
        try (InputStream inputStream = Files.newInputStream(path);
             HWPFDocument document = new HWPFDocument(inputStream);
             WordExtractor extractor = new WordExtractor(document)) {
            return extractor.getText();
        }
    }

    private String extractWorkbook(Path path) throws IOException, InvalidFormatException {
        try (InputStream inputStream = Files.newInputStream(path);
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            DataFormatter formatter = new DataFormatter(Locale.CHINA);
            StringBuilder builder = new StringBuilder();
            int totalRows = 0;
            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                if (totalRows >= MAX_SHEET_ROWS) {
                    break;
                }
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                builder.append("Sheet：").append(sheet.getSheetName()).append('\n');
                for (Row row : sheet) {
                    if (totalRows >= MAX_SHEET_ROWS) {
                        break;
                    }
                    String rowText = formatRow(row, formatter);
                    if (rowText == null) {
                        continue;
                    }
                    builder.append(rowText).append('\n');
                    totalRows++;
                }
                builder.append('\n');
            }
            return builder.toString();
        }
    }

    private String formatRow(Row row, DataFormatter formatter) {
        List<String> values = new ArrayList<>();
        int lastCell = Math.min(Math.max(row.getLastCellNum(), 0), MAX_COLUMNS);
        for (int cellIndex = 0; cellIndex < lastCell; cellIndex++) {
            Cell cell = row.getCell(cellIndex);
            if (cell == null || cell.getCellType() == CellType.BLANK) {
                values.add("");
                continue;
            }
            values.add(formatter.formatCellValue(cell).trim());
        }
        while (!values.isEmpty() && values.get(values.size() - 1).isEmpty()) {
            values.remove(values.size() - 1);
        }
        if (values.isEmpty()) {
            return null;
        }
        if (values.size() == 2 && !values.get(0).isEmpty() && !values.get(1).isEmpty()) {
            return values.get(0) + "：" + values.get(1);
        }
        StringJoiner joiner = new StringJoiner("\t");
        values.forEach(joiner::add);
        return joiner.toString();
    }

    private boolean isImage(AttachmentService.AttachmentFileContext attachment) {
        String contentType = attachment.contentType();
        if (contentType != null && contentType.startsWith("image/")) {
            return true;
        }
        String extension = fileExtension(attachment.fileName());
        return "png".equals(extension)
                || "jpg".equals(extension)
                || "jpeg".equals(extension)
                || "gif".equals(extension)
                || "bmp".equals(extension)
                || "webp".equals(extension);
    }

    private String detectFileType(AttachmentService.AttachmentFileContext attachment) {
        String extension = fileExtension(attachment.fileName());
        if (!extension.isEmpty()) {
            return extension.toUpperCase(Locale.ROOT);
        }
        return attachment.contentType() == null ? "UNKNOWN" : attachment.contentType();
    }

    private String fileExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int index = fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.replace("\r", "")
                .replace('\u0000', ' ')
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String truncate(String value) {
        if (value.length() <= MAX_TEXT_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_TEXT_LENGTH) + "\n\n[已截断，保留前 " + MAX_TEXT_LENGTH + " 个字符]";
    }

    private String summarizeException(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        return message.length() <= 120 ? message : message.substring(0, 120);
    }

    public record AttachmentExtractionBatch(List<IntakeAttachmentSummary> summaries,
                                            List<String> warnings) {
    }
}
