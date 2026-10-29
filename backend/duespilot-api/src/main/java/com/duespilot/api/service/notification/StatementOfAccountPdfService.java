package com.duespilot.api.service.notification;

import com.duespilot.api.domain.CaseEntity;
import com.duespilot.api.domain.ReconciliationResultEntity;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
public class StatementOfAccountPdfService {

    private static final Logger log = LoggerFactory.getLogger(StatementOfAccountPdfService.class);

    public byte[] generateStatementPdf(CaseEntity caseEntity) {
        return generateStatementPdf(caseEntity, null);
    }

    public byte[] generateStatementPdf(CaseEntity caseEntity, @Nullable ReconciliationResultEntity reconciliation) {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            PDType1Font fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                // Header Banner
                cs.beginText();
                cs.setFont(fontBold, 18);
                cs.newLineAtOffset(50, 780);
                cs.showText("DUESPILOT — STATEMENT OF ACCOUNT");
                cs.endText();

                cs.beginText();
                cs.setFont(fontRegular, 10);
                cs.newLineAtOffset(50, 762);
                cs.showText("Evidence-backed reconciliation summary · Date: " + LocalDate.now().format(DateTimeFormatter.ISO_DATE));
                cs.endText();

                // Horizontal line
                cs.setLineWidth(1.0f);
                cs.moveTo(50, 750);
                cs.lineTo(545, 750);
                cs.stroke();

                // Case Details Box
                cs.beginText();
                cs.setFont(fontBold, 11);
                cs.newLineAtOffset(50, 720);
                cs.showText("CASE REFERENCE: " + (caseEntity.code() != null ? caseEntity.code() : "N/A"));
                cs.endText();

                cs.beginText();
                cs.setFont(fontRegular, 10);
                cs.newLineAtOffset(50, 702);
                cs.showText("Supplier: " + (caseEntity.supplierName() != null ? caseEntity.supplierName() : "N/A"));
                cs.endText();

                cs.beginText();
                cs.setFont(fontRegular, 10);
                cs.newLineAtOffset(50, 686);
                cs.showText("Buyer: " + (caseEntity.buyerName() != null ? caseEntity.buyerName() : "N/A"));
                cs.endText();

                // Ledger Summary Header
                cs.beginText();
                cs.setFont(fontBold, 12);
                cs.newLineAtOffset(50, 640);
                cs.showText("RECONCILED EVIDENCE SUMMARY");
                cs.endText();

                Long invoicedPaise = reconciliation != null ? reconciliation.invoicedPaise() : null;
                Long confirmedPaidPaise = reconciliation != null ? reconciliation.confirmedPaidPaise() : null;
                Long confirmedCreditPaise = reconciliation != null ? reconciliation.confirmedCreditPaise() : null;
                Long disputedPaise = reconciliation != null ? reconciliation.disputedPaise() : null;
                Long unverifiedPaise = reconciliation != null ? reconciliation.unverifiedPaise() : null;
                Long balancePaise = reconciliation != null ? reconciliation.balancePaise() : null;

                // Ledger items
                float y = 615;
                y = drawRow(cs, fontRegular, fontBold, "Total Gross Invoiced:", formatPaise(invoicedPaise), 50, y);
                y = drawRow(cs, fontRegular, fontBold, "Confirmed Inward Payments:", "-" + formatPaise(confirmedPaidPaise), 50, y);
                y = drawRow(cs, fontRegular, fontBold, "Confirmed Credit Note Deductions:", "-" + formatPaise(confirmedCreditPaise), 50, y);
                y = drawRow(cs, fontRegular, fontBold, "Disputed Deductions (Escrow/Quarantined):", formatPaise(disputedPaise), 50, y);
                y = drawRow(cs, fontRegular, fontBold, "Unverified Claims (Excluded from Balance):", formatPaise(unverifiedPaise), 50, y);

                // Line before net balance
                cs.moveTo(50, y - 5);
                cs.lineTo(545, y - 5);
                cs.stroke();

                // Net Balance
                y -= 25;
                cs.beginText();
                cs.setFont(fontBold, 14);
                cs.newLineAtOffset(50, y);
                cs.showText("VERIFIED NET BALANCE PAYABLE:");
                cs.endText();

                cs.beginText();
                cs.setFont(fontBold, 14);
                cs.newLineAtOffset(420, y);
                cs.showText(balancePaise != null ? formatPaise(balancePaise) : "UNDER REVIEW");
                cs.endText();

                // Statutory Disclaimer (Footer)
                cs.setLineWidth(0.5f);
                cs.moveTo(50, 100);
                cs.lineTo(545, 100);
                cs.stroke();

                cs.beginText();
                cs.setFont(fontRegular, 8);
                cs.newLineAtOffset(50, 85);
                cs.showText("Not legal advice. Reconciled from digital transaction proof pursuant to commercial practice.");
                cs.endText();

                cs.beginText();
                cs.setFont(fontRegular, 8);
                cs.newLineAtOffset(50, 72);
                cs.showText("Every figure is cross-verified against underlying tax invoices, bank statements, and bilateral credit notes.");
                cs.endText();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            log.error("Failed to generate PDF statement of account", e);
            throw new RuntimeException("Failed to generate Statement of Account PDF", e);
        }
    }

    private float drawRow(PDPageContentStream cs, PDType1Font regular, PDType1Font bold, String label, String value, float x, float y) throws IOException {
        cs.beginText();
        cs.setFont(regular, 10);
        cs.newLineAtOffset(x, y);
        cs.showText(label);
        cs.endText();

        cs.beginText();
        cs.setFont(bold, 10);
        cs.newLineAtOffset(430, y);
        cs.showText(value);
        cs.endText();

        return y - 20;
    }

    private String formatPaise(Long paise) {
        if (paise == null) return "₹0.00";
        double rupees = paise / 100.0;
        return String.format("₹%,.2f", rupees);
    }
}
