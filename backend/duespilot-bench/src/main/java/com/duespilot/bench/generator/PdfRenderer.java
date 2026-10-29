package com.duespilot.bench.generator;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Calendar;
import java.util.List;
import java.util.Objects;
import java.util.TimeZone;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Deterministic PDF renderer using Apache PDFBox with Poppins font (supporting Latin and Devanagari).
 *
 * Enforces:
 * - Deterministic output (fixed metadata timestamps).
 * - Full Devanagari Hindi, English, and Hinglish rendering.
 * - 3 distinct invoice layouts + PO, Delivery Challan, Payment Advice, Credit Note, and Message transcripts.
 */
public final class PdfRenderer {

    private static final byte[] FONT_BYTES;

    static {
        try (InputStream is = PdfRenderer.class.getResourceAsStream("/fonts/Poppins-Regular.ttf")) {
            Objects.requireNonNull(is, "Resource /fonts/Poppins-Regular.ttf not found on classpath");
            FONT_BYTES = is.readAllBytes();
        } catch (Exception ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    private PdfRenderer() {
    }

    private static PDType0Font loadFont(PDDocument doc) throws Exception {
        return PDType0Font.load(doc, new ByteArrayInputStream(FONT_BYTES));
    }

    private static void setFixedMetadata(PDDocument doc, String title) {
        PDDocumentInformation info = doc.getDocumentInformation();
        Calendar fixedCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        fixedCal.setTimeInMillis(1707000000000L); // Fixed timestamp: 2024-02-04
        info.setCreationDate(fixedCal);
        info.setModificationDate(fixedCal);
        info.setProducer("DuesPilot Benchmark Generator");
        info.setAuthor("DuesPilot Synthetic Engine");
        info.setTitle(title);
        info.setSubject("MSME Adjudication Benchmark Evidence");
    }

    // =========================================================================
    // LAYOUT 1: Intrastate Tax Invoice (CGST + SGST Split)
    // =========================================================================
    public static byte[] renderInvoiceLayout1(
        @Nullable Path outputPath,
        @NonNull String supplierName,
        @NonNull String supplierGstin,
        @NonNull String supplierAddress,
        @NonNull String buyerName,
        @NonNull String buyerGstin,
        @NonNull String buyerAddress,
        @NonNull String invoiceNo,
        @NonNull String invoiceDate,
        @NonNull String poRef,
        @NonNull List<LineItem> items,
        long taxablePaise,
        long cgstPaise,
        long sgstPaise,
        long totalPaise,
        @NonNull String paymentTerms
    ) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            setFixedMetadata(doc, "Tax Invoice " + invoiceNo);
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDType0Font font = loadFont(doc);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                // Header banner
                drawHeaderBanner(cs, "TAX INVOICE / कर बीजक (INTRA-STATE SUPPLY)", font, 800);

                // Supplier info (Left)
                float y = 770;
                drawText(cs, font, 11, 40, y, supplierName);
                y -= 14;
                drawText(cs, font, 9, 40, y, supplierAddress);
                y -= 12;
                drawText(cs, font, 9, 40, y, "GSTIN: " + supplierGstin + " | State: Maharashtra (27)");
                y -= 12;
                drawText(cs, font, 9, 40, y, "MSME Reg (Udyam): UDYAM-MH-12-0019284 (Micro Enterprise)");

                // Invoice metadata (Right)
                float ry = 770;
                drawText(cs, font, 10, 360, ry, "Invoice No: " + invoiceNo);
                ry -= 14;
                drawText(cs, font, 9, 360, ry, "Invoice Date: " + invoiceDate);
                ry -= 12;
                drawText(cs, font, 9, 360, ry, "PO Ref: " + poRef);
                ry -= 12;
                drawText(cs, font, 9, 360, ry, "Terms: " + paymentTerms);

                // Divider
                drawLine(cs, 40, 715, 555, 715);

                // Billed To / Consignee
                y = 700;
                drawText(cs, font, 10, 40, y, "Billed To / Consignee:");
                y -= 13;
                drawText(cs, font, 10, 40, y, buyerName);
                y -= 12;
                drawText(cs, font, 9, 40, y, buyerAddress);
                y -= 12;
                drawText(cs, font, 9, 40, y, "GSTIN: " + buyerGstin + " | State: Maharashtra (27)");

                drawLine(cs, 40, 650, 555, 650);

                // Line items table
                y = 635;
                drawTableHeader(cs, font, y, new String[]{"S.No", "Description / HSN", "Qty", "Rate (Rs)", "Taxable (Rs)"});
                y -= 18;

                int sNo = 1;
                for (LineItem item : items) {
                    drawTableRow(cs, font, y, new String[]{
                        String.valueOf(sNo++),
                        item.description() + " (" + item.hsn() + ")",
                        String.valueOf(item.qty()) + " " + item.unit(),
                        formatRupees(item.unitRatePaise()),
                        formatRupees(item.taxablePaise())
                    });
                    y -= 16;
                }

                drawLine(cs, 40, y, 555, y);
                y -= 18;

                // Tax calculation section
                drawText(cs, font, 9, 320, y, "Taxable Value:");
                drawRightText(cs, font, 9, 540, y, formatRupees(taxablePaise));
                y -= 14;
                drawText(cs, font, 9, 320, y, "Central Tax (CGST 9%):");
                drawRightText(cs, font, 9, 540, y, formatRupees(cgstPaise));
                y -= 14;
                drawText(cs, font, 9, 320, y, "State Tax (SGST 9%):");
                drawRightText(cs, font, 9, 540, y, formatRupees(sgstPaise));
                y -= 14;
                drawLine(cs, 310, y + 10, 555, y + 10);
                drawText(cs, font, 11, 320, y, "Total Invoice Value (Rs):");
                drawRightText(cs, font, 11, 540, y, formatRupees(totalPaise));

                // Bank details & MSMED Act prompt notice
                y = 480;
                drawText(cs, font, 9, 40, y, "Bank Details for NEFT/RTGS Transfer:");
                y -= 12;
                drawText(cs, font, 9, 40, y, "Bank: State Bank of India | A/C No: 40192837465 | IFSC: SBIN0001234");
                y -= 14;
                drawText(cs, font, 8, 40, y, "Statutory Notice: Payment governed by MSMED Act 2006. Interest payable under Section 16 for delays.");

                // Signature
                drawText(cs, font, 9, 400, 430, "For " + supplierName);
                drawText(cs, font, 9, 400, 390, "Authorized Signatory");
            }

            return saveDoc(doc, outputPath);
        }
    }

    // =========================================================================
    // LAYOUT 2: Interstate Tax Invoice (IGST Split)
    // =========================================================================
    public static byte[] renderInvoiceLayout2(
        @Nullable Path outputPath,
        @NonNull String supplierName,
        @NonNull String supplierGstin,
        @NonNull String supplierAddress,
        @NonNull String buyerName,
        @NonNull String buyerGstin,
        @NonNull String buyerAddress,
        @NonNull String invoiceNo,
        @NonNull String invoiceDate,
        @NonNull String poRef,
        @NonNull List<LineItem> items,
        long taxablePaise,
        long igstPaise,
        long totalPaise,
        @NonNull String paymentTerms
    ) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            setFixedMetadata(doc, "Inter-State Tax Invoice " + invoiceNo);
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDType0Font font = loadFont(doc);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                drawHeaderBanner(cs, "TAX INVOICE (INTER-STATE SUPPLY - IGST)", font, 800);

                // Two column header box
                float y = 765;
                drawText(cs, font, 10, 40, y, "SUPPLIER (DISPATCH FROM):");
                drawText(cs, font, 10, 320, y, "BUYER (BILL TO & SHIP TO):");
                y -= 14;
                drawText(cs, font, 10, 40, y, supplierName);
                drawText(cs, font, 10, 320, y, buyerName);
                y -= 12;
                drawText(cs, font, 9, 40, y, supplierAddress);
                drawText(cs, font, 9, 320, y, buyerAddress);
                y -= 12;
                drawText(cs, font, 9, 40, y, "GSTIN: " + supplierGstin);
                drawText(cs, font, 9, 320, y, "GSTIN: " + buyerGstin);
                y -= 12;
                drawText(cs, font, 9, 40, y, "State: Karnataka (Code: 29)");
                drawText(cs, font, 9, 320, y, "Place of Supply: Maharashtra (27)");

                drawLine(cs, 40, 700, 555, 700);

                // Invoice details
                y = 685;
                drawText(cs, font, 9, 40, y, "Invoice No: " + invoiceNo + " | Date: " + invoiceDate + " | PO: " + poRef);
                drawText(cs, font, 9, 360, y, "Agreed Payment Terms: " + paymentTerms);

                drawLine(cs, 40, 670, 555, 670);

                // Table
                y = 650;
                drawTableHeader(cs, font, y, new String[]{"Item", "Description / HSN", "Qty", "Unit Price", "Taxable (Rs)"});
                y -= 18;

                int sNo = 1;
                for (LineItem item : items) {
                    drawTableRow(cs, font, y, new String[]{
                        String.valueOf(sNo++),
                        item.description() + " [HSN " + item.hsn() + "]",
                        item.qty() + " " + item.unit(),
                        formatRupees(item.unitRatePaise()),
                        formatRupees(item.taxablePaise())
                    });
                    y -= 16;
                }

                drawLine(cs, 40, y, 555, y);
                y -= 18;

                // IGST Calculation
                drawText(cs, font, 9, 320, y, "Taxable Amount:");
                drawRightText(cs, font, 9, 540, y, formatRupees(taxablePaise));
                y -= 14;
                drawText(cs, font, 9, 320, y, "Integrated Tax (IGST 18%):");
                drawRightText(cs, font, 9, 540, y, formatRupees(igstPaise));
                y -= 14;
                drawLine(cs, 310, y + 10, 555, y + 10);
                drawText(cs, font, 11, 320, y, "Total Payable (Rs):");
                drawRightText(cs, font, 11, 540, y, formatRupees(totalPaise));

                // Payment instructions
                y = 480;
                drawText(cs, font, 9, 40, y, "Electronic Remittance Details:");
                y -= 12;
                drawText(cs, font, 9, 40, y, "HDFC Bank | A/C: 50200019283746 | IFSC: HDFC0000123");
                y -= 14;
                drawText(cs, font, 8, 40, y, "Reverse Charge applicable: NO | All disputes subject to supplier jurisdiction.");

                drawText(cs, font, 9, 400, 430, "Authorized Signatory");
                drawText(cs, font, 8, 400, 410, supplierName);
            }

            return saveDoc(doc, outputPath);
        }
    }

    // =========================================================================
    // LAYOUT 3: Compact Service Supply Invoice
    // =========================================================================
    public static byte[] renderInvoiceLayout3(
        @Nullable Path outputPath,
        @NonNull String supplierName,
        @NonNull String supplierGstin,
        @NonNull String buyerName,
        @NonNull String buyerGstin,
        @NonNull String invoiceNo,
        @NonNull String invoiceDate,
        @NonNull String poRef,
        @NonNull String serviceDescription,
        @NonNull String sacCode,
        long taxablePaise,
        long taxPaise,
        long totalPaise,
        @NonNull String paymentTerms
    ) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            setFixedMetadata(doc, "Service Invoice " + invoiceNo);
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDType0Font font = loadFont(doc);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                drawHeaderBanner(cs, "PROFESSIONAL SERVICE BILL / सेवा बीजक", font, 800);

                float y = 765;
                drawText(cs, font, 11, 40, y, supplierName);
                drawText(cs, font, 9, 350, y, "Invoice No: " + invoiceNo);
                y -= 14;
                drawText(cs, font, 9, 40, y, "GSTIN: " + supplierGstin + " | MSME Enterprise Type: MICRO");
                drawText(cs, font, 9, 350, y, "Date: " + invoiceDate);
                y -= 14;
                drawText(cs, font, 9, 40, y, "Customer: " + buyerName + " (GSTIN: " + buyerGstin + ")");
                drawText(cs, font, 9, 350, y, "PO Ref: " + poRef);

                drawLine(cs, 40, 715, 555, 715);

                y = 690;
                drawTableHeader(cs, font, y, new String[]{"No", "Service Description / SAC", "Rate (Rs)", "Tax (Rs)", "Amount (Rs)"});
                y -= 18;

                drawTableRow(cs, font, y, new String[]{
                    "1",
                    serviceDescription + " (SAC: " + sacCode + ")",
                    formatRupees(taxablePaise),
                    formatRupees(taxPaise),
                    formatRupees(totalPaise)
                });
                y -= 16;
                drawLine(cs, 40, y, 555, y);
                y -= 18;

                drawText(cs, font, 10, 320, y, "Net Total Amount:");
                drawRightText(cs, font, 10, 540, y, formatRupees(totalPaise));

                y = 550;
                drawText(cs, font, 9, 40, y, "Terms of Engagement: " + paymentTerms);
                y -= 14;
                drawText(cs, font, 8, 40, y, "Section 15 MSMED Act 2006 applies. Deemed acceptance within 15 days of milestone sign-off.");

                drawText(cs, font, 9, 400, 480, supplierName);
                drawText(cs, font, 9, 400, 450, "Partner / Director");
            }

            return saveDoc(doc, outputPath);
        }
    }

    // =========================================================================
    // PURCHASE ORDER (PO)
    // =========================================================================
    public static byte[] renderPurchaseOrder(
        @Nullable Path outputPath,
        @NonNull String buyerName,
        @NonNull String buyerGstin,
        @NonNull String supplierName,
        @NonNull String poNo,
        @NonNull String poDate,
        @NonNull String deliveryDeadline,
        @NonNull String termsClause,
        long agreedAmountPaise,
        @NonNull String scopeOfSupply
    ) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            setFixedMetadata(doc, "Purchase Order " + poNo);
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDType0Font font = loadFont(doc);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                drawHeaderBanner(cs, "OFFICIAL PURCHASE ORDER / क्रयादेश", font, 800);

                float y = 765;
                drawText(cs, font, 11, 40, y, buyerName);
                drawText(cs, font, 10, 360, y, "PO Number: " + poNo);
                y -= 14;
                drawText(cs, font, 9, 40, y, "GSTIN: " + buyerGstin);
                drawText(cs, font, 9, 360, y, "PO Date: " + poDate);
                y -= 14;
                drawText(cs, font, 10, 40, y, "Vendor / Supplier: " + supplierName);
                drawText(cs, font, 9, 360, y, "Delivery Deadline: " + deliveryDeadline);

                drawLine(cs, 40, 710, 555, 710);

                y = 680;
                drawText(cs, font, 10, 40, y, "Scope of Supply / Order Details:");
                y -= 16;
                drawText(cs, font, 9, 40, y, scopeOfSupply);
                y -= 16;
                drawText(cs, font, 10, 40, y, "Total Agreed Order Value: Rs. " + formatRupees(agreedAmountPaise));

                drawLine(cs, 40, 620, 555, 620);

                y = 600;
                drawText(cs, font, 10, 40, y, "Mandatory Commercial Terms:");
                y -= 16;
                drawText(cs, font, 9, 40, y, "1. Payment Terms: " + termsClause);
                y -= 14;
                drawText(cs, font, 9, 40, y, "2. Quality inspection to be completed upon receipt at buyer works.");
                y -= 14;
                drawText(cs, font, 9, 40, y, "3. Delivery challan and test certificates must accompany dispatch.");

                drawText(cs, font, 9, 400, 480, "For " + buyerName);
                drawText(cs, font, 9, 400, 440, "Procurement Manager");
            }

            return saveDoc(doc, outputPath);
        }
    }

    // =========================================================================
    // DELIVERY CHALLAN / PROOF OF DELIVERY (POD)
    // =========================================================================
    public static byte[] renderDeliveryChallan(
        @Nullable Path outputPath,
        @NonNull String supplierName,
        @NonNull String buyerName,
        @NonNull String challanNo,
        @NonNull String dispatchDate,
        @NonNull String poRef,
        @NonNull String invoiceRef,
        @NonNull String itemDesc,
        int qtyDispatched,
        int qtyAccepted,
        @NonNull String deliveryStatusNote
    ) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            setFixedMetadata(doc, "Delivery Challan " + challanNo);
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDType0Font font = loadFont(doc);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                drawHeaderBanner(cs, "DELIVERY CHALLAN & PROOF OF RECEIPT / सुपुर्दगी चालान", font, 800);

                float y = 765;
                drawText(cs, font, 10, 40, y, "Consignor: " + supplierName);
                drawText(cs, font, 10, 350, y, "Challan No: " + challanNo);
                y -= 14;
                drawText(cs, font, 10, 40, y, "Consignee: " + buyerName);
                drawText(cs, font, 9, 350, y, "Dispatch Date: " + dispatchDate);
                y -= 14;
                drawText(cs, font, 9, 40, y, "Referenced PO: " + poRef);
                drawText(cs, font, 9, 350, y, "Invoice No: " + invoiceRef);

                drawLine(cs, 40, 710, 555, 710);

                y = 680;
                drawTableHeader(cs, font, y, new String[]{"S.No", "Description of Goods", "Qty Dispatched", "Qty Received", "Inspection Status"});
                y -= 18;

                drawTableRow(cs, font, y, new String[]{
                    "1",
                    itemDesc,
                    String.valueOf(qtyDispatched) + " Units",
                    String.valueOf(qtyAccepted) + " Units",
                    qtyAccepted == qtyDispatched ? "Accepted OK" : "Discrepancy Noted"
                });
                y -= 16;
                drawLine(cs, 40, y, 555, y);

                y = 600;
                drawText(cs, font, 10, 40, y, "Consignee Goods Receipt & Acceptance Verification:");
                y -= 16;
                drawText(cs, font, 9, 40, y, "Remarks: " + deliveryStatusNote);
                y -= 14;
                drawText(cs, font, 9, 40, y, "Received by Warehouse Storekeeper in good condition unless noted above.");

                drawText(cs, font, 9, 40, 500, "Carrier / Transporter: VRL Logistics | LR No: VRL-892184");
                drawText(cs, font, 9, 380, 500, "Store In-charge Stamp & Signature");
            }

            return saveDoc(doc, outputPath);
        }
    }

    // =========================================================================
    // PAYMENT ADVICE / BANK REMITTANCE
    // =========================================================================
    public static byte[] renderPaymentAdvice(
        @Nullable Path outputPath,
        @NonNull String bankName,
        @NonNull String payerName,
        @NonNull String beneficiaryName,
        @NonNull String utrNumber,
        @NonNull String paymentDate,
        long grossAmountPaise,
        long deductionPaise,
        long netPaidPaise,
        @NonNull String deductionReason,
        @NonNull List<String> invoiceRefs
    ) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            setFixedMetadata(doc, "Bank Payment Advice " + utrNumber);
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDType0Font font = loadFont(doc);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                drawHeaderBanner(cs, "BANK PAYMENT ADVICE & REMITTANCE CONFIRMATION / भुगतान सूचना", font, 800);

                float y = 765;
                drawText(cs, font, 11, 40, y, bankName + " Corporate Banking");
                drawText(cs, font, 10, 350, y, "UTR: " + utrNumber);
                y -= 14;
                drawText(cs, font, 9, 40, y, "Payer (Debtor): " + payerName);
                drawText(cs, font, 9, 350, y, "Payment Date: " + paymentDate);
                y -= 14;
                drawText(cs, font, 9, 40, y, "Beneficiary: " + beneficiaryName);
                drawText(cs, font, 9, 350, y, "Mode: NEFT / RTGS");

                drawLine(cs, 40, 710, 555, 710);

                y = 680;
                drawText(cs, font, 10, 40, y, "Invoices Settled: " + String.join(", ", invoiceRefs));
                y -= 20;

                drawText(cs, font, 9, 40, y, "Gross Invoiced Value:");
                drawRightText(cs, font, 9, 300, y, "Rs. " + formatRupees(grossAmountPaise));
                y -= 16;

                if (deductionPaise > 0) {
                    drawText(cs, font, 9, 40, y, "Itemised Deductions (" + deductionReason + "):");
                    drawRightText(cs, font, 9, 300, y, "- Rs. " + formatRupees(deductionPaise));
                    y -= 16;
                }

                drawLine(cs, 40, y + 10, 310, y + 10);
                drawText(cs, font, 10, 40, y, "Net Amount Remitted:");
                drawRightText(cs, font, 10, 300, y, "Rs. " + formatRupees(netPaidPaise));

                y = 560;
                drawText(cs, font, 8, 40, y, "This is a computer-generated bank remittance advice confirming account debit.");
            }

            return saveDoc(doc, outputPath);
        }
    }

    // =========================================================================
    // CREDIT NOTE
    // =========================================================================
    public static byte[] renderCreditNote(
        @Nullable Path outputPath,
        @NonNull String supplierName,
        @NonNull String supplierGstin,
        @NonNull String buyerName,
        @NonNull String buyerGstin,
        @NonNull String creditNoteNo,
        @NonNull String creditNoteDate,
        @NonNull String invoiceRef,
        @NonNull String invoiceDate,
        long creditValuePaise,
        @NonNull String reason
    ) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            setFixedMetadata(doc, "Credit Note " + creditNoteNo);
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDType0Font font = loadFont(doc);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                drawHeaderBanner(cs, "TAX CREDIT NOTE / क्रेडिट नोट (SECTION 34 CGST ACT)", font, 800);

                float y = 765;
                drawText(cs, font, 11, 40, y, supplierName);
                drawText(cs, font, 10, 350, y, "Credit Note No: " + creditNoteNo);
                y -= 14;
                drawText(cs, font, 9, 40, y, "GSTIN: " + supplierGstin);
                drawText(cs, font, 9, 350, y, "Date: " + creditNoteDate);
                y -= 14;
                drawText(cs, font, 9, 40, y, "Issued To: " + buyerName + " (GSTIN: " + buyerGstin + ")");
                drawText(cs, font, 9, 350, y, "Against Invoice: " + invoiceRef + " dtd " + invoiceDate);

                drawLine(cs, 40, 710, 555, 710);

                y = 670;
                drawText(cs, font, 10, 40, y, "Reason for Credit Note: " + reason);
                y -= 20;
                drawText(cs, font, 11, 40, y, "Total Credit Adjustment Value: Rs. " + formatRupees(creditValuePaise));

                y = 580;
                drawText(cs, font, 8, 40, y, "Note: Corresponding reduction in supplier tax liability. Buyer to adjust input tax credit.");

                drawText(cs, font, 9, 400, 500, "For " + supplierName);
                drawText(cs, font, 9, 400, 460, "Authorized Signatory");
            }

            return saveDoc(doc, outputPath);
        }
    }

    // =========================================================================
    // MESSAGE DOCUMENT (HINDI / ENGLISH / HINGLISH)
    // =========================================================================
    public static byte[] renderMessageTranscript(
        @Nullable Path outputPath,
        @NonNull String channel,
        @NonNull String dateTimestamp,
        @NonNull String sender,
        @NonNull String recipient,
        @NonNull String subjectOrTopic,
        @NonNull String messageBody,
        @NonNull String language
    ) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            setFixedMetadata(doc, "Communication Record " + channel);
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDType0Font font = loadFont(doc);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                drawHeaderBanner(cs, "COMMUNICATION RECORD / संचार विवरण (" + channel.toUpperCase() + ")", font, 800);

                float y = 765;
                drawText(cs, font, 9, 40, y, "Channel: " + channel + " | Language: " + language);
                drawText(cs, font, 9, 350, y, "Timestamp: " + dateTimestamp);
                y -= 14;
                drawText(cs, font, 9, 40, y, "From: " + sender);
                drawText(cs, font, 9, 350, y, "To: " + recipient);
                y -= 14;
                drawText(cs, font, 9, 40, y, "Subject / Context: " + subjectOrTopic);

                drawLine(cs, 40, 715, 555, 715);

                // Message box container
                y = 690;
                drawText(cs, font, 10, 45, y, "Message Content:");
                y -= 20;

                // Split into lines for wrapping
                String[] lines = messageBody.split("\n");
                for (String line : lines) {
                    if (line.length() > 70) {
                        // Simple wrap at ~65 characters
                        int splitIdx = line.lastIndexOf(' ', 65);
                        if (splitIdx > 20) {
                            drawText(cs, font, 10, 45, y, line.substring(0, splitIdx));
                            y -= 16;
                            drawText(cs, font, 10, 45, y, line.substring(splitIdx + 1));
                        } else {
                            drawText(cs, font, 10, 45, y, line);
                        }
                    } else {
                        drawText(cs, font, 10, 45, y, line);
                    }
                    y -= 16;
                }

                y = 480;
                drawText(cs, font, 8, 40, y, "Verified evidentiary record extracted for dispute adjudication.");
            }

            return saveDoc(doc, outputPath);
        }
    }

    // =========================================================================
    // HELPER DRAWING METHODS
    // =========================================================================

    private static void drawHeaderBanner(PDPageContentStream cs, String title, PDFont font, float y) throws Exception {
        drawText(cs, font, 12, 40, y, title);
        drawLine(cs, 40, y - 6, 555, y - 6);
    }

    private static void drawText(PDPageContentStream cs, PDFont font, float size, float x, float y, String text) throws Exception {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(cleanText(text));
        cs.endText();
    }

    private static void drawRightText(PDPageContentStream cs, PDFont font, float size, float rightX, float y, String text) throws Exception {
        float textWidth = font.getStringWidth(cleanText(text)) / 1000 * size;
        drawText(cs, font, size, rightX - textWidth, y, text);
    }

    private static void drawLine(PDPageContentStream cs, float x1, float y1, float x2, float y2) throws Exception {
        cs.moveTo(x1, y1);
        cs.lineTo(x2, y2);
        cs.stroke();
    }

    private static void drawTableHeader(PDPageContentStream cs, PDFont font, float y, String[] headers) throws Exception {
        float[] colX = {40, 80, 290, 380, 470};
        for (int i = 0; i < headers.length && i < colX.length; i++) {
            drawText(cs, font, 9, colX[i], y, headers[i]);
        }
        drawLine(cs, 40, y - 4, 555, y - 4);
    }

    private static void drawTableRow(PDPageContentStream cs, PDFont font, float y, String[] cells) throws Exception {
        float[] colX = {40, 80, 290, 380, 470};
        for (int i = 0; i < cells.length && i < colX.length; i++) {
            drawText(cs, font, 8.5f, colX[i], y, cells[i]);
        }
    }

    private static String cleanText(String s) {
        return s != null ? s : "";
    }

    public static String formatRupees(long paise) {
        long absPaise = Math.abs(paise);
        long rs = absPaise / 100;
        long p = absPaise % 100;
        String sign = paise < 0 ? "-" : "";
        return String.format("%s%,d.%02d", sign, rs, p);
    }

    private static byte[] saveDoc(PDDocument doc, @Nullable Path outputPath) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        doc.save(baos);
        byte[] bytes = baos.toByteArray();
        if (outputPath != null) {
            if (outputPath.getParent() != null) {
                Files.createDirectories(outputPath.getParent());
            }
            Files.write(outputPath, bytes);
        }
        return bytes;
    }

    public record LineItem(
        @NonNull String description,
        @NonNull String hsn,
        int qty,
        @NonNull String unit,
        long unitRatePaise,
        long taxablePaise
    ) {}
}
