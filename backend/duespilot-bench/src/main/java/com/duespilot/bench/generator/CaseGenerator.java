package com.duespilot.bench.generator;

import com.duespilot.bench.model.BenchmarkCaseData;
import com.duespilot.bench.model.GroundTruth;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

/**
 * Deterministic seeded generator producing benchmark cases and PDFs in memory
 * and optionally exporting to disk per docs/architecture.md section 14 and docs/phases.md P1.
 */
public final class CaseGenerator {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
        .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
        .build();

    private final long seed;
    private final int count;
    private final @Nullable Path outDir;
    private final Random rng;

    public CaseGenerator(long seed, int count, @Nullable Path outDir) {
        this.seed = seed;
        this.count = count;
        this.outDir = outDir;
        this.rng = new Random(seed);
    }

    public static List<BenchmarkCaseData> generate(long seed, int count, @Nullable Path outDir) throws Exception {
        return new CaseGenerator(seed, count, outDir).generateCases();
    }

    public void run() throws Exception {
        generateCases();
    }

    public List<BenchmarkCaseData> generateCases() throws Exception {
        if (outDir != null) {
            Files.createDirectories(outDir);
        }

        List<BenchmarkCaseData> cases = new ArrayList<>(count);

        for (int i = 1; i <= count; i++) {
            String caseCode = String.format("GEN-%03d", i);
            Path docsDir = null;
            Path caseDir = null;

            if (outDir != null) {
                caseDir = outDir.resolve(caseCode);
                docsDir = caseDir.resolve("documents");
                Files.createDirectories(docsDir);
            }

            Map<String, byte[]> fileMap = new LinkedHashMap<>();
            GroundTruth gt = generateCase(i, caseCode, docsDir, fileMap);

            if (outDir != null && caseDir != null) {
                Path gtPath = caseDir.resolve("ground_truth.json");
                String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(gt);
                Files.writeString(gtPath, json);
            }

            cases.add(new BenchmarkCaseData("generated", caseCode, gt, fileMap));
        }

        return cases;
    }

    private static Path p(@Nullable Path docsDir, String fileName) {
        return docsDir != null ? docsDir.resolve(fileName) : null;
    }

    private GroundTruth generateCase(int index, String caseCode, @Nullable Path docsDir, Map<String, byte[]> fileMap) throws Exception {
        if (index <= 8) {
            return generateControlCase(index, caseCode, docsDir, fileMap);
        } else if (index <= 20) {
            return generateFinancialConflictCase(index, caseCode, docsDir, fileMap);
        } else if (index <= 28) {
            return generateContractDeliveryConflictCase(index, caseCode, docsDir, fileMap);
        } else if (index <= 34) {
            return generateGateCase(index, caseCode, docsDir, fileMap);
        } else {
            return generateLanguageCase(index, caseCode, docsDir, fileMap);
        }
    }

    // =========================================================================
    // 1. CONTROLS (GEN-001 to GEN-008)
    // =========================================================================
    private GroundTruth generateControlCase(int idx, String code, @Nullable Path docsDir, Map<String, byte[]> fileMap) throws Exception {
        String supplier = "Precision Engineering Works Pvt Ltd";
        String supplierGstin = "27AAPFP1029F1Z1";
        String buyer = "Apex Industrial Machinery Ltd";
        String buyerGstin = "27AABCA9102R1ZM";

        long taxable = (100_000L + (idx * 25_000L)) * 100L; // in paise
        long tax = Math.round(taxable * 0.18);
        long total = taxable + tax;
        long cgst = tax / 2;
        long sgst = tax - cgst;

        String invNo = "INV-2024-" + code;
        String poNo = "PO-2024-" + code;
        String dcNo = "DC-2024-" + code;
        String invDate = "2024-02-01";
        String poDate = "2024-01-20";
        String dcDate = "2024-02-03";

        List<PdfRenderer.LineItem> items = List.of(
            new PdfRenderer.LineItem("Precision Machined Shafts Grade EN8", "8483", 100, "PCS", taxable / 100, taxable)
        );

        List<GroundTruth.DocumentMeta> docMetas = new ArrayList<>();

        if (idx % 3 == 1) {
            fileMap.put(invNo + ".pdf", PdfRenderer.renderInvoiceLayout1(
                p(docsDir, invNo + ".pdf"),
                supplier, supplierGstin, "Plot 14, MIDC Bhosari, Pune 411026",
                buyer, buyerGstin, "Waluj Industrial Area, Aurangabad 431136",
                invNo, invDate, poNo, items, taxable, cgst, sgst, total, "30 days net"
            ));
        } else if (idx % 3 == 2) {
            fileMap.put(invNo + ".pdf", PdfRenderer.renderInvoiceLayout2(
                p(docsDir, invNo + ".pdf"),
                supplier, "29AAPFP1029F1Z5", "Peenya Industrial Area, Bengaluru 560058",
                buyer, buyerGstin, "MIDC Andheri, Mumbai 400093",
                invNo, invDate, poNo, items, taxable, tax, total, "30 days net"
            ));
        } else {
            fileMap.put(invNo + ".pdf", PdfRenderer.renderInvoiceLayout3(
                p(docsDir, invNo + ".pdf"),
                supplier, supplierGstin, buyer, buyerGstin,
                invNo, invDate, poNo, "Precision Tooling Fabrication & Calibration", "998314",
                taxable, tax, total, "30 days net"
            ));
        }
        docMetas.add(new GroundTruth.DocumentMeta("INVOICE", invNo + ".pdf", invDate, total, invNo, "EN"));

        // PO
        fileMap.put(poNo + ".pdf", PdfRenderer.renderPurchaseOrder(
            p(docsDir, poNo + ".pdf"),
            buyer, buyerGstin, supplier, poNo, poDate, "2024-02-05", "30 days net", total,
            "Supply of Precision Machined Shafts as per Drawing PEW-8921"
        ));
        docMetas.add(new GroundTruth.DocumentMeta("PO", poNo + ".pdf", poDate, total, poNo, "EN"));

        // Delivery
        fileMap.put(dcNo + ".pdf", PdfRenderer.renderDeliveryChallan(
            p(docsDir, dcNo + ".pdf"),
            supplier, buyer, dcNo, dcDate, poNo, invNo, "Precision Machined Shafts", 100, 100,
            "Received 100 units in good order and verified against drawing specifications."
        ));
        docMetas.add(new GroundTruth.DocumentMeta("DELIVERY", dcNo + ".pdf", dcDate, null, dcNo, "EN"));

        long confirmedPaid;
        long expectedBalance;
        String status;

        if (idx == 4 || idx == 6) {
            confirmedPaid = 0L;
            expectedBalance = total;
            status = "UNRECONCILED";
        } else if (idx == 7) {
            long p1 = total / 2;
            long p2 = total - p1;
            String utr1 = "UTR24021598101" + idx;
            String utr2 = "UTR24022098102" + idx;

            fileMap.put("PAY-1-" + code + ".pdf", PdfRenderer.renderPaymentAdvice(
                p(docsDir, "PAY-1-" + code + ".pdf"),
                "HDFC Bank", buyer, supplier, utr1, "2024-02-15", p1, 0, p1, "None", List.of(invNo)
            ));
            fileMap.put("PAY-2-" + code + ".pdf", PdfRenderer.renderPaymentAdvice(
                p(docsDir, "PAY-2-" + code + ".pdf"),
                "HDFC Bank", buyer, supplier, utr2, "2024-02-20", p2, 0, p2, "None", List.of(invNo)
            ));
            docMetas.add(new GroundTruth.DocumentMeta("PAYMENT", "PAY-1-" + code + ".pdf", "2024-02-15", p1, utr1, "EN"));
            docMetas.add(new GroundTruth.DocumentMeta("PAYMENT", "PAY-2-" + code + ".pdf", "2024-02-20", p2, utr2, "EN"));
            confirmedPaid = total;
            expectedBalance = 0L;
            status = "RECONCILED";
        } else {
            String utr = "UTRN2402189910" + idx;
            fileMap.put("PAY-" + code + ".pdf", PdfRenderer.renderPaymentAdvice(
                p(docsDir, "PAY-" + code + ".pdf"),
                "State Bank of India", buyer, supplier, utr, "2024-02-18", total, 0, total, "None", List.of(invNo)
            ));
            docMetas.add(new GroundTruth.DocumentMeta("PAYMENT", "PAY-" + code + ".pdf", "2024-02-18", total, utr, "EN"));
            confirmedPaid = total;
            expectedBalance = 0L;
            status = "RECONCILED";
        }

        return new GroundTruth(
            code,
            "Control Case " + idx + ": Clean standard invoice, delivery, and payment",
            "CONTROL",
            new GroundTruth.SupplierMeta(supplier, supplierGstin, "MICRO", true, "UDYAM-MH-12-0019284"),
            new GroundTruth.BuyerMeta(buyer, buyerGstin),
            new GroundTruth.FinancialGroundTruth(total, taxable, tax, confirmedPaid, 0L, 0L, 0L, expectedBalance, status),
            List.of(),
            new GroundTruth.GateStatuses("ELIGIBLE_FOR_REVIEW", "ELIGIBLE_FOR_REVIEW", "ELIGIBLE_FOR_REVIEW", "NOT_ESTABLISHED"),
            List.of(),
            List.of("ARCHIVE_SETTLED_CASE"),
            List.of("Debt is legally enforceable in court", "Buyer is liable for punitive damages", "Interest is definitively owed"),
            docMetas
        );
    }

    // =========================================================================
    // 2. FINANCIAL CONFLICTS (GEN-009 to GEN-020)
    // =========================================================================
    private GroundTruth generateFinancialConflictCase(int idx, String code, @Nullable Path docsDir, Map<String, byte[]> fileMap) throws Exception {
        String supplier = "Bharat Forgings & Auto Components";
        String supplierGstin = "27AABCB8192K1Z0";
        String buyer = "Mahindra Commercial Vehicles Ltd";
        String buyerGstin = "27AAACM1234F1Z8";

        long taxable = 200_000_00L;
        long tax = 36_000_00L;
        long total = taxable + tax; // Rs 2,36,000

        String invNo = "INV-2024-" + code;
        String poNo = "PO-2024-" + code;
        String invDate = "2024-01-15";
        String poDate = "2024-01-05";

        List<PdfRenderer.LineItem> items = List.of(
            new PdfRenderer.LineItem("Forged Gear Blanks 20MnCr5", "8708", 200, "NOS", taxable / 200, taxable)
        );

        List<GroundTruth.DocumentMeta> docMetas = new ArrayList<>();
        List<GroundTruth.ExpectedConflict> conflicts = new ArrayList<>();

        // Invoice
        fileMap.put(invNo + ".pdf", PdfRenderer.renderInvoiceLayout1(
            p(docsDir, invNo + ".pdf"),
            supplier, supplierGstin, "MIDC Chakan, Pune 410501",
            buyer, buyerGstin, "Kothrud Industrial Area, Pune 411038",
            invNo, invDate, poNo, items, taxable, tax / 2, tax / 2, total, "30 days net"
        ));
        docMetas.add(new GroundTruth.DocumentMeta("INVOICE", invNo + ".pdf", invDate, total, invNo, "EN"));

        // PO
        long poAmount = (idx == 13 || idx == 14) ? total - 30_000_00L : total;
        fileMap.put(poNo + ".pdf", PdfRenderer.renderPurchaseOrder(
            p(docsDir, poNo + ".pdf"),
            buyer, buyerGstin, supplier, poNo, poDate, "2024-01-25", "30 days net", poAmount,
            "Forged Gear Blanks as per PO Specification"
        ));
        docMetas.add(new GroundTruth.DocumentMeta("PO", poNo + ".pdf", poDate, poAmount, poNo, "EN"));

        long confirmedPaid = 0L;
        long confirmedCredit = 0L;
        long unverifiedPaid = 0L;
        long expectedBalance = total;
        String status = "UNRECONCILED";

        if (idx == 9 || idx == 10) {
            long paid = 140_000_00L;
            String utr = "UTRN240201994" + idx;
            fileMap.put("PAY-" + code + ".pdf", PdfRenderer.renderPaymentAdvice(
                p(docsDir, "PAY-" + code + ".pdf"),
                "ICICI Bank", buyer, supplier, utr, "2024-02-01", paid, 0, paid, "Partial payment", List.of(invNo)
            ));
            docMetas.add(new GroundTruth.DocumentMeta("PAYMENT", "PAY-" + code + ".pdf", "2024-02-01", paid, utr, "EN"));
            confirmedPaid = paid;
            expectedBalance = total - confirmedPaid;
            status = "PARTIAL";
            conflicts.add(new GroundTruth.ExpectedConflict("C1", "Partial payment", "Partial payment of Rs. 1,40,000 made against invoice total of Rs. 2,36,000", List.of(invNo + ".pdf", "PAY-" + code + ".pdf")));
        } else if (idx == 11 || idx == 12) {
            long cnValue = 35_400_00L;
            String cnNo = "CN-2024-" + code;
            fileMap.put(cnNo + ".pdf", PdfRenderer.renderCreditNote(
                p(docsDir, cnNo + ".pdf"),
                supplier, supplierGstin, buyer, buyerGstin, cnNo, "2024-01-28", invNo, invDate, cnValue, "Rate difference adjustment"
            ));
            docMetas.add(new GroundTruth.DocumentMeta("CREDIT_NOTE", cnNo + ".pdf", "2024-01-28", cnValue, cnNo, "EN"));
            confirmedCredit = cnValue;
            expectedBalance = total - confirmedCredit;
            status = "PARTIAL";
            conflicts.add(new GroundTruth.ExpectedConflict("C2", "Credit note unapplied", "Credit note " + cnNo + " for Rs. 35,400 unapplied against invoice " + invNo, List.of(invNo + ".pdf", cnNo + ".pdf")));
        } else if (idx == 13 || idx == 14) {
            conflicts.add(new GroundTruth.ExpectedConflict("C3", "PO/invoice amount mismatch", "Invoice amount Rs. 2,36,000 exceeds PO amount of Rs. 2,06,000", List.of(invNo + ".pdf", poNo + ".pdf")));
        } else if (idx == 15) {
            String dupInvNo = invNo + "-DUP";
            fileMap.put(dupInvNo + ".pdf", PdfRenderer.renderInvoiceLayout1(
                p(docsDir, dupInvNo + ".pdf"),
                supplier, supplierGstin, "MIDC Chakan, Pune 410501",
                buyer, buyerGstin, "Kothrud Industrial Area, Pune 411038",
                invNo, invDate, poNo, items, taxable, tax / 2, tax / 2, total, "30 days net"
            ));
            docMetas.add(new GroundTruth.DocumentMeta("INVOICE", dupInvNo + ".pdf", invDate, total, invNo, "EN"));
            conflicts.add(new GroundTruth.ExpectedConflict("C6", "Duplicate suspect", "Duplicate invoice number and amount detected for " + invNo, List.of(invNo + ".pdf", dupInvNo + ".pdf")));
        } else if (idx == 16) {
            String utr = "UTRN24021088716";
            fileMap.put("PAY-A-" + code + ".pdf", PdfRenderer.renderPaymentAdvice(
                p(docsDir, "PAY-A-" + code + ".pdf"),
                "State Bank of India", buyer, supplier, utr, "2024-02-10", total, 0, total, "Payment voucher A", List.of(invNo)
            ));
            fileMap.put("PAY-B-" + code + ".pdf", PdfRenderer.renderPaymentAdvice(
                p(docsDir, "PAY-B-" + code + ".pdf"),
                "State Bank of India", buyer, supplier, utr, "2024-02-10", total, 0, total, "Payment voucher B", List.of(invNo)
            ));
            docMetas.add(new GroundTruth.DocumentMeta("PAYMENT", "PAY-A-" + code + ".pdf", "2024-02-10", total, utr, "EN"));
            docMetas.add(new GroundTruth.DocumentMeta("PAYMENT", "PAY-B-" + code + ".pdf", "2024-02-10", total, utr, "EN"));
            confirmedPaid = total;
            expectedBalance = 0L;
            status = "RECONCILED";
            conflicts.add(new GroundTruth.ExpectedConflict("C6", "Duplicate suspect", "Same bank transaction UTR cited in multiple payment vouchers", List.of("PAY-A-" + code + ".pdf", "PAY-B-" + code + ".pdf")));
        } else if (idx == 17 || idx == 18) {
            long deduction = 25_000_00L;
            long netPaid = total - deduction;
            String utr = "UTRN24021598217";
            String reason = (idx == 17) ? "TDS Rs 4,720 + Debit Note DN-12 for handling Rs 20,280" : "Transit freight deduction and packaging penalty";
            fileMap.put("PAY-" + code + ".pdf", PdfRenderer.renderPaymentAdvice(
                p(docsDir, "PAY-" + code + ".pdf"),
                "HDFC Bank", buyer, supplier, utr, "2024-02-15", total, deduction, netPaid, reason, List.of(invNo)
            ));
            docMetas.add(new GroundTruth.DocumentMeta("PAYMENT", "PAY-" + code + ".pdf", "2024-02-15", netPaid, utr, "EN"));
            confirmedPaid = netPaid;
            expectedBalance = total - netPaid;
            status = "PARTIAL";
            conflicts.add(new GroundTruth.ExpectedConflict("C9", "Short-payment deduction", "Buyer short-paid with itemised deduction: " + reason, List.of(invNo + ".pdf", "PAY-" + code + ".pdf")));
        } else {
            String utr = "UTRN24022510919";
            long lumpSum = 500_000_00L;
            fileMap.put("PAY-" + code + ".pdf", PdfRenderer.renderPaymentAdvice(
                p(docsDir, "PAY-" + code + ".pdf"),
                "Axis Bank", buyer, supplier, utr, "2024-02-25", lumpSum, 0, lumpSum, "Lump sum remittance for multiple bills", List.of(invNo, "INV-102", "INV-103")
            ));
            docMetas.add(new GroundTruth.DocumentMeta("PAYMENT", "PAY-" + code + ".pdf", "2024-02-25", lumpSum, utr, "EN"));
            conflicts.add(new GroundTruth.ExpectedConflict("C10", "Unallocated lump-sum payment", "One payment of Rs. 5,00,000 references multiple invoices without line allocation", List.of("PAY-" + code + ".pdf")));
        }

        return new GroundTruth(
            code,
            "Financial Conflict Case " + idx + ": Rule " + (conflicts.isEmpty() ? "None" : conflicts.get(0).ruleId()),
            "FINANCIAL_CONFLICT",
            new GroundTruth.SupplierMeta(supplier, supplierGstin, "SMALL", true, "UDYAM-MH-12-0089123"),
            new GroundTruth.BuyerMeta(buyer, buyerGstin),
            new GroundTruth.FinancialGroundTruth(total, taxable, tax, confirmedPaid, confirmedCredit, unverifiedPaid, 0L, expectedBalance, status),
            conflicts,
            new GroundTruth.GateStatuses("ELIGIBLE_FOR_REVIEW", "ELIGIBLE_FOR_REVIEW", "ELIGIBLE_FOR_REVIEW", "NOT_ESTABLISHED"),
            List.of(),
            List.of("FLAG_DISCREPANCY_FOR_CONCILIATION"),
            List.of("Taxes were computed fraudulently", "Statutory interest waived"),
            docMetas
        );
    }

    // =========================================================================
    // 3. CONTRACT & DELIVERY CONFLICTS (GEN-021 to GEN-028)
    // =========================================================================
    private GroundTruth generateContractDeliveryConflictCase(int idx, String code, @Nullable Path docsDir, Map<String, byte[]> fileMap) throws Exception {
        String supplier = "Bharat Stampings & Assemblies";
        String supplierGstin = "27AABCS7712M1ZN";
        String buyer = "Mahindra Heavy Assemblies Ltd";
        String buyerGstin = "27AAACM9981K1Z2";

        long taxable = 150_000_00L;
        long tax = 27_000_00L;
        long total = taxable + tax;

        String invNo = "INV-2024-" + code;
        String poNo = "PO-2024-" + code;
        String dcNo = "DC-2024-" + code;
        String invDate = "2024-02-01";
        String poDate = "2024-01-15";

        List<PdfRenderer.LineItem> items = List.of(
            new PdfRenderer.LineItem("Cold Rolled Stamped Brackets", "870829", 500, "PCS", 300_00L, taxable)
        );

        List<GroundTruth.DocumentMeta> docMetas = new ArrayList<>();
        List<GroundTruth.ExpectedConflict> conflicts = new ArrayList<>();
        List<String> missingEvidence = new ArrayList<>();

        if (idx == 21 || idx == 22) {
            String poTerms = (idx == 21) ? "Payment in 60 days from delivery" : "Payment in 90 days from dispatch";
            String invTerms = "Immediate payment upon presentation";
            fileMap.put(invNo + ".pdf", PdfRenderer.renderInvoiceLayout1(
                p(docsDir, invNo + ".pdf"),
                supplier, supplierGstin, "MIDC Ranjangaon, Pune 412209",
                buyer, buyerGstin, "Kandivali Industrial Area, Mumbai 400067",
                invNo, invDate, poNo, items, taxable, tax / 2, tax / 2, total, invTerms
            ));
            fileMap.put(poNo + ".pdf", PdfRenderer.renderPurchaseOrder(
                p(docsDir, poNo + ".pdf"),
                buyer, buyerGstin, supplier, poNo, poDate, "2024-02-15", poTerms, total, "Cold Rolled Stamped Brackets"
            ));
            docMetas.add(new GroundTruth.DocumentMeta("INVOICE", invNo + ".pdf", invDate, total, invNo, "EN"));
            docMetas.add(new GroundTruth.DocumentMeta("PO", poNo + ".pdf", poDate, total, poNo, "EN"));
            conflicts.add(new GroundTruth.ExpectedConflict("C4", "Terms conflict", "Conflicting payment terms: PO states '" + poTerms + "' while invoice states '" + invTerms + "'", List.of(poNo + ".pdf", invNo + ".pdf")));
        } else if (idx == 23 || idx == 24) {
            fileMap.put(invNo + ".pdf", PdfRenderer.renderInvoiceLayout1(
                p(docsDir, invNo + ".pdf"),
                supplier, supplierGstin, "MIDC Ranjangaon, Pune 412209",
                buyer, buyerGstin, "Kandivali Industrial Area, Mumbai 400067",
                invNo, invDate, poNo, items, taxable, tax / 2, tax / 2, total, "30 days net"
            ));
            fileMap.put(poNo + ".pdf", PdfRenderer.renderPurchaseOrder(
                p(docsDir, poNo + ".pdf"),
                buyer, buyerGstin, supplier, poNo, poDate, "2024-02-15", "30 days net", total, "Cold Rolled Stamped Brackets"
            ));
            docMetas.add(new GroundTruth.DocumentMeta("INVOICE", invNo + ".pdf", invDate, total, invNo, "EN"));
            docMetas.add(new GroundTruth.DocumentMeta("PO", poNo + ".pdf", poDate, total, poNo, "EN"));
            missingEvidence.add("delivery_challan");
            conflicts.add(new GroundTruth.ExpectedConflict("C5", "Delivery evidence missing", "Invoice and PO present without proof of delivery or acceptance document", List.of(invNo + ".pdf")));
        } else if (idx == 25) {
            fileMap.put(invNo + ".pdf", PdfRenderer.renderInvoiceLayout1(
                p(docsDir, invNo + ".pdf"),
                supplier, supplierGstin, "MIDC Ranjangaon, Pune 412209",
                buyer, buyerGstin, "Kandivali Industrial Area, Mumbai 400067",
                invNo, invDate, poNo, items, taxable, tax / 2, tax / 2, total, "30 days net"
            ));
            fileMap.put(poNo + ".pdf", PdfRenderer.renderPurchaseOrder(
                p(docsDir, poNo + ".pdf"),
                buyer, buyerGstin, supplier, poNo, poDate, "2024-02-15", "30 days net", total, "Cold Rolled Stamped Brackets"
            ));
            fileMap.put(dcNo + ".pdf", PdfRenderer.renderDeliveryChallan(
                p(docsDir, dcNo + ".pdf"),
                supplier, buyer, dcNo, "2024-02-12", poNo, invNo, "Cold Rolled Stamped Brackets", 500, 420,
                "Short delivery: 80 units damaged during transit; 420 units accepted."
            ));
            docMetas.add(new GroundTruth.DocumentMeta("INVOICE", invNo + ".pdf", invDate, total, invNo, "EN"));
            docMetas.add(new GroundTruth.DocumentMeta("PO", poNo + ".pdf", poDate, total, poNo, "EN"));
            docMetas.add(new GroundTruth.DocumentMeta("DELIVERY", dcNo + ".pdf", "2024-02-12", null, dcNo, "EN"));
            conflicts.add(new GroundTruth.ExpectedConflict("C7", "Quantity/quality dispute", "Delivery challan records 420 accepted units against invoice billed quantity of 500 units", List.of(invNo + ".pdf", dcNo + ".pdf")));
        } else if (idx == 26) {
            fileMap.put(invNo + ".pdf", PdfRenderer.renderInvoiceLayout1(
                p(docsDir, invNo + ".pdf"),
                supplier, supplierGstin, "MIDC Ranjangaon, Pune 412209",
                buyer, buyerGstin, "Kandivali Industrial Area, Mumbai 400067",
                invNo, invDate, poNo, items, taxable, tax / 2, tax / 2, total, "30 days net"
            ));
            fileMap.put(dcNo + ".pdf", PdfRenderer.renderDeliveryChallan(
                p(docsDir, dcNo + ".pdf"),
                supplier, buyer, dcNo, "2024-02-12", poNo, invNo, "Cold Rolled Stamped Brackets", 500, 500,
                "Received 500 units subject to metallurgical lab testing."
            ));
            String msgFile = "MSG-DISPUTE-" + code + ".pdf";
            fileMap.put(msgFile, PdfRenderer.renderMessageTranscript(
                p(docsDir, msgFile),
                "Email", "2024-02-18 14:30 IST", "quality@mahindra-assemblies.com", "sales@bharatstampings.com",
                "Rejection Notice: Batch failed tensile strength requirement",
                "Dear Vendor,\nThe brackets supplied under invoice " + invNo + " failed elongation testing (12% vs req 22%).\nWe cannot accept this consignment and payment is on hold until replacement is delivered.\nRegards,\nQuality Assurance Team",
                "EN"
            ));
            docMetas.add(new GroundTruth.DocumentMeta("INVOICE", invNo + ".pdf", invDate, total, invNo, "EN"));
            docMetas.add(new GroundTruth.DocumentMeta("DELIVERY", dcNo + ".pdf", "2024-02-12", null, dcNo, "EN"));
            docMetas.add(new GroundTruth.DocumentMeta("MESSAGE", msgFile, "2024-02-18", null, null, "EN"));
            conflicts.add(new GroundTruth.ExpectedConflict("C7", "Quantity/quality dispute", "Buyer quality rejection notice states batch failed tensile specifications", List.of(invNo + ".pdf", msgFile)));
        } else {
            String dcDate = (idx == 27) ? "2024-03-01" : "2024-02-12";
            String invoiceDt = (idx == 27) ? "2024-02-01" : "2024-02-12";
            fileMap.put(invNo + ".pdf", PdfRenderer.renderInvoiceLayout1(
                p(docsDir, invNo + ".pdf"),
                supplier, supplierGstin, "MIDC Ranjangaon, Pune 412209",
                buyer, buyerGstin, "Kandivali Industrial Area, Mumbai 400067",
                invNo, invoiceDt, poNo, items, taxable, tax / 2, tax / 2, total, "30 days net"
            ));
            fileMap.put(dcNo + ".pdf", PdfRenderer.renderDeliveryChallan(
                p(docsDir, dcNo + ".pdf"),
                supplier, buyer, dcNo, dcDate, poNo, invNo, "Cold Rolled Stamped Brackets", 500, 500,
                "Delivered on " + dcDate
            ));
            docMetas.add(new GroundTruth.DocumentMeta("INVOICE", invNo + ".pdf", invoiceDt, total, invNo, "EN"));
            docMetas.add(new GroundTruth.DocumentMeta("DELIVERY", dcNo + ".pdf", dcDate, null, dcNo, "EN"));
            conflicts.add(new GroundTruth.ExpectedConflict("C8", "Date contradiction", "Document dates violate logical order (invoice predates dispatch challan by one month)", List.of(invNo + ".pdf", dcNo + ".pdf")));
        }

        return new GroundTruth(
            code,
            "Contract Delivery Conflict Case " + idx + ": Rule " + (conflicts.isEmpty() ? "None" : conflicts.get(0).ruleId()),
            "CONTRACT_DELIVERY_CONFLICT",
            new GroundTruth.SupplierMeta(supplier, supplierGstin, "SMALL", true, "UDYAM-MH-12-0045612"),
            new GroundTruth.BuyerMeta(buyer, buyerGstin),
            new GroundTruth.FinancialGroundTruth(total, taxable, tax, 0L, 0L, 0L, 0L, total, "UNRECONCILED"),
            conflicts,
            new GroundTruth.GateStatuses("ELIGIBLE_FOR_REVIEW", missingEvidence.isEmpty() ? "ELIGIBLE_FOR_REVIEW" : "NOT_ESTABLISHED", "ELIGIBLE_FOR_REVIEW", "NOT_ESTABLISHED"),
            missingEvidence,
            List.of("REQUEST_PROOF_OF_DELIVERY"),
            List.of("Delivery is legally deemed complete without POD", "Contract terms supersede statutory caps"),
            docMetas
        );
    }

    // =========================================================================
    // 4. GATE CONDITIONS & REFUSAL (GEN-029 to GEN-034)
    // =========================================================================
    private GroundTruth generateGateCase(int idx, String code, @Nullable Path docsDir, Map<String, byte[]> fileMap) throws Exception {
        String supplier = "Shree Ganesh Precision Dies";
        String supplierGstin = "27AAAPG1234F1Z9";
        String buyer = "Tata AutoComp Systems Ltd";
        String buyerGstin = "27AAACT9910D1Z1";

        long taxable = 300_000_00L;
        long tax = 54_000_00L;
        long total = taxable + tax;

        String invNo = "INV-2024-" + code;
        String poNo = "PO-2024-" + code;
        String invDate = "2024-01-20";

        List<PdfRenderer.LineItem> items = List.of(
            new PdfRenderer.LineItem("Precision Tooling Dies", "8207", 10, "SETS", 30_000_00L, taxable)
        );

        List<GroundTruth.DocumentMeta> docMetas = new ArrayList<>();
        List<GroundTruth.ExpectedConflict> conflicts = new ArrayList<>();
        List<String> missingEvidence = new ArrayList<>();

        fileMap.put(invNo + ".pdf", PdfRenderer.renderInvoiceLayout1(
            p(docsDir, invNo + ".pdf"),
            supplier, supplierGstin, "Bhosari Industrial Estate, Pune",
            buyer, buyerGstin, "Chakan Phase II, Pune",
            invNo, invDate, poNo, items, taxable, tax / 2, tax / 2, total, "30 days net"
        ));
        Long invAmountForDoc = (idx == 34) ? null : total;
        docMetas.add(new GroundTruth.DocumentMeta("INVOICE", invNo + ".pdf", invDate, invAmountForDoc, invNo, "EN"));

        String gateA = "ELIGIBLE_FOR_REVIEW";
        String gateB = "ELIGIBLE_FOR_REVIEW";
        String gateC = "ELIGIBLE_FOR_REVIEW";
        String gateD = "NOT_ESTABLISHED";
        Long expectedBal = total;
        String enterpriseType = "MICRO";
        boolean udyamReg = true;
        String udyamNo = "UDYAM-MH-12-0091823";

        if (idx == 29) {
            enterpriseType = "MEDIUM";
            gateA = "OUT_OF_SCOPE";
            gateD = "NOT_ESTABLISHED";
        } else if (idx == 30) {
            udyamReg = false;
            udyamNo = null;
            gateA = "NOT_ESTABLISHED";
            missingEvidence.add("udyam_registration");
        } else if (idx == 31) {
            gateB = "HUMAN_REVIEW_REQUIRED";
            String msgFile = "MSG-REJECT-" + code + ".pdf";
            fileMap.put(msgFile, PdfRenderer.renderMessageTranscript(
                p(docsDir, msgFile),
                "Email", "2024-01-28", "stores@tata-autocomp.com", "sales@shreeganesh.com",
                "Consignment Rejection - Missing Certificate of Analysis",
                "We cannot take delivery of tooling set INV-" + invNo + " as heat treatment certificate is missing.",
                "EN"
            ));
            docMetas.add(new GroundTruth.DocumentMeta("MESSAGE", msgFile, "2024-01-28", null, null, "EN"));
            missingEvidence.add("delivery_acceptance");
        } else if (idx == 32) {
            gateC = "HUMAN_REVIEW_REQUIRED";
            fileMap.put(poNo + ".pdf", PdfRenderer.renderPurchaseOrder(
                p(docsDir, poNo + ".pdf"),
                buyer, buyerGstin, supplier, poNo, "2024-01-10", "2024-01-25", "90 days credit", total, "Precision Dies"
            ));
            docMetas.add(new GroundTruth.DocumentMeta("PO", poNo + ".pdf", "2024-01-10", total, poNo, "EN"));
            conflicts.add(new GroundTruth.ExpectedConflict("C4", "Terms conflict", "PO payment term of 90 days exceeds Section 15 statutory cap of 45 days", List.of(poNo + ".pdf")));
        } else if (idx == 33) {
            gateD = "NOT_ESTABLISHED";
            missingEvidence.add("confirmed_bank_rate");
        } else {
            gateD = "NOT_ESTABLISHED";
            expectedBal = null;
            missingEvidence.add("invoice_amount");
        }

        return new GroundTruth(
            code,
            "Gate Condition Case " + idx + ": Gate eval",
            "GATE_ABSTENTION",
            new GroundTruth.SupplierMeta(supplier, supplierGstin, enterpriseType, udyamReg, udyamNo),
            new GroundTruth.BuyerMeta(buyer, buyerGstin),
            new GroundTruth.FinancialGroundTruth(total, taxable, tax, 0L, 0L, 0L, 0L, expectedBal, "UNRECONCILED"),
            conflicts,
            new GroundTruth.GateStatuses(gateA, gateB, gateC, gateD),
            missingEvidence,
            List.of("ABSTAIN_FROM_MSME_SAMADHAAN_FILING"),
            List.of("Gate passes automatically under presumption of regularity", "Provisional interest calculated"),
            docMetas
        );
    }

    // =========================================================================
    // 5. LANGUAGE VARIANTS (GEN-035 to GEN-040)
    // =========================================================================
    private GroundTruth generateLanguageCase(int idx, String code, @Nullable Path docsDir, Map<String, byte[]> fileMap) throws Exception {
        String supplier = "Gupta Fasteners & Industrial Supplies";
        String supplierGstin = "06AAAPG9912K1Z3";
        String buyer = "Rajasthan Heavy Electricals Ltd";
        String buyerGstin = "08AAACR8821N1ZM";

        long taxable = 100_000_00L;
        long tax = 18_000_00L;
        long total = taxable + tax;

        String invNo = "INV-2024-" + code;
        String poNo = "PO-2024-" + code;
        String msgFile = "MSG-" + code + ".pdf";

        List<PdfRenderer.LineItem> items = List.of(
            new PdfRenderer.LineItem("Industrial Steel Fasteners M16", "7318", 1000, "PCS", 100_00L, taxable)
        );

        fileMap.put(invNo + ".pdf", PdfRenderer.renderInvoiceLayout1(
            p(docsDir, invNo + ".pdf"),
            supplier, supplierGstin, "Sector 7, IMT Manesar, Haryana 122050",
            buyer, buyerGstin, "Sitapura Industrial Area, Jaipur 302022",
            invNo, "2024-02-01", poNo, items, taxable, tax / 2, tax / 2, total, "30 days net"
        ));

        List<GroundTruth.DocumentMeta> docMetas = new ArrayList<>();
        docMetas.add(new GroundTruth.DocumentMeta("INVOICE", invNo + ".pdf", "2024-02-01", total, invNo, "EN"));

        String msgBody;
        String lang;
        List<GroundTruth.ExpectedConflict> conflicts = new ArrayList<>();
        long confirmedPaid = 0L;
        long unverifiedPaid = 0L;
        long expectedBal = total;
        String status = "UNRECONCILED";

        if (idx == 35) {
            lang = "HI";
            msgBody = "नमस्ते गुप्ता जी,\nचालान संख्या " + invNo + " का कुल 1,18,000 रुपये का भुगतान हमारे रिकॉर्ड में लंबित है।\nहम अगले 10 दिनों में इसका भुगतान सुनिश्चित करेंगे। असुविधा के लिए खेद है।\n- राजेश शर्मा, वित्त विभाग";
        } else if (idx == 36) {
            lang = "HI";
            msgBody = "प्रिय महोदय,\nआपके द्वारा भेजे गए फास्टनर्स की गुणवत्ता तकनीकी मानकों के अनुसार नहीं है।\nसामग्री का निरीक्षण पूरा होने तक " + invNo + " का भुगतान रोका जा रहा है। कृपया नया बैच भेजें।";
            conflicts.add(new GroundTruth.ExpectedConflict("C7", "Quantity/quality dispute", "Hindi message states quality inspection failure and halts payment", List.of(msgFile)));
        } else if (idx == 37) {
            lang = "HINGLISH";
            msgBody = "Hello Sharma ji,\nHumne invoice " + invNo + " ke against Rs. 68,000 NEFT kar diya hai.\nRef number UTR240219821 hai. Baaki balance amount 50,000 next week tak clear kar denge.\nThanks & Regards,\nAccounts Team";
            unverifiedPaid = 68_000_00L;
        } else if (idx == 38) {
            lang = "HINGLISH";
            msgBody = "Dear Supplier,\nInvoice " + invNo + " me se transit packaging damage ka 18,000 deduct karke balance amount release kiya ja raha hai.\nDebit note details email par bhej di hai.\n- Procurement Head";
            conflicts.add(new GroundTruth.ExpectedConflict("C9", "Short-payment deduction", "Hinglish communication notifies packaging damage deduction", List.of(msgFile)));
        } else if (idx == 39) {
            lang = "HI";
            msgBody = "सुपुर्दगी सूचना:\nचालान " + invNo + " के सभी 1000 फास्टनर्स आज दिनांक 05 फरवरी 2024 को गोदाम में सुरक्षित प्राप्त हो गए हैं।\nजांच रिपोर्ट संलग्न है।";
        } else {
            lang = "HINGLISH";
            msgBody = "Mutual Settlement Agreement:\nBoth parties have agreed ki total pending amount Rs. 1,18,000 full and final settle hoga.\nSupplier will not initiate MSME Samadhaan action if paid by 15-March-2024.";
        }

        fileMap.put(msgFile, PdfRenderer.renderMessageTranscript(
            p(docsDir, msgFile),
            "WhatsApp", "2024-02-15 11:20 IST", "+91-9821098210", "+91-9876543210",
            "Payment Discussion for " + invNo, msgBody, lang
        ));
        docMetas.add(new GroundTruth.DocumentMeta("MESSAGE", msgFile, "2024-02-15", null, null, lang));

        return new GroundTruth(
            code,
            "Language Case " + idx + ": " + lang + " message processing",
            "LANGUAGE_VARIANT",
            new GroundTruth.SupplierMeta(supplier, supplierGstin, "MICRO", true, "UDYAM-HR-04-0019283"),
            new GroundTruth.BuyerMeta(buyer, buyerGstin),
            new GroundTruth.FinancialGroundTruth(total, taxable, tax, confirmedPaid, 0L, unverifiedPaid, 0L, expectedBal, status),
            conflicts,
            new GroundTruth.GateStatuses("ELIGIBLE_FOR_REVIEW", "ELIGIBLE_FOR_REVIEW", "ELIGIBLE_FOR_REVIEW", "NOT_ESTABLISHED"),
            List.of(),
            List.of("FOLLOW_UP_ON_PROMISED_PAYMENT"),
            List.of("Message promise constitutes legally binding promissory note"),
            docMetas
        );
    }
}
