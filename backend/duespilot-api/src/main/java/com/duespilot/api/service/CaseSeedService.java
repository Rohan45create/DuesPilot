package com.duespilot.api.service;

import com.duespilot.api.domain.AnalysisRunEntity;
import com.duespilot.api.domain.CaseEntity;
import com.duespilot.api.domain.ConflictEntity;
import com.duespilot.api.domain.ContactEntity;
import com.duespilot.api.domain.DocType;
import com.duespilot.api.domain.DocumentEntity;
import com.duespilot.api.domain.DocumentPageEntity;
import com.duespilot.api.domain.EligibilityAssessmentEntity;
import com.duespilot.api.domain.ExtractedFieldEntity;
import com.duespilot.api.domain.InterestEstimateEntity;
import com.duespilot.api.domain.LedgerEntryEntity;
import com.duespilot.api.domain.LlmCallEntity;
import com.duespilot.api.domain.ProposedActionEntity;
import com.duespilot.api.domain.RateReferenceEntity;
import com.duespilot.api.domain.ReconciliationResultEntity;
import com.duespilot.api.domain.SupplierCategory;
import com.duespilot.api.repository.AnalysisRunRepository;
import com.duespilot.api.repository.CaseRepository;
import com.duespilot.api.repository.ConflictRepository;
import com.duespilot.api.repository.DocumentPageRepository;
import com.duespilot.api.repository.DocumentRepository;
import com.duespilot.api.repository.EligibilityAssessmentRepository;
import com.duespilot.api.repository.ExtractedFieldRepository;
import com.duespilot.api.repository.FileBlobRepository;
import com.duespilot.api.repository.InterestEstimateRepository;
import com.duespilot.api.repository.LedgerEntryRepository;
import com.duespilot.api.repository.LlmCallRepository;
import com.duespilot.api.repository.ProposedActionRepository;
import com.duespilot.api.repository.RateReferenceRepository;
import com.duespilot.api.repository.ReconciliationResultRepository;
import com.duespilot.api.repository.SeedTemplateRepository;
import com.duespilot.core.extract.FieldStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Seeds 12 realistic benchmark cases with pre-recorded analyses per Architecture sections 11, 15, 16.
 * Guaranteed to include:
 * - Case 1 (GEN-001): All gates pass (Gates A, B, C, D pass, confirmed bank rate, provisional interest computed).
 * - Case 2 (GEN-029): Refuse to compute (Medium enterprise -> Gate A OUT_OF_SCOPE, interest refused).
 * - Plus 10 other realistic cases covering statutory & contract conflicts C1-C10.
 */
@Service
public class CaseSeedService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CaseSeedService.class);

    private final CaseRepository caseRepository;
    private final DocumentRepository documentRepository;
    private final DocumentPageRepository documentPageRepository;
    private final ExtractedFieldRepository extractedFieldRepository;
    private final AnalysisRunRepository analysisRunRepository;
    private final ReconciliationResultRepository reconciliationResultRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final ConflictRepository conflictRepository;
    private final EligibilityAssessmentRepository eligibilityAssessmentRepository;
    private final RateReferenceRepository rateReferenceRepository;
    private final InterestEstimateRepository interestEstimateRepository;
    private final ProposedActionRepository proposedActionRepository;
    private final LlmCallRepository llmCallRepository;
    private final FileBlobRepository fileBlobRepository;
    private final SeedTemplateRepository seedTemplateRepository;
    private final com.duespilot.api.repository.ContactRepository contactRepository;

    public CaseSeedService(
            CaseRepository caseRepository,
            DocumentRepository documentRepository,
            DocumentPageRepository documentPageRepository,
            ExtractedFieldRepository extractedFieldRepository,
            AnalysisRunRepository analysisRunRepository,
            ReconciliationResultRepository reconciliationResultRepository,
            LedgerEntryRepository ledgerEntryRepository,
            ConflictRepository conflictRepository,
            EligibilityAssessmentRepository eligibilityAssessmentRepository,
            RateReferenceRepository rateReferenceRepository,
            InterestEstimateRepository interestEstimateRepository,
            ProposedActionRepository proposedActionRepository,
            LlmCallRepository llmCallRepository,
            FileBlobRepository fileBlobRepository,
            SeedTemplateRepository seedTemplateRepository,
            com.duespilot.api.repository.ContactRepository contactRepository) {
        this.caseRepository = caseRepository;
        this.documentRepository = documentRepository;
        this.documentPageRepository = documentPageRepository;
        this.extractedFieldRepository = extractedFieldRepository;
        this.analysisRunRepository = analysisRunRepository;
        this.reconciliationResultRepository = reconciliationResultRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.conflictRepository = conflictRepository;
        this.eligibilityAssessmentRepository = eligibilityAssessmentRepository;
        this.rateReferenceRepository = rateReferenceRepository;
        this.interestEstimateRepository = interestEstimateRepository;
        this.proposedActionRepository = proposedActionRepository;
        this.llmCallRepository = llmCallRepository;
        this.fileBlobRepository = fileBlobRepository;
        this.seedTemplateRepository = seedTemplateRepository;
        this.contactRepository = contactRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            seedAll();
        } catch (Exception e) {
            log.error("Failed to seed initial cases", e);
        }
    }

    @Transactional
    public void seedAll() {
        List<CaseEntity> existingSeeds = caseRepository.findSeedCases();
        if (existingSeeds.size() >= 12 && seedTemplateRepository.countTemplates() >= 12) {
            log.info("Database already contains {} seed cases and {} templates; skipping seed runner",
                existingSeeds.size(), seedTemplateRepository.countTemplates());
            return;
        }

        log.info("Seeding 12 benchmark cases with pre-recorded analyses...");

        // 0. Seed confirmed bank rate reference if none exists
        RateReferenceEntity confirmedRate = rateReferenceRepository.findLatestConfirmed().orElseGet(() -> {
            RateReferenceEntity rate = new RateReferenceEntity(
                UUID.randomUUID(),
                "https://www.rbi.org.in/scripts/BS_PressReleaseDisplay.aspx",
                "The Reserve Bank of India has decided to keep the policy Repo Rate unchanged at 6.50% and the Bank Rate at 6.75%.",
                675,
                LocalDate.of(2024, 1, 1),
                Instant.now(),
                Instant.now(),
                "compliance_officer"
            );
            return rateReferenceRepository.save(rate);
        });

        // 1. GEN-001: All Gates Pass (Control Case 1)
        seedCase(
            "GEN-001",
            "Control Case 1: Clean invoice & delivery (All Gates Pass)",
            "Precision Engineering Works Pvt Ltd",
            "Apex Industrial Machinery Ltd",
            SupplierCategory.MICRO,
            LocalDate.of(2021, 6, 1),
            "INVOICE", "INV-2024-GEN-001.pdf", "TAX INVOICE\nInv: INV-2024-GEN-001\nDate: 2024-02-01\nTotal: ₹ 1,47,500.00",
            14_750_000L, LocalDate.of(2024, 2, 1),
            "DELIVERY", "DC-2024-GEN-001.pdf", "DELIVERY CHALLAN\nDC-2024-GEN-001\nDelivered in good condition on 2024-02-03",
            "ELIGIBLE_FOR_REVIEW", "ELIGIBLE_FOR_REVIEW", "ELIGIBLE_FOR_REVIEW", "ELIGIBLE_FOR_REVIEW",
            null, null,
            confirmedRate, 14_750_000L, 249_000L,
            "ARCHIVE_SETTLED_CASE",
            "The receivable reconciles with confirmed delivery and verified rate. All statutory gates pass.",
            "सभी वैधानिक शर्तें पूरी होती हैं और डिलीवरी की पुष्टि हो चुकी है। मामला सुलझा हुआ है।"
        );

        // 2. GEN-029: Refuse to compute (Medium Enterprise -> Gate A OUT_OF_SCOPE)
        seedCase(
            "GEN-029",
            "Gate Case 29: Medium Enterprise (Refuse to compute)",
            "Mega Corp Heavy Industries Ltd",
            "Tata AutoComp Systems Ltd",
            SupplierCategory.MEDIUM,
            LocalDate.of(2020, 1, 15),
            "INVOICE", "INV-2024-GEN-029.pdf", "TAX INVOICE\nInv: INV-2024-GEN-029\nDate: 2024-01-20\nTotal: ₹ 3,54,000.00",
            35_400_000L, LocalDate.of(2024, 1, 20),
            null, null, null,
            "OUT_OF_SCOPE", "ELIGIBLE_FOR_REVIEW", "ELIGIBLE_FOR_REVIEW", "OUT_OF_SCOPE",
            null, null,
            null, 0L, 0L,
            "REQUEST_STATUTORY_EVIDENCE",
            "Supplier is registered as a MEDIUM enterprise; delayed payment remedies under Chapter V of MSMED Act apply only to Micro and Small enterprises. Interest computation is refused.",
            "आपूर्तिकर्ता एक मध्यम उद्यम के रूप में पंजीकृत है; एमएसएमईडी अधिनियम के अध्याय 5 के तहत विलंबित भुगतान के उपाय केवल सूक्ष्म और लघु उद्यमों पर लागू होते हैं। ब्याज की गणना अस्वीकार कर दी गई है।"
        );

        // 3. GEN-030: Refuse to compute (Missing Udyam Registration -> Gate A NOT_ESTABLISHED)
        seedCase(
            "GEN-030",
            "Gate Case 30: Missing Udyam Registration (Refuse to compute)",
            "Shree Ganesh Precision Dies",
            "Tata AutoComp Systems Ltd",
            SupplierCategory.MICRO,
            null,
            "INVOICE", "INV-2024-GEN-030.pdf", "TAX INVOICE\nInv: INV-2024-GEN-030\nDate: 2024-01-20\nTotal: ₹ 3,54,000.00",
            35_400_000L, LocalDate.of(2024, 1, 20),
            null, null, null,
            "NOT_ESTABLISHED", "ELIGIBLE_FOR_REVIEW", "ELIGIBLE_FOR_REVIEW", "NOT_ESTABLISHED",
            null, null,
            null, 0L, 0L,
            "REQUEST_STATUTORY_EVIDENCE",
            "Udyam registration document is missing or unverified. Request a copy of the valid Udyam certificate to establish statutory eligibility.",
            "उद्यम पंजीकरण दस्तावेज़ अनुपलब्ध या असत्यापित है। वैधानिक पात्रता स्थापित करने के लिए वैध उद्यम प्रमाणपत्र की एक प्रति का अनुरोध करें।"
        );

        // 4. GEN-021: Missing Delivery Challan (Conflict C5)
        seedCase(
            "GEN-021",
            "Contract Conflict 1: Missing Delivery Proof (C5)",
            "Vardhman Industries",
            "Bajaj Auto Ltd",
            SupplierCategory.MICRO,
            LocalDate.of(2021, 5, 1),
            "INVOICE", "INV-2024-GEN-021.pdf", "TAX INVOICE\nInv: INV-2024-GEN-021\nDate: 2024-01-15\nTotal: ₹ 1,18,000.00",
            11_800_000L, LocalDate.of(2024, 1, 15),
            null, null, null,
            "ELIGIBLE_FOR_REVIEW", "NOT_ESTABLISHED", "ELIGIBLE_FOR_REVIEW", "NOT_ESTABLISHED",
            "C5", "Invoice raised without delivery or acceptance proof",
            null, 0L, 0L,
            "REQUEST_DELIVERY_PROOF",
            "Delivery or acceptance documentation is not confirmed. Request proof of delivery (signed delivery challan) from the buyer to substantiate receipt.",
            "वितरण या स्वीकृति दस्तावेज़ की पुष्टि नहीं हुई है। माल प्राप्ति की पुष्टि के लिए खरीदार से डिलीवरी का प्रमाण (हस्ताक्षरित चालान) का अनुरोध करें।"
        );

        // 5. GEN-009: TDS Short-Payment Deduction (Conflict C9)
        seedCase(
            "GEN-009",
            "Financial Conflict 9: TDS Deduction (C9)",
            "Shree Krishna Precision",
            "Mahindra Ltd",
            SupplierCategory.MICRO,
            LocalDate.of(2022, 1, 1),
            "INVOICE", "INV-2024-GEN-009.pdf", "TAX INVOICE\nInv: INV-2024-GEN-009\nDate: 2024-01-15\nTotal: ₹ 2,36,000.00",
            23_600_000L, LocalDate.of(2024, 1, 15),
            "PAYMENT", "PAY-GEN-009.pdf", "PAYMENT ADVICE\nUTR: UTR998877\nPaid: ₹ 2,12,400.00\nDeduction TDS Section 194C: ₹ 23,600.00",
            "ELIGIBLE_FOR_REVIEW", "ELIGIBLE_FOR_REVIEW", "ELIGIBLE_FOR_REVIEW", "HUMAN_REVIEW_REQUIRED",
            "C9", "Remittance advice specifies deduction of ₹236.00 for Section 194C TDS",
            null, 0L, 0L,
            "REQUEST_CLARIFICATION_ON_DEDUCTION",
            "A deduction was itemised in the remittance advice against the invoice. Request itemised TDS certificate (Form 16A) from the buyer.",
            "भुगतान प्रेषण सूचना में चालान राशि से टीडीएस कटौती की गई है। खरीदार से फॉर्म 16ए टीडीएस प्रमाणपत्र का अनुरोध करें।"
        );

        // 6. GEN-002: Quantity Mismatch (Conflict C1)
        seedCase(
            "GEN-002",
            "Commercial Conflict 1: Quantity Mismatch (C1)",
            "Bharat Forgings Ltd",
            "Hero MotoCorp Ltd",
            SupplierCategory.SMALL,
            LocalDate.of(2021, 3, 10),
            "INVOICE", "INV-2024-GEN-002.pdf", "TAX INVOICE\nInv: INV-2024-GEN-002\nQty: 120 units\nTotal: ₹ 1,41,600.00",
            14_160_000L, LocalDate.of(2024, 1, 10),
            "PO", "PO-2024-GEN-002.pdf", "PURCHASE ORDER\nPO-2024-GEN-002\nQty: 100 units\nTotal: ₹ 1,18,000.00",
            "ELIGIBLE_FOR_REVIEW", "ELIGIBLE_FOR_REVIEW", "ELIGIBLE_FOR_REVIEW", "HUMAN_REVIEW_REQUIRED",
            "C1", "Billed quantity (120 units) exceeds purchase order quantity (100 units)",
            null, 0L, 0L,
            "RECONCILE_DISCREPANCY",
            "Quantity billed on invoice exceeds purchase order quantity. Reconcile delivery records with buyer procurement.",
            "चालान पर बिल की गई मात्रा खरीद आदेश से अधिक है। खरीदार के साथ डिलीवरी रिकॉर्ड का मिलान करें।"
        );

        // 7. GEN-003: Price Variance (Conflict C2)
        seedCase(
            "GEN-003",
            "Commercial Conflict 2: Unit Price Variance (C2)",
            "Apex Toolings Pvt Ltd",
            "TVS Motor Company Ltd",
            SupplierCategory.MICRO,
            LocalDate.of(2022, 4, 15),
            "INVOICE", "INV-2024-GEN-003.pdf", "TAX INVOICE\nInv: INV-2024-GEN-003\nRate: ₹ 1,200.00\nTotal: ₹ 1,41,600.00",
            14_160_000L, LocalDate.of(2024, 2, 10),
            "PO", "PO-2024-GEN-003.pdf", "PURCHASE ORDER\nPO-2024-GEN-003\nRate: ₹ 1,000.00\nTotal: ₹ 1,18,000.00",
            "ELIGIBLE_FOR_REVIEW", "ELIGIBLE_FOR_REVIEW", "ELIGIBLE_FOR_REVIEW", "HUMAN_REVIEW_REQUIRED",
            "C2", "Unit price variance: Invoice unit rate ₹1,200 exceeds PO rate ₹1,000",
            null, 0L, 0L,
            "RECONCILE_DISCREPANCY",
            "Unit price discrepancy detected between purchase order and tax invoice. Confirm agreed amendment.",
            "खरीद आदेश और कर चालान के बीच इकाई मूल्य में विसंगति पाई गई है। सहमति की पुष्टि करें।"
        );

        // 8. GEN-004: Missing Invoice for Payment (Conflict C3)
        seedCase(
            "GEN-004",
            "Financial Conflict 3: Unrecorded Bank Payment (C3)",
            "Deccan Fabworks",
            "Escorts Kubota Ltd",
            SupplierCategory.SMALL,
            LocalDate.of(2020, 8, 20),
            "PAYMENT", "BANK-STMT-GEN-004.pdf", "BANK STATEMENT\nNEFT Credit Ref UTR554433\nAmount: ₹ 1,75,000.00",
            17_500_000L, LocalDate.of(2024, 2, 5),
            null, null, null,
            "ELIGIBLE_FOR_REVIEW", "ELIGIBLE_FOR_REVIEW", "ELIGIBLE_FOR_REVIEW", "HUMAN_REVIEW_REQUIRED",
            "C3", "Bank remittance received with no matching supplier invoice in records",
            null, 0L, 0L,
            "PROPOSE_PAYMENT_PLAN",
            "Remittance received without corresponding open invoice. Match payment reference against supplier ledger.",
            "संबंधित खुले चालान के बिना भुगतान प्राप्त हुआ। आपूर्तिकर्ता बहीखाते से मिलान करें।"
        );

        // 9. GEN-006: Duplicate Invoice Suspect (Conflict C6)
        seedCase(
            "GEN-006",
            "Commercial Conflict 6: Duplicate Invoice Suspect (C6)",
            "Omkar Electronics",
            "Havells India Ltd",
            SupplierCategory.MICRO,
            LocalDate.of(2021, 11, 5),
            "INVOICE", "INV-2024-GEN-006A.pdf", "TAX INVOICE A\nInv: INV-2024-GEN-006A\nTotal: ₹ 88,500.00",
            8_850_000L, LocalDate.of(2024, 2, 12),
            "INVOICE", "INV-2024-GEN-006B.pdf", "TAX INVOICE B\nInv: INV-2024-GEN-006B\nTotal: ₹ 88,500.00",
            "ELIGIBLE_FOR_REVIEW", "ELIGIBLE_FOR_REVIEW", "ELIGIBLE_FOR_REVIEW", "HUMAN_REVIEW_REQUIRED",
            "C6", "Two invoices detected with identical amount (₹88,500.00) and same date",
            null, 0L, 0L,
            "RECONCILE_DISCREPANCY",
            "Possible duplicate billing detected for ₹88,500.00. Clarify with accounts payable before proceedings.",
            "समान राशि के लिए संभावित डुप्लिकेट बिलिंग का पता चला है। समीक्षा करें।"
        );

        // 10. GEN-007: Disputed Credit Note (Conflict C7)
        seedCase(
            "GEN-007",
            "Commercial Conflict 7: Disputed Credit Note (C7)",
            "Zenith Pumps Pvt Ltd",
            "Kirloskar Brothers Ltd",
            SupplierCategory.MICRO,
            LocalDate.of(2022, 7, 1),
            "INVOICE", "INV-2024-GEN-007.pdf", "TAX INVOICE\nInv: INV-2024-GEN-007\nTotal: ₹ 3,12,000.00",
            31_200_000L, LocalDate.of(2024, 1, 18),
            "CREDIT_NOTE", "CN-2024-GEN-007.pdf", "CREDIT NOTE\nCN-2024-001\nRebate: ₹ 35,000.00 (Buyer rejected credit note)",
            "ELIGIBLE_FOR_REVIEW", "ELIGIBLE_FOR_REVIEW", "ELIGIBLE_FOR_REVIEW", "HUMAN_REVIEW_REQUIRED",
            "C7", "Buyer disputed credit note CN-2024-001 regarding defect discount",
            null, 0L, 0L,
            "CONFIRM_APPLICABLE_TERMS",
            "Credit note adjustment of ₹35,000.00 disputed by buyer. Reach alignment on net receivable amount.",
            "क्रेडिट नोट समायोजन पर खरीदार द्वारा विवाद किया गया है। शुद्ध प्राप्य राशि पर संरेखण तक पहुंचें।"
        );

        // 11. GEN-008: Unverified Payment Claim (Conflict C8)
        seedCase(
            "GEN-008",
            "Financial Conflict 8: Unverified Payment Claim (C8)",
            "Metro Castings Pvt Ltd",
            "Ashok Leyland Ltd",
            SupplierCategory.SMALL,
            LocalDate.of(2021, 9, 14),
            "INVOICE", "INV-2024-GEN-008.pdf", "TAX INVOICE\nInv: INV-2024-GEN-008\nTotal: ₹ 4,50,000.00",
            45_000_000L, LocalDate.of(2024, 1, 25),
            "MESSAGE", "MSG-GEN-008.txt", "WhatsApp message: 'We have processed payment of ₹4,50,000 today via RTGS'",
            "ELIGIBLE_FOR_REVIEW", "ELIGIBLE_FOR_REVIEW", "ELIGIBLE_FOR_REVIEW", "HUMAN_REVIEW_REQUIRED",
            "C8", "Informal message claims payment of ₹4,50,000 but no bank statement or UTR evidences receipt",
            null, 0L, 0L,
            "PROPOSE_PAYMENT_PLAN",
            "Payment claimed informally in message without valid bank UTR. Request bank remittance advice.",
            "संदेश में अनौपचारिक रूप से भुगतान का दावा किया गया है लेकिन बैंक पावती नहीं है। यूटीआर का अनुरोध करें।"
        );

        // 12. GEN-010: Conflicting Commercial Terms (Conflict C10)
        seedCase(
            "GEN-010",
            "Contract Conflict 10: Conflicting Commercial Terms (C10)",
            "Delta Springs Pvt Ltd",
            "Cummins India Ltd",
            SupplierCategory.MICRO,
            LocalDate.of(2021, 10, 10),
            "INVOICE", "INV-2024-GEN-010.pdf", "TAX INVOICE\nInv: INV-2024-GEN-010\nTerms: Net 30 days\nTotal: ₹ 1,95,000.00",
            19_500_000L, LocalDate.of(2024, 1, 5),
            "PO", "PO-2024-GEN-010.pdf", "PURCHASE ORDER\nPO-2024-GEN-010\nPayment Terms: 60 days credit\nTotal: ₹ 1,95,000.00",
            "ELIGIBLE_FOR_REVIEW", "ELIGIBLE_FOR_REVIEW", "HUMAN_REVIEW_REQUIRED", "HUMAN_REVIEW_REQUIRED",
            "C10", "Conflicting payment terms: Invoice specifies 30 days while PO specifies 60 days",
            null, 0L, 0L,
            "CONFIRM_APPLICABLE_TERMS",
            "Conflicting credit periods between invoice (30 days) and purchase order (60 days). Section 15 of MSMED Act caps agreed period at 45 days.",
            "चालान (30 दिन) और खरीद आदेश (60 दिन) के बीच परस्पर विरोधी शर्तें। धारा 15 के तहत 45 दिनों की सीमा लागू होती है।"
        );

        log.info("Successfully seeded 12 benchmark cases with recorded analyses.");
    }

    private void seedCase(
            String code,
            String title,
            String supplierName,
            String buyerName,
            SupplierCategory category,
            LocalDate udyamRegDate,
            String doc1TypeStr, String doc1FileName, String doc1Text,
            long amountPaise, LocalDate doc1Date,
            String doc2TypeStr, String doc2FileName, String doc2Text,
            String gateAStatus, String gateBStatus, String gateCStatus, String gateDStatus,
            String conflictType, String conflictSummary,
            RateReferenceEntity rateRef, long principalPaise, long interestPaise,
            String actionType, String enDraft, String hiDraft) {

        // 1. Case Entity (idempotent lookup)
        UUID caseId = caseRepository.findByCodeAndWorkspaceId(code, DocumentEntity.DEMO_WORKSPACE_ID)
            .map(CaseEntity::id)
            .orElseGet(UUID::randomUUID);
        CaseEntity caseEntity = new CaseEntity(
            caseId, DocumentEntity.DEMO_WORKSPACE_ID, code, title, supplierName, buyerName, category, udyamRegDate, true, false, Instant.now(), 1
        );
        caseRepository.save(caseEntity);

        // 2. Fictional buyer contact
        contactRepository.findByWorkspaceAndCaseId(DocumentEntity.DEMO_WORKSPACE_ID, caseId).orElseGet(() -> {
            String slug = code.toLowerCase().replace("-", "");
            return contactRepository.save(new ContactEntity(
                UUID.randomUUID(),
                DocumentEntity.DEMO_WORKSPACE_ID,
                caseId,
                buyerName != null ? buyerName : "Buyer Corp",
                "accounts@" + slug + ".fictional-corp.example",
                "+91980000" + String.format("%04d", Math.abs(code.hashCode() % 10000)),
                "+91980000" + String.format("%04d", Math.abs(code.hashCode() % 10000)),
                true,
                "DEMO_SEED",
                Instant.now(),
                true,
                "DEMO_SEED",
                Instant.now(),
                null,
                false,
                Instant.now(),
                Instant.now()
            ));
        });

        // 3. Blobs, Templates, Documents & Pages
        byte[] doc1Bytes = doc1Text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String doc1Sha = FileStorageService.calculateSha256(doc1Bytes);
        fileBlobRepository.save(doc1Sha, doc1Bytes, "text/plain", doc1Bytes.length);
        seedTemplateRepository.saveTemplateDocument(code, doc1FileName, doc1TypeStr, doc1Date, doc1Text, doc1Sha);

        byte[] doc2Bytes = null;
        String doc2Sha = null;
        LocalDate doc2Date = doc1Date != null ? doc1Date.plusDays(5) : LocalDate.now();
        if (doc2TypeStr != null && doc2FileName != null && doc2Text != null) {
            doc2Bytes = doc2Text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            doc2Sha = FileStorageService.calculateSha256(doc2Bytes);
            fileBlobRepository.save(doc2Sha, doc2Bytes, "text/plain", doc2Bytes.length);
            seedTemplateRepository.saveTemplateDocument(code, doc2FileName, doc2TypeStr, doc2Date, doc2Text, doc2Sha);
        }

        // Idempotent template record
        String caseJson = String.format(
            "{\"code\":\"%s\",\"title\":\"%s\",\"supplier\":\"%s\",\"buyer\":\"%s\",\"category\":\"%s\"}",
            code, title.replace("\"", "\\\""), supplierName.replace("\"", "\\\""), buyerName.replace("\"", "\\\""), category.name()
        );
        String analysisJson = String.format(
            "{\"gate_a\":\"%s\",\"gate_b\":\"%s\",\"gate_c\":\"%s\",\"gate_d\":\"%s\",\"principal_paise\":%d,\"interest_paise\":%d}",
            gateAStatus, gateBStatus, gateCStatus, gateDStatus, principalPaise, interestPaise
        );
        seedTemplateRepository.saveTemplate(code, caseJson, analysisJson);

        if (!documentRepository.findByCaseId(caseId).isEmpty()) {
            return;
        }

        UUID doc1Id = UUID.randomUUID();
        DocType d1Type = DocType.fromString(doc1TypeStr);
        DocumentEntity doc1 = new DocumentEntity(
            doc1Id, caseId, DocumentEntity.DEMO_WORKSPACE_ID, d1Type, doc1FileName, "text/plain", doc1Sha,
            "db_blob:" + doc1Sha, 1, doc1Date, Instant.now(), doc1Bytes.length, doc1Bytes, doc1Sha
        );
        documentRepository.save(doc1);
        documentPageRepository.save(new DocumentPageEntity(UUID.randomUUID(), doc1Id, 1, doc1Text));

        UUID doc2Id = null;
        if (doc2TypeStr != null && doc2FileName != null && doc2Text != null) {
            doc2Id = UUID.randomUUID();
            DocType d2Type = DocType.fromString(doc2TypeStr);
            DocumentEntity doc2 = new DocumentEntity(
                doc2Id, caseId, DocumentEntity.DEMO_WORKSPACE_ID, d2Type, doc2FileName, "text/plain", doc2Sha,
                "db_blob:" + doc2Sha, 1, doc2Date, Instant.now(), doc2Bytes.length, doc2Bytes, doc2Sha
            );
            documentRepository.save(doc2);
            documentPageRepository.save(new DocumentPageEntity(UUID.randomUUID(), doc2Id, 1, doc2Text));
        }

        // 3. Analysis Run (Status = COMPLETED)
        UUID runId = UUID.randomUUID();
        AnalysisRunEntity run = new AnalysisRunEntity(
            runId, caseId, "COMPLETED",
            "{\"extract\":\"nano\",\"reason\":\"super\",\"adjudicate\":\"ultra\",\"seed\":true}",
            12500L, Instant.now().minusSeconds(120), Instant.now()
        );
        analysisRunRepository.save(run);

        // 4. Extracted Fields
        UUID f1Id = UUID.randomUUID();
        int charStart = Math.max(0, doc1Text.indexOf("Total:"));
        int charEnd = charStart > 0 ? charStart + 15 : doc1Text.length();
        String quote = doc1Text.substring(charStart, Math.min(doc1Text.length(), charEnd));

        ExtractedFieldEntity f1 = new ExtractedFieldEntity(
            f1Id, doc1Id, runId, "total_amount", String.valueOf(amountPaise / 100.0), amountPaise, doc1Date,
            FieldStatus.VERIFIED, 1, charStart, charEnd, quote, "nvidia/nemotron-nano-3-30b-a3b"
        );
        extractedFieldRepository.save(f1);

        // 5. Ledger Entries & Reconciliation
        UUID ledgerId = UUID.randomUUID();
        ledgerEntryRepository.save(new LedgerEntryEntity(
            ledgerId, runId, "INVOICE", code, amountPaise, doc1Date, "CONFIRMED", List.of(f1Id)
        ));

        reconciliationResultRepository.save(new ReconciliationResultEntity(
            runId, amountPaise, 0L, 0L, 0L, 0L, amountPaise, "RECONCILED"
        ));

        // 6. Conflicts
        if (conflictType != null && conflictSummary != null) {
            conflictRepository.save(new ConflictEntity(
                UUID.randomUUID(), runId, conflictType, "RULE", "WARN", conflictSummary, amountPaise, List.of(f1Id), "OPEN"
            ));
        }

        // 7. Eligibility Assessments (Gates A, B, C, D)
        eligibilityAssessmentRepository.save(new EligibilityAssessmentEntity(
            UUID.randomUUID(), runId, "A", gateAStatus, "[\"Gate A status evaluated from enterprise evidence\"]"
        ));
        eligibilityAssessmentRepository.save(new EligibilityAssessmentEntity(
            UUID.randomUUID(), runId, "B", gateBStatus, "[\"Gate B status evaluated from delivery records\"]"
        ));
        eligibilityAssessmentRepository.save(new EligibilityAssessmentEntity(
            UUID.randomUUID(), runId, "C", gateCStatus, "[\"Gate C payment terms evaluated against statutory parameters\"]"
        ));
        eligibilityAssessmentRepository.save(new EligibilityAssessmentEntity(
            UUID.randomUUID(), runId, "D", gateDStatus, "[\"Gate D statutory interest eligibility determined\"]"
        ));

        // 8. Interest Estimate (only if Gate D is ELIGIBLE_FOR_REVIEW)
        if ("ELIGIBLE_FOR_REVIEW".equals(gateDStatus) && rateRef != null && interestPaise > 0) {
            interestEstimateRepository.save(new InterestEstimateEntity(
                UUID.randomUUID(), runId, principalPaise, rateRef.id(), 3,
                doc1Date != null ? doc1Date : LocalDate.now().minusMonths(3), LocalDate.now(),
                interestPaise, "MONTHLY_REST", "PROVISIONAL"
            ));
        }

        // 9. Proposed Actions (EN and HI)
        proposedActionRepository.save(new ProposedActionEntity(
            UUID.randomUUID(), runId, actionType, "en", enDraft, List.of(f1Id), "PROPOSED", null, null
        ));
        proposedActionRepository.save(new ProposedActionEntity(
            UUID.randomUUID(), runId, actionType, "hi", hiDraft, List.of(f1Id), "PROPOSED", null, null
        ));

        // 10. LLM Calls (Realistic token accounting for cost meter)
        llmCallRepository.save(new LlmCallEntity(
            UUID.randomUUID(), runId, null, "extract", "nvidia/nemotron-nano-3-30b-a3b",
            "seed_hash_extract_" + code, 450, 180, 0, 320, 280L, "OK", Instant.now()
        ));
        llmCallRepository.save(new LlmCallEntity(
            UUID.randomUUID(), runId, null, "reason", "nvidia/nemotron-3-super-120b-a12b",
            "seed_hash_reason_" + code, 1200, 400, 0, 750, 2200L, "OK", Instant.now()
        ));
    }
}
