package com.duespilot.api.service;

import com.duespilot.api.web.error.DocumentParseException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TextExtractionServiceTest {

    private final TextExtractionService extractionService = new TextExtractionService();

    @Test
    @DisplayName("Extracts text per page from multi-page PDF")
    void extractMultiPagePdf() throws Exception {
        byte[] pdfBytes;
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            PDPage page1 = new PDPage();
            doc.addPage(page1);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page1)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(100, 700);
                cs.showText("Page 1 Tax Invoice Content");
                cs.endText();
            }

            PDPage page2 = new PDPage();
            doc.addPage(page2);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page2)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(100, 700);
                cs.showText("Page 2 Terms and Delivery Challan");
                cs.endText();
            }

            doc.save(baos);
            pdfBytes = baos.toByteArray();
        }

        List<TextExtractionService.ExtractedPage> pages = extractionService.extractPages(pdfBytes, "application/pdf", "invoice.pdf");

        assertThat(pages).hasSize(2);
        assertThat(pages.get(0).pageNo()).isEqualTo(1);
        assertThat(pages.get(0).text()).contains("Page 1 Tax Invoice Content");
        assertThat(pages.get(1).pageNo()).isEqualTo(2);
        assertThat(pages.get(1).text()).contains("Page 2 Terms and Delivery Challan");
    }

    @Test
    @DisplayName("Plain-text passthrough extracts entire text as single page")
    void extractPlainText() {
        String content = "Subject: Payment confirmation for PO-9988\nWe transferred ₹1,50,000 on 2024-03-01.";
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);

        List<TextExtractionService.ExtractedPage> pages = extractionService.extractPages(bytes, "text/plain", "notes.txt");

        assertThat(pages).hasSize(1);
        assertThat(pages.get(0).pageNo()).isEqualTo(1);
        assertThat(pages.get(0).text()).isEqualTo(content);
    }

    @Test
    @DisplayName("Corrupt PDF throws DocumentParseException (422 PARSE_FAILED)")
    void corruptPdfThrowsDocumentParseException() {
        byte[] corruptBytes = "NOT_A_VALID_PDF_STREAM_CONTENT".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> extractionService.extractPages(corruptBytes, "application/pdf", "corrupt.pdf"))
            .isInstanceOf(DocumentParseException.class)
            .satisfies(ex -> {
                DocumentParseException e = (DocumentParseException) ex;
                assertThat(e.getCode()).isEqualTo("PARSE_FAILED");
                assertThat(e.getStatus().value()).isEqualTo(422);
            });
    }
}
