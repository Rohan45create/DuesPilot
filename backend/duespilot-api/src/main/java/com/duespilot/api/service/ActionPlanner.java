package com.duespilot.api.service;

import com.duespilot.api.domain.CaseEntity;
import com.duespilot.api.domain.ConflictEntity;
import com.duespilot.api.domain.ExtractedFieldEntity;
import com.duespilot.api.domain.InterestEstimateEntity;
import com.duespilot.api.domain.ProposedActionEntity;
import com.duespilot.api.llm.DraftActionPolicy;
import com.duespilot.api.llm.LlmClient;
import com.duespilot.api.llm.ModelRouter;
import com.duespilot.core.gate.CaseEligibility;
import com.duespilot.core.gate.GateStatus;
import com.duespilot.core.model.CaseReconciliation;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ActionPlanner {

    private static final Logger log = LoggerFactory.getLogger(ActionPlanner.class);

    private final ModelRouter modelRouter;
    private final com.duespilot.api.config.DuesPilotProperties duesPilotProperties;

    public ActionPlanner(ModelRouter modelRouter) {
        this(modelRouter, new com.duespilot.api.config.DuesPilotProperties());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ActionPlanner(ModelRouter modelRouter, com.duespilot.api.config.DuesPilotProperties duesPilotProperties) {
        this.modelRouter = modelRouter;
        this.duesPilotProperties = duesPilotProperties;
    }

    public List<ProposedActionEntity> planActions(
            CaseEntity c,
            CaseReconciliation recon,
            List<ConflictEntity> conflicts,
            CaseEligibility eligibility,
            @Nullable InterestEstimateEntity interestEstimate,
            List<ExtractedFieldEntity> verifiedFields,
            UUID runId) {

        List<ProposedActionEntity> actions = new ArrayList<>();

        // Gather relevant evidence field IDs
        List<UUID> evidenceIds = verifiedFields.stream()
            .map(ExtractedFieldEntity::id)
            .filter(id -> id != null)
            .limit(5)
            .toList();

        // Determine action type from deterministic priority
        String actionType;
        String fallbackEnDraft;
        String fallbackHiDraft;

        if (recon.isFullyReconciled() || (recon.totalOutstandingBalance() != null && recon.totalOutstandingBalance().isZero())) {
            actionType = "ARCHIVE_SETTLED_CASE";
            fallbackEnDraft = "The case receivable is fully settled with a zero outstanding balance. All verified payments and credit notes reconcile against the invoice. Recommended action: archive case as settled.";
            fallbackHiDraft = "यह मामला शून्य बकाया राशि के साथ पूरी तरह से सुलझा हुआ है। सभी सत्यापित भुगतान और क्रेडिट नोट इनवॉइस से मेल खाते हैं। अनुशंसित कार्रवाई: मामले को सुलझे हुए के रूप में संग्रहीत करें।";
        } else if (eligibility.gateA().status() == GateStatus.NOT_ESTABLISHED) {
            actionType = "REQUEST_STATUTORY_EVIDENCE";
            fallbackEnDraft = "Udyam registration documentation is missing or unverified. Request a copy of the valid Udyam certificate to establish statutory eligibility.";
            fallbackHiDraft = "उद्यम पंजीकरण दस्तावेज़ अनुपलब्ध या असत्यापित है। वैधानिक पात्रता स्थापित करने के लिए वैध उद्यम प्रमाणपत्र की एक प्रति का अनुरोध करें।";
        } else if (eligibility.gateB().status() != GateStatus.ELIGIBLE_FOR_REVIEW || hasConflict(conflicts, "C5")) {
            actionType = "REQUEST_DELIVERY_PROOF";
            fallbackEnDraft = "Delivery or acceptance documentation is not confirmed. Request proof of delivery (signed delivery challan or consignment note) from the buyer to substantiate receipt.";
            fallbackHiDraft = "वितरण या स्वीकृति दस्तावेज़ की पुष्टि नहीं हुई है। माल प्राप्ति की पुष्टि के लिए खरीदार से डिलीवरी का प्रमाण (हस्ताक्षरित डिलीवरी चालान) का अनुरोध करें।";
        } else if (hasConflict(conflicts, "C9")) {
            actionType = "REQUEST_CLARIFICATION_ON_DEDUCTION";
            fallbackEnDraft = "A deduction was itemised in the remittance advice against the invoice. Request itemised clarification from the buyer regarding the basis for the deduction.";
            fallbackHiDraft = "भुगतान प्रेषण सूचना में चालान राशि से कटौती की गई है। कटौती के आधार के संबंध में खरीदार से मदवार स्पष्टीकरण का अनुरोध करें।";
        } else if (eligibility.gateC().status() == GateStatus.HUMAN_REVIEW_REQUIRED || hasConflict(conflicts, "C4")) {
            actionType = "CONFIRM_APPLICABLE_TERMS";
            fallbackEnDraft = "Payment terms differ across commercial documents or are unconfirmed. Contact the buyer's procurement team to confirm the mutually agreed payment timeline.";
            fallbackHiDraft = "दस्तावेज़ों में भुगतान की शर्तें भिन्न हैं या अपुष्ट हैं। पारस्परिक रूप से सहमत भुगतान समयरेखा की पुष्टि के लिए खरीदार की खरीद टीम से संपर्क करें।";
        } else if (interestEstimate != null && eligibility.gateD().status() == GateStatus.ELIGIBLE_FOR_REVIEW) {
            actionType = "ISSUE_INTEREST_DEMAND";
            fallbackEnDraft = String.format("A provisional interest calculation of ₹%.2f has been estimated under Section 16 of the MSMED Act for the overdue principal. Provide an itemised interest statement to the buyer for review.",
                interestEstimate.interestPaise() / 100.0);
            fallbackHiDraft = String.format("अतिदेय मूलधन पर एमएसएमई अधिनियम की धारा 16 के तहत ₹%.2f का अनंतिम ब्याज आंका गया है। समीक्षा के लिए खरीदार को एक मदवार ब्याज विवरण प्रदान करें।",
                interestEstimate.interestPaise() / 100.0);
        } else if (hasConflict(conflicts, "C1")) {
            actionType = "PROPOSE_PAYMENT_PLAN";
            fallbackEnDraft = "A partial payment was confirmed, leaving an outstanding balance. Propose a structured payment schedule for the remaining dues.";
            fallbackHiDraft = "आंशिक भुगतान की पुष्टि हुई है, जिससे शेष राशि बकाया है। शेष बकाया राशि के लिए एक संरचित भुगतान कार्यक्रम का प्रस्ताव करें।";
        } else {
            actionType = "RECONCILE_DISCREPANCY";
            fallbackEnDraft = "Commercial discrepancies exist between the invoices and payments. Share the itemised reconciliation ledger with the buyer for alignment.";
            fallbackHiDraft = "चालान और भुगतानों के बीच वाणिज्यिक विसंगतियां हैं। समाधान के लिए खरीदार के साथ मदवार समाधान बहीखाता साझा करें।";
        }

        // Try calling Super for LLM draft refinement (Rule R-26, R-28) unless in replay mode
        String enDraft = fallbackEnDraft;
        String hiDraft = fallbackHiDraft;

        if (duesPilotProperties == null || !duesPilotProperties.isReplayMode()) {
            try {
                var messages = List.of(
                new LlmClient.Message("system", "You are an assistant for MSME receivables. Draft polite, professional business messages in English and Hindi. Never make legal threats or conclusions (Rule R-7). Return JSON with keys 'en_draft' and 'hi_draft'."),
                new LlmClient.Message("user", "<context>\nAction: " + actionType + "\nSupplier: " + c.supplierName() + "\nBuyer: " + c.buyerName() + "\nDetails: " + fallbackEnDraft + "\n</context>\nGenerate the drafts as JSON.")
            );
            ModelRouter.RouterResult routerResult = modelRouter.draftAction(messages, runId);
            if (routerResult.isSuccess() && routerResult.parsedJson() != null) {
                JsonNode json = routerResult.parsedJson();
                if (json.has("en_draft") && !json.get("en_draft").asText().isBlank()) {
                    enDraft = json.get("en_draft").asText();
                }
                if (json.has("hi_draft") && !json.get("hi_draft").asText().isBlank()) {
                    hiDraft = json.get("hi_draft").asText();
                }
            }
        } catch (Exception e) {
            log.warn("LLM draft generation failed or unavailable; using deterministic drafts", e);
        }
        }

        // Add English draft
        actions.add(new ProposedActionEntity(
            null,
            runId,
            actionType,
            "en",
            enDraft,
            evidenceIds,
            "PROPOSED",
            null,
            null
        ));

        // Add Hindi draft
        actions.add(new ProposedActionEntity(
            null,
            runId,
            actionType,
            "hi",
            hiDraft,
            evidenceIds,
            "PROPOSED",
            null,
            null
        ));

        return actions;
    }

    private static boolean hasConflict(List<ConflictEntity> conflicts, String type) {
        return conflicts.stream().anyMatch(c -> type.equalsIgnoreCase(c.type()));
    }
}
