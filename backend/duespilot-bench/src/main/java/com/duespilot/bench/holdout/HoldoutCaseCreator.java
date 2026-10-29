package com.duespilot.bench.holdout;

import com.duespilot.bench.generator.PdfRenderer;
import com.duespilot.bench.model.BenchmarkCaseData;
import com.duespilot.bench.model.GroundTruth;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

/**
 * Creates the 8 hand-written holdout cases in-memory and optionally on disk
 * per docs/phases.md P1 and docs/architecture.md section 14.
 */
public final class HoldoutCaseCreator {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
        .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
        .build();

    public static void main(String[] args) throws Exception {
        Path out = null;
        if (args.length > 0) {
            out = java.nio.file.Paths.get(args[0]);
        }
        List<BenchmarkCaseData> cases = generateHoldoutCases(out);
        System.out.printf("Successfully generated %d holdout cases in memory%s%n",
            cases.size(), out != null ? (" and exported to " + out.toAbsolutePath()) : "");
    }

    private HoldoutCaseCreator() {
    }

    public static List<BenchmarkCaseData> generateHoldoutCases(@Nullable Path holdoutDir) throws Exception {
        if (holdoutDir != null) {
            Files.createDirectories(holdoutDir);
        }
        return List.of(
            createHoldout1(p(holdoutDir, "HOLD-001")),
            createHoldout2(p(holdoutDir, "HOLD-002")),
            createHoldout3(p(holdoutDir, "HOLD-003")),
            createHoldout4(p(holdoutDir, "HOLD-004")),
            createHoldout5(p(holdoutDir, "HOLD-005")),
            createHoldout6(p(holdoutDir, "HOLD-006")),
            createHoldout7(p(holdoutDir, "HOLD-007")),
            createHoldout8(p(holdoutDir, "HOLD-008"))
        );
    }

    public static void createHoldoutCases(Path holdoutDir) throws Exception {
        generateHoldoutCases(holdoutDir);
    }

    private static Path p(@Nullable Path dir, String name) {
        return dir != null ? dir.resolve(name) : null;
    }

    private static void writeCase(Path caseDir, GroundTruth gt) throws Exception {
        Files.createDirectories(caseDir);
        Path gtPath = caseDir.resolve("ground_truth.json");
        String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(gt);
        Files.writeString(gtPath, json);
    }

    // HOLD-001: Multi-item engineering supplies with partial payment + unverified message payment
    private static BenchmarkCaseData createHoldout1(@Nullable Path dir) throws Exception {
        Path docs = null;
        if (dir != null) {
            docs = dir.resolve("documents");
            Files.createDirectories(docs);
        }
        Map<String, byte[]> fileMap = new LinkedHashMap<>();

        String supplier = "Precision Forgings India LLP";
        String buyer = "Larsen & Construction Equipments Ltd";
        long taxable = 400_000_00L;
        long tax = 72_000_00L;
        long total = taxable + tax;
        long paid = 300_000_00L;
        long unverifiedMsg = 172_000_00L;
        long balance = total - paid;

        List<PdfRenderer.LineItem> items = List.of(
            new PdfRenderer.LineItem("Forged High-Tensile Flanges 150#", "7307", 200, "NOS", 1200_00L, 240_000_00L),
            new PdfRenderer.LineItem("Hardened Steel Pins 25mm", "7318", 400, "PCS", 400_00L, 160_000_00L)
        );

        fileMap.put("INV-HOLD-001.pdf", PdfRenderer.renderInvoiceLayout1(
            p(docs, "INV-HOLD-001.pdf"),
            supplier, "27AAPFP9012A1Z1", "MIDC Waluj, Chhatrapati Sambhajinagar 431136",
            buyer, "27AAACL9012R1Z8", "Powai Works, Saki Vihar Road, Mumbai 400072",
            "INV-HOLD-001", "2024-01-10", "PO-HOLD-001", items, taxable, tax / 2, tax / 2, total, "30 days net"
        ));

        fileMap.put("PO-HOLD-001.pdf", PdfRenderer.renderPurchaseOrder(
            p(docs, "PO-HOLD-001.pdf"),
            buyer, "27AAACL9012R1Z8", supplier, "PO-HOLD-001", "2023-12-28", "2024-01-15", "30 days net", total,
            "Supply of Forged Flanges and Hardened Pins as per Spec LCE-F-88"
        ));

        fileMap.put("DC-HOLD-001.pdf", PdfRenderer.renderDeliveryChallan(
            p(docs, "DC-HOLD-001.pdf"),
            supplier, buyer, "DC-HOLD-001", "2024-01-12", "PO-HOLD-001", "INV-HOLD-001",
            "Flanges (200 Nos) and Hardened Pins (400 Pcs)", 600, 600,
            "Consignment received in sound condition. Passed incoming inspection."
        ));

        fileMap.put("PAY-HOLD-001.pdf", PdfRenderer.renderPaymentAdvice(
            p(docs, "PAY-HOLD-001.pdf"),
            "Kotak Mahindra Bank", buyer, supplier, "UTRN24012588101", "2024-01-25", paid, 0, paid,
            "Part payment against INV-HOLD-001", List.of("INV-HOLD-001")
        ));

        fileMap.put("MSG-HOLD-001.pdf", PdfRenderer.renderMessageTranscript(
            p(docs, "MSG-HOLD-001.pdf"),
            "WhatsApp", "2024-02-05 10:15 IST", "+91-9820011223 (Finance Dept, L&T)", "+91-9821033445 (Precision Forgings)",
            "Balance payment discussion",
            "Hi Sharma ji, humne remaining balance Rs 1,72,000 RTGS se bhej diya hai subah. Please check account.\nAccounts Head",
            "HINGLISH"
        ));

        List<GroundTruth.DocumentMeta> docsMeta = List.of(
            new GroundTruth.DocumentMeta("INVOICE", "INV-HOLD-001.pdf", "2024-01-10", total, "INV-HOLD-001", "EN"),
            new GroundTruth.DocumentMeta("PO", "PO-HOLD-001.pdf", "2023-12-28", total, "PO-HOLD-001", "EN"),
            new GroundTruth.DocumentMeta("DELIVERY", "DC-HOLD-001.pdf", "2024-01-12", null, "DC-HOLD-001", "EN"),
            new GroundTruth.DocumentMeta("PAYMENT", "PAY-HOLD-001.pdf", "2024-01-25", paid, "UTRN24012588101", "EN"),
            new GroundTruth.DocumentMeta("MESSAGE", "MSG-HOLD-001.pdf", "2024-02-05", unverifiedMsg, null, "HINGLISH")
        );

        GroundTruth gt = new GroundTruth(
            "HOLD-001",
            "Hand-written Holdout 1: Multi-item invoice with partial payment and unverified WhatsApp claim",
            "CONTROL",
            new GroundTruth.SupplierMeta(supplier, "27AAPFP9012A1Z1", "MICRO", true, "UDYAM-MH-12-0099881"),
            new GroundTruth.BuyerMeta(buyer, "27AAACL9012R1Z8"),
            new GroundTruth.FinancialGroundTruth(total, taxable, tax, paid, 0L, unverifiedMsg, 0L, balance, "PARTIAL"),
            List.of(new GroundTruth.ExpectedConflict("C1", "Partial payment", "Confirmed payment of Rs. 3,00,000 leaves unpaid balance of Rs. 1,72,000", List.of("INV-HOLD-001.pdf", "PAY-HOLD-001.pdf"))),
            new GroundTruth.GateStatuses("ELIGIBLE_FOR_REVIEW", "ELIGIBLE_FOR_REVIEW", "ELIGIBLE_FOR_REVIEW", "NOT_ESTABLISHED"),
            List.of(),
            List.of("REQUEST_BANK_STATEMENT_FOR_UNVERIFIED_PAYMENT", "SEND_PAYMENT_REMINDER"),
            List.of("WhatsApp message payment is verified", "Interest is calculated on full invoice total"),
            docsMeta
        );

        if (dir != null) {
            writeCase(dir, gt);
        }
        return new BenchmarkCaseData("holdout", "HOLD-001", gt, fileMap);
    }

    // HOLD-002: Lump-sum payment covering two invoices (C10)
    private static BenchmarkCaseData createHoldout2(@Nullable Path dir) throws Exception {
        Path docs = null;
        if (dir != null) {
            docs = dir.resolve("documents");
            Files.createDirectories(docs);
        }
        Map<String, byte[]> fileMap = new LinkedHashMap<>();

        String supplier = "Apex Industrial Rubber Goods Pvt Ltd";
        String buyer = "Bajaj Auto Ancillaries Ltd";
        long total = 354_000_00L;
        long lumpSum = 500_000_00L;

        List<PdfRenderer.LineItem> items = List.of(
            new PdfRenderer.LineItem("Molded EPDM Gaskets & Oil Seals", "4016", 1000, "SETS", 300_00L, 300_000_00L)
        );

        fileMap.put("INV-HOLD-002.pdf", PdfRenderer.renderInvoiceLayout2(
            p(docs, "INV-HOLD-002.pdf"),
            supplier, "29AAAPA1029B1Z0", "Bommasandra Industrial Area, Bengaluru 560099",
            buyer, "27AAACB8910M1Z2", "Akurdi Industrial Estate, Pune 411035",
            "INV-HOLD-002", "2024-01-15", "PO-HOLD-002", items, 300_000_00L, 54_000_00L, total, "30 days net"
        ));

        fileMap.put("PAY-HOLD-002.pdf", PdfRenderer.renderPaymentAdvice(
            p(docs, "PAY-HOLD-002.pdf"),
            "Axis Bank", buyer, supplier, "UTRN24021599002", "2024-02-15", lumpSum, 0, lumpSum,
            "Lump sum clearing bills INV-HOLD-002 and INV-HOLD-002B", List.of("INV-HOLD-002", "INV-HOLD-002B")
        ));

        List<GroundTruth.DocumentMeta> docsMeta = List.of(
            new GroundTruth.DocumentMeta("INVOICE", "INV-HOLD-002.pdf", "2024-01-15", total, "INV-HOLD-002", "EN"),
            new GroundTruth.DocumentMeta("PAYMENT", "PAY-HOLD-002.pdf", "2024-02-15", lumpSum, "UTRN24021599002", "EN")
        );

        GroundTruth gt = new GroundTruth(
            "HOLD-002",
            "Hand-written Holdout 2: One lump-sum payment covering several invoices without allocation",
            "FINANCIAL_CONFLICT",
            new GroundTruth.SupplierMeta(supplier, "29AAAPA1029B1Z0", "SMALL", true, "UDYAM-KR-03-0019284"),
            new GroundTruth.BuyerMeta(buyer, "27AAACB8910M1Z2"),
            new GroundTruth.FinancialGroundTruth(total, 300_000_00L, 54_000_00L, 0L, 0L, 0L, 0L, total, "UNRECONCILED"),
            List.of(new GroundTruth.ExpectedConflict("C10", "Unallocated lump-sum payment", "Single remittance advice of Rs. 5,00,000 references multiple bills without itemised allocation", List.of("PAY-HOLD-002.pdf"))),
            new GroundTruth.GateStatuses("ELIGIBLE_FOR_REVIEW", "ELIGIBLE_FOR_REVIEW", "ELIGIBLE_FOR_REVIEW", "NOT_ESTABLISHED"),
            List.of(),
            List.of("REQUEST_REMITTANCE_BREAKDOWN"),
            List.of("Entire payment belongs to INV-HOLD-002"),
            docsMeta
        );

        if (dir != null) {
            writeCase(dir, gt);
        }
        return new BenchmarkCaseData("holdout", "HOLD-002", gt, fileMap);
    }

    // HOLD-003: Short payment with itemised tax deduction + freight debit note (C9)
    private static BenchmarkCaseData createHoldout3(@Nullable Path dir) throws Exception {
        Path docs = null;
        if (dir != null) {
            docs = dir.resolve("documents");
            Files.createDirectories(docs);
        }
        Map<String, byte[]> fileMap = new LinkedHashMap<>();

        String supplier = "Deccan Electro-Plating Works";
        String buyer = "Godrej Industrial Solutions Ltd";
        long total = 118_000_00L;
        long deduction = 23_000_00L;
        long netPaid = 95_000_00L;
        long balance = total - netPaid;

        fileMap.put("INV-HOLD-003.pdf", PdfRenderer.renderInvoiceLayout3(
            p(docs, "INV-HOLD-003.pdf"),
            supplier, "27AAAPD8192C1Z4", buyer, "27AAACG9910H1ZZ",
            "INV-HOLD-003", "2024-01-20", "PO-HOLD-003", "Electro-Zinc Plating & Passivation", "998818",
            100_000_00L, 18_000_00L, total, "30 days net"
        ));

        fileMap.put("PAY-HOLD-003.pdf", PdfRenderer.renderPaymentAdvice(
            p(docs, "PAY-HOLD-003.pdf"),
            "Standard Chartered Bank", buyer, supplier, "UTRN24021877003", "2024-02-18", total, deduction, netPaid,
            "TDS Sec 194C Rs 2,360 + Debit Note DN-44 freight deduction Rs 20,640", List.of("INV-HOLD-003")
        ));

        List<GroundTruth.DocumentMeta> docsMeta = List.of(
            new GroundTruth.DocumentMeta("INVOICE", "INV-HOLD-003.pdf", "2024-01-20", total, "INV-HOLD-003", "EN"),
            new GroundTruth.DocumentMeta("PAYMENT", "PAY-HOLD-003.pdf", "2024-02-18", netPaid, "UTRN24021877003", "EN")
        );

        GroundTruth gt = new GroundTruth(
            "HOLD-003",
            "Hand-written Holdout 3: Short payment with itemised statutory and commercial deduction",
            "FINANCIAL_CONFLICT",
            new GroundTruth.SupplierMeta(supplier, "27AAAPD8192C1Z4", "MICRO", true, "UDYAM-MH-12-0045182"),
            new GroundTruth.BuyerMeta(buyer, "27AAACG9910H1ZZ"),
            new GroundTruth.FinancialGroundTruth(total, 100_000_00L, 18_000_00L, netPaid, 0L, 0L, 0L, balance, "PARTIAL"),
            List.of(new GroundTruth.ExpectedConflict("C9", "Short-payment deduction", "Buyer remitted Rs. 95,000 itemising TDS and freight debit note deductions", List.of("INV-HOLD-003.pdf", "PAY-HOLD-003.pdf"))),
            new GroundTruth.GateStatuses("ELIGIBLE_FOR_REVIEW", "ELIGIBLE_FOR_REVIEW", "ELIGIBLE_FOR_REVIEW", "NOT_ESTABLISHED"),
            List.of(),
            List.of("VERIFY_DEBIT_NOTE_JUSTIFICATION", "RECONCILE_DISCREPANCY"),
            List.of("Buyer was entitled to deduct freight penalty without written approval", "Tax computation verified by system"),
            docsMeta
        );

        if (dir != null) {
            writeCase(dir, gt);
        }
        return new BenchmarkCaseData("holdout", "HOLD-003", gt, fileMap);
    }

    // HOLD-004: Medium Enterprise claiming MSMED Act protection (Gate A rejection)
    private static BenchmarkCaseData createHoldout4(@Nullable Path dir) throws Exception {
        Path docs = null;
        if (dir != null) {
            docs = dir.resolve("documents");
            Files.createDirectories(docs);
        }
        Map<String, byte[]> fileMap = new LinkedHashMap<>();

        String supplier = "Sterling Megawatt Turbines Ltd";
        String buyer = "National Thermal Infrastructure Ltd";
        long total = 125_000_000_00L;

        List<PdfRenderer.LineItem> items = List.of(
            new PdfRenderer.LineItem("Heavy Turbine Casing Assembly", "8406", 1, "SET", 100_000_000_00L, 100_000_000_00L)
        );

        fileMap.put("INV-HOLD-004.pdf", PdfRenderer.renderInvoiceLayout1(
            p(docs, "INV-HOLD-004.pdf"),
            supplier, "27AAACS9981K1Z2", "MIDC Butibori, Nagpur 441122",
            buyer, "07AAACN1928F1Z9", "Scope Complex, Lodhi Road, New Delhi 110003",
            "INV-HOLD-004", "2024-01-05", "PO-HOLD-004", items, 100_000_000_00L, 12_500_000_00L, 12_500_000_00L, total, "30 days net"
        ));

        List<GroundTruth.DocumentMeta> docsMeta = List.of(
            new GroundTruth.DocumentMeta("INVOICE", "INV-HOLD-004.pdf", "2024-01-05", total, "INV-HOLD-004", "EN")
        );

        GroundTruth gt = new GroundTruth(
            "HOLD-004",
            "Hand-written Holdout 4: Medium enterprise turnover exceeding Section 15 MSMED Act threshold",
            "GATE_ABSTENTION",
            new GroundTruth.SupplierMeta(supplier, "27AAACS9981K1Z2", "MEDIUM", true, "UDYAM-MH-20-0081294"),
            new GroundTruth.BuyerMeta(buyer, "07AAACN1928F1Z9"),
            new GroundTruth.FinancialGroundTruth(total, 100_000_000_00L, 25_000_000_00L, 0L, 0L, 0L, 0L, total, "UNRECONCILED"),
            List.of(),
            new GroundTruth.GateStatuses("OUT_OF_SCOPE", "ELIGIBLE_FOR_REVIEW", "ELIGIBLE_FOR_REVIEW", "NOT_ESTABLISHED"),
            List.of("micro_or_small_enterprise_status"),
            List.of("ABSTAIN_FROM_MSME_SAMADHAAN_FILING"),
            List.of("Medium enterprise is entitled to Section 16 interest penalty"),
            docsMeta
        );

        if (dir != null) {
            writeCase(dir, gt);
        }
        return new BenchmarkCaseData("holdout", "HOLD-004", gt, fileMap);
    }

    // HOLD-005: Defective batch in Hindi with credit note unapplied (C2 + C7)
    private static BenchmarkCaseData createHoldout5(@Nullable Path dir) throws Exception {
        Path docs = null;
        if (dir != null) {
            docs = dir.resolve("documents");
            Files.createDirectories(docs);
        }
        Map<String, byte[]> fileMap = new LinkedHashMap<>();

        String supplier = "Omkar Brass Components";
        String buyer = "Voltas Cooling Systems Ltd";
        long total = 236_000_00L;
        long creditVal = 50_000_00L;
        long balance = total - creditVal;

        List<PdfRenderer.LineItem> items = List.of(
            new PdfRenderer.LineItem("Precision Brass Ball Valves 1/2 inch", "8481", 1000, "PCS", 200_00L, 200_000_00L)
        );

        fileMap.put("INV-HOLD-005.pdf", PdfRenderer.renderInvoiceLayout1(
            p(docs, "INV-HOLD-005.pdf"),
            supplier, "27AAAPO7712M1Z0", "Udyog Nagar, Jamnagar 361004",
            buyer, "27AAACV9012D1Z6", "Thane Belapur Road, Navi Mumbai 400705",
            "INV-HOLD-005", "2024-01-18", "PO-HOLD-005", items, 200_000_00L, 18_000_00L, 18_000_00L, total, "30 days net"
        ));

        fileMap.put("CN-HOLD-005.pdf", PdfRenderer.renderCreditNote(
            p(docs, "CN-HOLD-005.pdf"),
            supplier, "27AAAPO7712M1Z0", buyer, "27AAACV9012D1Z6", "CN-HOLD-005", "2024-01-30",
            "INV-HOLD-005", "2024-01-18", creditVal, "Returned defective valves (250 pcs)"
        ));

        fileMap.put("MSG-HOLD-005.pdf", PdfRenderer.renderMessageTranscript(
            p(docs, "MSG-HOLD-005.pdf"),
            "WhatsApp", "2024-01-25 16:45 IST", "+91-9876543210 (Voltas QA)", "+91-9821199887 (Omkar Brass)",
            "दोषपूर्ण माल अस्वीकृति सूचना",
            "श्रीमान जी,\nआपके द्वारा भेजे गए 1000 वाल्वों में से 250 पीस में लीकेज की समस्या पाई गई है।\nहमने 250 वाल्व वापस भेज दिए हैं और 50,000 रुपये का क्रेडिट नोट जारी करने का अनुरोध किया है। शेष भुगतान प्रक्रिया में है।",
            "HI"
        ));

        List<GroundTruth.DocumentMeta> docsMeta = List.of(
            new GroundTruth.DocumentMeta("INVOICE", "INV-HOLD-005.pdf", "2024-01-18", total, "INV-HOLD-005", "EN"),
            new GroundTruth.DocumentMeta("CREDIT_NOTE", "CN-HOLD-005.pdf", "2024-01-30", creditVal, "CN-HOLD-005", "EN"),
            new GroundTruth.DocumentMeta("MESSAGE", "MSG-HOLD-005.pdf", "2024-01-25", null, null, "HI")
        );

        GroundTruth gt = new GroundTruth(
            "HOLD-005",
            "Hand-written Holdout 5: Defective batch rejected in Hindi with unapplied credit note",
            "CONTRACT_DELIVERY_CONFLICT",
            new GroundTruth.SupplierMeta(supplier, "27AAAPO7712M1Z0", "MICRO", true, "UDYAM-GJ-08-0012984"),
            new GroundTruth.BuyerMeta(buyer, "27AAACV9012D1Z6"),
            new GroundTruth.FinancialGroundTruth(total, 200_000_00L, 36_000_00L, 0L, creditVal, 0L, 0L, balance, "PARTIAL"),
            List.of(
                new GroundTruth.ExpectedConflict("C2", "Credit note unapplied", "Credit note CN-HOLD-005 for Rs. 50,000 unapplied against invoice total", List.of("INV-HOLD-005.pdf", "CN-HOLD-005.pdf")),
                new GroundTruth.ExpectedConflict("C7", "Quantity/quality dispute", "Hindi WhatsApp message records 250 defective leaking valves rejected and returned", List.of("MSG-HOLD-005.pdf"))
            ),
            new GroundTruth.GateStatuses("ELIGIBLE_FOR_REVIEW", "ELIGIBLE_FOR_REVIEW", "ELIGIBLE_FOR_REVIEW", "NOT_ESTABLISHED"),
            List.of(),
            List.of("APPLY_CREDIT_NOTE_TO_LEDGER", "DISPATCH_REPLACEMENT_VALVES"),
            List.of("Entire invoice is void due to defect", "Interest runs on original gross total"),
            docsMeta
        );

        if (dir != null) {
            writeCase(dir, gt);
        }
        return new BenchmarkCaseData("holdout", "HOLD-005", gt, fileMap);
    }

    // HOLD-006: Conflicting payment terms (PO 90 days vs Invoice 30 days vs Statutory 45 days)
    private static BenchmarkCaseData createHoldout6(@Nullable Path dir) throws Exception {
        Path docs = null;
        if (dir != null) {
            docs = dir.resolve("documents");
            Files.createDirectories(docs);
        }
        Map<String, byte[]> fileMap = new LinkedHashMap<>();

        String supplier = "Synergy Sheet Metal Fabricators";
        String buyer = "Thermax Boilers Ltd";
        long total = 300_000_00L;

        List<PdfRenderer.LineItem> items = List.of(
            new PdfRenderer.LineItem("Heavy Boiler Chimney Ducting", "7308", 4, "UNITS", 63_559_32L, 254_237_28L)
        );

        fileMap.put("INV-HOLD-006.pdf", PdfRenderer.renderInvoiceLayout1(
            p(docs, "INV-HOLD-006.pdf"),
            supplier, "27AAPFS8821N1ZM", "MIDC Pimpri, Pune 411018",
            buyer, "27AAACT1029R1Z1", "MIDC Chinchwad, Pune 411019",
            "INV-HOLD-006", "2024-01-10", "PO-HOLD-006", items, 254_237_28L, 22_881_36L, 22_881_36L, total, "30 days net"
        ));

        fileMap.put("PO-HOLD-006.pdf", PdfRenderer.renderPurchaseOrder(
            p(docs, "PO-HOLD-006.pdf"),
            buyer, "27AAACT1029R1Z1", supplier, "PO-HOLD-006", "2023-12-20", "2024-01-15",
            "Payment strictly within 90 days from inspection", total, "Heavy Boiler Ducting"
        ));

        List<GroundTruth.DocumentMeta> docsMeta = List.of(
            new GroundTruth.DocumentMeta("INVOICE", "INV-HOLD-006.pdf", "2024-01-10", total, "INV-HOLD-006", "EN"),
            new GroundTruth.DocumentMeta("PO", "PO-HOLD-006.pdf", "2023-12-20", total, "PO-HOLD-006", "EN")
        );

        GroundTruth gt = new GroundTruth(
            "HOLD-006",
            "Hand-written Holdout 6: Conflicting terms across PO (90d), Invoice (30d), and statutory 45d cap",
            "CONTRACT_DELIVERY_CONFLICT",
            new GroundTruth.SupplierMeta(supplier, "27AAPFS8821N1ZM", "SMALL", true, "UDYAM-MH-12-0033192"),
            new GroundTruth.BuyerMeta(buyer, "27AAACT1029R1Z1"),
            new GroundTruth.FinancialGroundTruth(total, 254_237_28L, 45_762_72L, 0L, 0L, 0L, 0L, total, "UNRECONCILED"),
            List.of(new GroundTruth.ExpectedConflict("C4", "Terms conflict", "PO specifies 90 days (violating Section 15 45-day cap), invoice specifies 30 days", List.of("PO-HOLD-006.pdf", "INV-HOLD-006.pdf"))),
            new GroundTruth.GateStatuses("ELIGIBLE_FOR_REVIEW", "ELIGIBLE_FOR_REVIEW", "HUMAN_REVIEW_REQUIRED", "NOT_ESTABLISHED"),
            List.of(),
            List.of("PRESENT_TERMS_SIDE_BY_SIDE", "APPLY_STATUTORY_45_DAY_CAP"),
            List.of("90-day term is legal and binding", "System has determined 30 days as true term"),
            docsMeta
        );

        if (dir != null) {
            writeCase(dir, gt);
        }
        return new BenchmarkCaseData("holdout", "HOLD-006", gt, fileMap);
    }

    // HOLD-007: Missing delivery proof + unconfirmed bank rate (Gate B & Gate D)
    private static BenchmarkCaseData createHoldout7(@Nullable Path dir) throws Exception {
        Path docs = null;
        if (dir != null) {
            docs = dir.resolve("documents");
            Files.createDirectories(docs);
        }
        Map<String, byte[]> fileMap = new LinkedHashMap<>();

        String supplier = "Premier Hydraulic Seals Pvt Ltd";
        String buyer = "JCB Earthmovers India Ltd";
        long total = 150_000_00L;

        fileMap.put("INV-HOLD-007.pdf", PdfRenderer.renderInvoiceLayout3(
            p(docs, "INV-HOLD-007.pdf"),
            supplier, "27AAPFP8812A1Z9", buyer, "06AAACJ9910F1Z4",
            "INV-HOLD-007", "2024-02-01", "PO-HOLD-007", "Hydraulic Cylinder Seal Kits Heavy Duty", "8484",
            127_118_64L, 22_881_36L, total, "30 days net"
        ));

        List<GroundTruth.DocumentMeta> docsMeta = List.of(
            new GroundTruth.DocumentMeta("INVOICE", "INV-HOLD-007.pdf", "2024-02-01", total, "INV-HOLD-007", "EN")
        );

        GroundTruth gt = new GroundTruth(
            "HOLD-007",
            "Hand-written Holdout 7: Missing proof of delivery and unconfirmed bank rate",
            "GATE_ABSTENTION",
            new GroundTruth.SupplierMeta(supplier, "27AAPFP8812A1Z9", "MICRO", true, "UDYAM-MH-12-0071290"),
            new GroundTruth.BuyerMeta(buyer, "06AAACJ9910F1Z4"),
            new GroundTruth.FinancialGroundTruth(total, 127_118_64L, 22_881_36L, 0L, 0L, 0L, 0L, total, "UNRECONCILED"),
            List.of(new GroundTruth.ExpectedConflict("C5", "Delivery evidence missing", "Invoice present without signed delivery challan or lorry receipt", List.of("INV-HOLD-007.pdf"))),
            new GroundTruth.GateStatuses("ELIGIBLE_FOR_REVIEW", "NOT_ESTABLISHED", "ELIGIBLE_FOR_REVIEW", "NOT_ESTABLISHED"),
            List.of("delivery_proof", "confirmed_bank_rate"),
            List.of("REQUEST_PROOF_OF_DELIVERY"),
            List.of("Delivery is deemed accepted without evidence", "Interest is chargeable immediately"),
            docsMeta
        );

        if (dir != null) {
            writeCase(dir, gt);
        }
        return new BenchmarkCaseData("holdout", "HOLD-007", gt, fileMap);
    }

    // HOLD-008: Duplicate payment UTR transaction across separate invoices (C6)
    private static BenchmarkCaseData createHoldout8(@Nullable Path dir) throws Exception {
        Path docs = null;
        if (dir != null) {
            docs = dir.resolve("documents");
            Files.createDirectories(docs);
        }
        Map<String, byte[]> fileMap = new LinkedHashMap<>();

        String supplier = "Nandi Precision Castings";
        String buyer = "BEML Heavy Haulers Ltd";
        long total = 180_000_00L;
        String utr = "UTRN24011889901";

        List<PdfRenderer.LineItem> items = List.of(
            new PdfRenderer.LineItem("Spheroidal Graphite Iron Castings", "7325", 50, "PCS", 3050_85L, 152_542_37L)
        );

        fileMap.put("INV-HOLD-008.pdf", PdfRenderer.renderInvoiceLayout1(
            p(docs, "INV-HOLD-008.pdf"),
            supplier, "29AAPFN9910D1Z7", "Belagavi Industrial Estate, Karnataka 590014",
            buyer, "29AAACB1029K1Z5", "BEML Soudha, SR Nagar, Bengaluru 560027",
            "INV-HOLD-008", "2024-01-10", "PO-HOLD-008", items, 152_542_37L, 13_728_81L, 13_728_81L, total, "30 days net"
        ));

        fileMap.put("PAY-1-HOLD-008.pdf", PdfRenderer.renderPaymentAdvice(
            p(docs, "PAY-1-HOLD-008.pdf"),
            "Canara Bank", buyer, supplier, utr, "2024-01-20", total, 0, total, "Payment voucher A", List.of("INV-HOLD-008")
        ));

        fileMap.put("PAY-2-HOLD-008.pdf", PdfRenderer.renderPaymentAdvice(
            p(docs, "PAY-2-HOLD-008.pdf"),
            "Canara Bank", buyer, supplier, utr, "2024-01-20", total, 0, total, "Payment voucher B", List.of("INV-HOLD-008")
        ));

        List<GroundTruth.DocumentMeta> docsMeta = List.of(
            new GroundTruth.DocumentMeta("INVOICE", "INV-HOLD-008.pdf", "2024-01-10", total, "INV-HOLD-008", "EN"),
            new GroundTruth.DocumentMeta("PAYMENT", "PAY-1-HOLD-008.pdf", "2024-01-20", total, utr, "EN"),
            new GroundTruth.DocumentMeta("PAYMENT", "PAY-2-HOLD-008.pdf", "2024-01-20", total, utr, "EN")
        );

        GroundTruth gt = new GroundTruth(
            "HOLD-008",
            "Hand-written Holdout 8: Duplicate payment transaction ID cited across multiple vouchers",
            "FINANCIAL_CONFLICT",
            new GroundTruth.SupplierMeta(supplier, "29AAPFN9910D1Z7", "SMALL", true, "UDYAM-KR-04-0018274"),
            new GroundTruth.BuyerMeta(buyer, "29AAACB1029K1Z5"),
            new GroundTruth.FinancialGroundTruth(total, 152_542_37L, 27_457_63L, total, 0L, 0L, 0L, 0L, "RECONCILED"),
            List.of(new GroundTruth.ExpectedConflict("C6", "Duplicate suspect", "Same bank transaction UTR UTRN24011889901 cited in multiple payment vouchers", List.of("PAY-1-HOLD-008.pdf", "PAY-2-HOLD-008.pdf"))),
            new GroundTruth.GateStatuses("ELIGIBLE_FOR_REVIEW", "ELIGIBLE_FOR_REVIEW", "ELIGIBLE_FOR_REVIEW", "NOT_ESTABLISHED"),
            List.of(),
            List.of("FLAG_DUPLICATE_PAYMENT_ENTRY", "REQUEST_BANK_RECONCILIATION_STATEMENT"),
            List.of("Second payment voucher represents a separate independent transfer"),
            docsMeta
        );

        if (dir != null) {
            writeCase(dir, gt);
        }
        return new BenchmarkCaseData("holdout", "HOLD-008", gt, fileMap);
    }
}
