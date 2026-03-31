package cn.aslight.workhub.domain.attachment.service;

import cn.aslight.workhub.domain.intake.dto.IntakeAttachmentSummary;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttachmentTextExtractionServiceTest {

    private final AttachmentTextExtractionService service = new AttachmentTextExtractionService();

    @TempDir
    Path tempDir;

    @Test
    void extractSummaries_shouldReadPdfDocxAndXlsx() throws Exception {
        Path pdf = createPdf(tempDir.resolve("approval.pdf"), "Approval Code: A-001\nRequirement Name: PDF requirement");
        Path docx = createDocx(tempDir.resolve("requirement.docx"), "需求简介：Word 正文\n预估工时：2d");
        Path xlsx = createXlsx(tempDir.resolve("plan.xlsx"));

        List<AttachmentService.AttachmentFileContext> contexts = List.of(
                new AttachmentService.AttachmentFileContext(1L, "附件", pdf.getFileName().toString(), pdf.toString(), "application/pdf"),
                new AttachmentService.AttachmentFileContext(2L, "附件", docx.getFileName().toString(), docx.toString(), "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
                new AttachmentService.AttachmentFileContext(3L, "附件", xlsx.getFileName().toString(), xlsx.toString(), "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        );

        AttachmentTextExtractionService.AttachmentExtractionBatch batch = service.extractSummaries(contexts);

        assertEquals(3, batch.summaries().size());
        assertTrue(batch.warnings().isEmpty());

        IntakeAttachmentSummary pdfSummary = batch.summaries().get(0);
        assertEquals("PDF", pdfSummary.fileType());
        assertTrue(pdfSummary.summaryText().contains("Approval Code: A-001"));

        IntakeAttachmentSummary docxSummary = batch.summaries().get(1);
        assertEquals("DOCX", docxSummary.fileType());
        assertTrue(docxSummary.summaryText().contains("需求简介：Word 正文"));

        IntakeAttachmentSummary xlsxSummary = batch.summaries().get(2);
        assertEquals("XLSX", xlsxSummary.fileType());
        assertTrue(xlsxSummary.summaryText().contains("Sheet：排期"));
        assertTrue(xlsxSummary.summaryText().contains("负责人：admin"));
    }

    @Test
    void extractSummaries_shouldSkipImages() {
        List<AttachmentService.AttachmentFileContext> contexts = List.of(
                new AttachmentService.AttachmentFileContext(9L, "截图", "approval.png", tempDir.resolve("approval.png").toString(), "image/png")
        );

        AttachmentTextExtractionService.AttachmentExtractionBatch batch = service.extractSummaries(contexts);

        assertTrue(batch.summaries().isEmpty());
        assertTrue(batch.warnings().isEmpty());
    }

    private Path createPdf(Path path, String text) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(50, 700);
                for (String line : text.split("\n")) {
                    stream.showText(line);
                    stream.newLineAtOffset(0, -18);
                }
                stream.endText();
            }
            document.save(path.toFile());
        }
        return path;
    }

    private Path createDocx(Path path, String text) throws IOException {
        try (XWPFDocument document = new XWPFDocument();
             OutputStream outputStream = Files.newOutputStream(path)) {
            for (String line : text.split("\n")) {
                XWPFParagraph paragraph = document.createParagraph();
                paragraph.createRun().setText(line);
            }
            document.write(outputStream);
        }
        return path;
    }

    private Path createXlsx(Path path) throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             OutputStream outputStream = Files.newOutputStream(path)) {
            var sheet = workbook.createSheet("排期");
            var row0 = sheet.createRow(0);
            row0.createCell(0).setCellValue("负责人");
            row0.createCell(1).setCellValue("admin");
            var row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("计划完成时间");
            row1.createCell(1).setCellValue("2026/03/31");
            workbook.write(outputStream);
        }
        return path;
    }
}
