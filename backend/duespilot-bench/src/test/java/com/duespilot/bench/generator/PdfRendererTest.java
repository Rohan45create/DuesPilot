package com.duespilot.bench.generator;

import java.io.InputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PdfRendererTest {

    @Test
    void canLoadDevanagariFontAndRenderPdf() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            try (InputStream is = getClass().getResourceAsStream("/fonts/Poppins-Regular.ttf")) {
                assertThat(is).isNotNull();
                PDType0Font font = PDType0Font.load(doc, is);
                assertThat(font).isNotNull();

                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(font, 12);
                    cs.newLineAtOffset(50, 750);
                    cs.showText("Tax Invoice / कर बीजक - ₹ 1,18,000");
                    cs.newLineAtOffset(0, -20);
                    cs.showText("नमस्ते, भुगतान प्राप्त हो गया है।");
                    cs.endText();
                }
            }

            assertThat(doc.getNumberOfPages()).isEqualTo(1);
        }
    }

    @Test
    void canRenderInvoiceInMemoryWithoutFilesystem() throws Exception {
        byte[] pdfBytes = PdfRenderer.renderInvoiceLayout1(
            null,
            "Precision Engineering Ltd",
            "27AAAAA0000A1Z5",
            "Plot 12, MIDC Bhosari, Pune 411026",
            "Apex Infotech Pvt Ltd",
            "27BBBBB0000B1Z5",
            "Tower 3, Hinjawadi Phase 2, Pune 411057",
            "INV-TEST-001",
            "2024-03-01",
            "PO-TEST-001",
            java.util.List.of(new PdfRenderer.LineItem("Precision CNC Milled Parts", "8482", 10, "PCS", 1000000L, 10000000L)),
            10000000L,
            900000L,
            900000L,
            11800000L,
            "45 days from delivery"
        );

        assertThat(pdfBytes).isNotEmpty();
        assertThat(new String(pdfBytes, 0, 5, java.nio.charset.StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
    }
}
