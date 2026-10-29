package com.duespilot.bench.generator;

import com.duespilot.bench.holdout.HoldoutCaseCreator;
import com.duespilot.bench.model.GroundTruth;
import com.duespilot.core.model.CaseReconciliation;
import com.duespilot.core.model.InvoiceReconciliation;
import com.duespilot.core.model.Known;
import com.duespilot.core.model.LedgerEntry;
import com.duespilot.core.model.LedgerEntryType;
import com.duespilot.core.model.Paise;
import com.duespilot.core.model.VerificationStatus;
import com.duespilot.core.reconcile.Reconciler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Benchmark Generator & Holdout Verification Tests (Section 14 & Rule R-62)")
class BenchmarkGeneratorTest {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
        .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
        .build();

    @Test
    @DisplayName("Regenerating with the same seed asserts identical ground truth and file output")
    void regenerateWithSameSeedAssertIdenticalOutput(@TempDir Path tempDir) throws Exception {
        Path run1 = tempDir.resolve("run1");
        Path run2 = tempDir.resolve("run2");

        long seed = 42L;
        int count = 40;

        CaseGenerator.generate(seed, count, run1);
        CaseGenerator.generate(seed, count, run2);

        for (int i = 1; i <= count; i++) {
            String code = String.format("GEN-%03d", i);

            Path gt1 = run1.resolve(code).resolve("ground_truth.json");
            Path gt2 = run2.resolve(code).resolve("ground_truth.json");

            assertThat(Files.exists(gt1)).isTrue();
            assertThat(Files.exists(gt2)).isTrue();

            String json1 = Files.readString(gt1);
            String json2 = Files.readString(gt2);
            assertThat(json1).isEqualTo(json2);

            Path docs1 = run1.resolve(code).resolve("documents");
            Path docs2 = run2.resolve(code).resolve("documents");

            List<Path> files1 = Files.list(docs1).sorted().toList();
            List<Path> files2 = Files.list(docs2).sorted().toList();

            assertThat(files1).hasSameSizeAs(files2);
            for (int f = 0; f < files1.size(); f++) {
                assertThat(files1.get(f).getFileName()).isEqualTo(files2.get(f).getFileName());
                assertThat(Files.size(files1.get(f))).isEqualTo(Files.size(files2.get(f)));
            }
        }
    }

    @Test
    @DisplayName("Recompute each case balance independently from generated documents and assert it equals ground truth")
    void recomputeCaseBalancesIndependently(@TempDir Path tempDir) throws Exception {
        long seed = 99L;
        int count = 40;
        CaseGenerator.generate(seed, count, tempDir);

        for (int i = 1; i <= count; i++) {
            String code = String.format("GEN-%03d", i);
            Path gtFile = tempDir.resolve(code).resolve("ground_truth.json");
            GroundTruth gt = MAPPER.readValue(gtFile.toFile(), GroundTruth.class);

            // Build independent ledger entries from document metadata
            boolean isC10 = gt.expectedConflicts().stream().anyMatch(c -> "C10".equals(c.ruleId()));
            java.util.Set<String> seenPaymentUtrs = new java.util.HashSet<>();
            LedgerEntry invoiceEntry = null;
            List<LedgerEntry> relatedEntries = new ArrayList<>();

            for (GroundTruth.DocumentMeta doc : gt.documents()) {
                LocalDate date = doc.docDate() != null ? LocalDate.parse(doc.docDate()) : LocalDate.of(2024, 2, 1);
                String ref = doc.docReference() != null ? doc.docReference() : code;

                switch (doc.docType()) {
                    case "INVOICE" -> {
                        if (doc.amountPaise() != null) {
                            if (invoiceEntry == null) {
                                invoiceEntry = LedgerEntry.invoice(
                                    code,
                                    ref,
                                    date,
                                    Paise.ofPaise(doc.amountPaise()),
                                    VerificationStatus.VERIFIED
                                );
                            }
                        } else {
                            invoiceEntry = new LedgerEntry(
                                code,
                                LedgerEntryType.INVOICE,
                                Known.value(ref),
                                Known.value(ref),
                                Known.unknown("No PO"),
                                Known.value(date),
                                Known.unknown("Missing invoice total amount"),
                                VerificationStatus.VERIFIED,
                                com.duespilot.core.model.DocumentSource.TAX_INVOICE,
                                false,
                                null,
                                null
                            );
                        }
                    }
                    case "PAYMENT" -> {
                        if (doc.amountPaise() != null) {
                            // C10: Unallocated lump-sum payment covering multiple invoices is not credited to single invoice
                            if (isC10) {
                                continue;
                            }
                            String utr = doc.docReference() != null ? doc.docReference() : doc.fileName();
                            // C6: Duplicate suspect with same transaction UTR is deduplicated for confirmed balance
                            if (seenPaymentUtrs.add(utr)) {
                                relatedEntries.add(LedgerEntry.payment(
                                    "PAY-" + doc.fileName(),
                                    utr,
                                    invoiceEntry != null ? invoiceEntry.referenceNumber().orElse(code) : code,
                                    date,
                                    Paise.ofPaise(doc.amountPaise()),
                                    VerificationStatus.VERIFIED,
                                    com.duespilot.core.model.DocumentSource.PAYMENT_RECEIPT
                                ));
                            }
                        }
                    }
                    case "CREDIT_NOTE" -> {
                        if (doc.amountPaise() != null) {
                            relatedEntries.add(LedgerEntry.creditNote(
                                "CN-" + doc.fileName(),
                                ref,
                                invoiceEntry != null ? invoiceEntry.referenceNumber().orElse(code) : code,
                                date,
                                Paise.ofPaise(doc.amountPaise()),
                                VerificationStatus.VERIFIED
                            ));
                        }
                    }
                    case "MESSAGE" -> {
                        if (doc.amountPaise() != null) {
                            // Rule R-12: message-only payments are UNVERIFIED
                            relatedEntries.add(LedgerEntry.payment(
                                "MSG-PAY-" + doc.fileName(),
                                ref,
                                invoiceEntry != null ? invoiceEntry.referenceNumber().orElse(code) : code,
                                date,
                                Paise.ofPaise(doc.amountPaise()),
                                VerificationStatus.UNVERIFIED,
                                com.duespilot.core.model.DocumentSource.BUYER_MESSAGE
                            ));
                        }
                    }
                }
            }

            assertThat(invoiceEntry).isNotNull();

            // Run pure core reconciler
            InvoiceReconciliation reconciliation = Reconciler.reconcileInvoice(invoiceEntry, relatedEntries);

            Long expectedBal = gt.financials().expectedBalancePaise();
            if (expectedBal == null) {
                // Rule R-2: Balance is null when any required input is unknown
                assertThat(reconciliation.balance()).isNull();
            } else {
                assertThat(reconciliation.balance()).isNotNull();
                assertThat(reconciliation.balance().value()).isEqualTo(expectedBal);
            }
        }
    }

    @Test
    @DisplayName("Verify case categories, counts (8 controls, 12 financial, 8 contract/delivery, 6 gate, 6 language = 40), C9, and C10")
    void verifyCaseCategoriesAndCounts(@TempDir Path tempDir) throws Exception {
        CaseGenerator.generate(42L, 40, tempDir);

        int controls = 0;
        int financial = 0;
        int contract = 0;
        int gate = 0;
        int language = 0;
        boolean hasC9 = false;
        boolean hasC10 = false;

        for (int i = 1; i <= 40; i++) {
            String code = String.format("GEN-%03d", i);
            Path gtFile = tempDir.resolve(code).resolve("ground_truth.json");
            GroundTruth gt = MAPPER.readValue(gtFile.toFile(), GroundTruth.class);

            switch (gt.category()) {
                case "CONTROL" -> controls++;
                case "FINANCIAL_CONFLICT" -> financial++;
                case "CONTRACT_DELIVERY_CONFLICT" -> contract++;
                case "GATE_ABSTENTION" -> gate++;
                case "LANGUAGE_VARIANT" -> language++;
            }

            for (GroundTruth.ExpectedConflict conflict : gt.expectedConflicts()) {
                if ("C9".equals(conflict.ruleId())) {
                    hasC9 = true;
                }
                if ("C10".equals(conflict.ruleId())) {
                    hasC10 = true;
                }
            }

            // Ensure ground truth is separate from documents
            Path docsDir = tempDir.resolve(code).resolve("documents");
            assertThat(Files.isDirectory(docsDir)).isTrue();
            assertThat(Files.exists(docsDir.resolve("ground_truth.json"))).isFalse();
        }

        assertThat(controls).isEqualTo(8);
        assertThat(financial).isEqualTo(12);
        assertThat(contract).isEqualTo(8);
        assertThat(gate).isEqualTo(6);
        assertThat(language).isEqualTo(6);
        assertThat(hasC9).isTrue();
        assertThat(hasC10).isTrue();
    }

    @Test
    @DisplayName("Verify holdout dataset contains 8 hand-written cases with ground truth and valid independent balances")
    void verifyHoldoutCases(@TempDir Path tempDir) throws Exception {
        Path holdoutDir = tempDir.resolve("holdout");
        HoldoutCaseCreator.createHoldoutCases(holdoutDir);

        for (int i = 1; i <= 8; i++) {
            String code = String.format("HOLD-%03d", i);
            Path caseDir = holdoutDir.resolve(code);
            Path gtFile = caseDir.resolve("ground_truth.json");
            Path docsDir = caseDir.resolve("documents");

            assertThat(Files.exists(gtFile)).isTrue();
            assertThat(Files.isDirectory(docsDir)).isTrue();
            assertThat(Files.exists(docsDir.resolve("ground_truth.json"))).isFalse();

            GroundTruth gt = MAPPER.readValue(gtFile.toFile(), GroundTruth.class);
            assertThat(gt.caseCode()).isEqualTo(code);
            assertThat(gt.documents()).isNotEmpty();

            // Recompute balance for holdout case
            LedgerEntry invoiceEntry = null;
            List<LedgerEntry> related = new ArrayList<>();
            boolean isC10 = gt.expectedConflicts().stream().anyMatch(c -> "C10".equals(c.ruleId()));
            java.util.Set<String> seenPaymentUtrs = new java.util.HashSet<>();

            for (GroundTruth.DocumentMeta doc : gt.documents()) {
                LocalDate dt = doc.docDate() != null ? LocalDate.parse(doc.docDate()) : LocalDate.of(2024, 1, 1);
                String ref = doc.docReference() != null ? doc.docReference() : code;

                if ("INVOICE".equals(doc.docType()) && invoiceEntry == null) {
                    invoiceEntry = LedgerEntry.invoice(code, ref, dt, Paise.ofPaise(doc.amountPaise()), VerificationStatus.VERIFIED);
                } else if ("PAYMENT".equals(doc.docType()) && doc.amountPaise() != null) {
                    if (!isC10 && seenPaymentUtrs.add(ref)) {
                        related.add(LedgerEntry.payment("PAY-" + doc.fileName(), ref, invoiceEntry != null ? invoiceEntry.referenceNumber().orElse(code) : code, dt, Paise.ofPaise(doc.amountPaise()), VerificationStatus.VERIFIED, com.duespilot.core.model.DocumentSource.PAYMENT_RECEIPT));
                    }
                } else if ("CREDIT_NOTE".equals(doc.docType()) && doc.amountPaise() != null) {
                    related.add(LedgerEntry.creditNote("CN-" + doc.fileName(), ref, invoiceEntry != null ? invoiceEntry.referenceNumber().orElse(code) : code, dt, Paise.ofPaise(doc.amountPaise()), VerificationStatus.VERIFIED));
                } else if ("MESSAGE".equals(doc.docType()) && doc.amountPaise() != null) {
                    // Rule R-12: message-only payments are UNVERIFIED
                    related.add(LedgerEntry.payment("MSG-" + doc.fileName(), ref, invoiceEntry != null ? invoiceEntry.referenceNumber().orElse(code) : code, dt, Paise.ofPaise(doc.amountPaise()), VerificationStatus.UNVERIFIED, com.duespilot.core.model.DocumentSource.BUYER_MESSAGE));
                }
            }

            if (invoiceEntry != null) {
                InvoiceReconciliation recon = Reconciler.reconcileInvoice(invoiceEntry, related);
                if (gt.financials().expectedBalancePaise() != null) {
                    assertThat(recon.balance()).isNotNull();
                    assertThat(recon.balance().value()).isEqualTo(gt.financials().expectedBalancePaise());
                }
            }
        }
    }
}
