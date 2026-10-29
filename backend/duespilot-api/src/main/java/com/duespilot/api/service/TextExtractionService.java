package com.duespilot.api.service;

import com.duespilot.api.web.error.DocumentParseException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class TextExtractionService {

    public record ExtractedPage(int pageNo, String text) {}

    public List<ExtractedPage> extractPages(byte[] content, String mimeType, String fileName) {
        String lowerName = fileName != null ? fileName.toLowerCase(Locale.ROOT) : "";
        String lowerMime = mimeType != null ? mimeType.toLowerCase(Locale.ROOT) : "";

        if (lowerMime.equals("application/pdf") || lowerName.endsWith(".pdf")) {
            return extractPdfPages(content);
        } else if (lowerMime.startsWith("text/") || lowerName.endsWith(".txt")) {
            String text = new String(content, StandardCharsets.UTF_8).trim();
            return List.of(new ExtractedPage(1, text));
        } else {
            // PNG, JPG images - single page, empty text
            return List.of(new ExtractedPage(1, ""));
        }
    }

    private List<ExtractedPage> extractPdfPages(byte[] content) {
        try (PDDocument document = Loader.loadPDF(content)) {
            int pageCount = document.getNumberOfPages();
            if (pageCount == 0) {
                return List.of(new ExtractedPage(1, ""));
            }
            PDFTextStripper stripper = new PDFTextStripper();
            List<ExtractedPage> pages = new ArrayList<>(pageCount);
            for (int p = 1; p <= pageCount; p++) {
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                String pageText = stripper.getText(document);
                pages.add(new ExtractedPage(p, pageText != null ? pageText.trim() : ""));
            }
            return pages;
        } catch (Exception e) {
            throw new DocumentParseException("Failed to parse PDF document: " + e.getMessage(), e);
        }
    }
}
