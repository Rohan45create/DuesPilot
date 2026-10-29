package com.duespilot.api.service.notification;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Enforces Rule R-73: WhatsApp content restriction.
 * WhatsApp carries only the approved message catalogue.
 * Legal notices, interest demands, statutory penalties, and collection threats
 * are strictly prohibited on WhatsApp.
 */
public final class WhatsAppTemplateCatalogue {

    private WhatsAppTemplateCatalogue() {}

    public static final String DELIVERY_PROOF_REQUEST = "delivery_proof_request";
    public static final String PAYMENT_ALLOCATION = "payment_allocation";
    public static final String CREDIT_NOTE_ADJUSTMENT = "credit_note_adjustment";
    public static final String RECONCILED_BALANCE_CONFIRMATION = "reconciled_balance_confirmation";
    public static final String CONFIRMED_DATE_REQUEST = "confirmed_date_request";
    public static final String THANK_YOU = "thank_you";

    public static final Set<String> ALLOWED_TEMPLATES = Set.of(
        DELIVERY_PROOF_REQUEST,
        PAYMENT_ALLOCATION,
        CREDIT_NOTE_ADJUSTMENT,
        RECONCILED_BALANCE_CONFIRMATION,
        CONFIRMED_DATE_REQUEST,
        THANK_YOU
    );

    private static final List<String> PROHIBITED_TERMS = List.of(
        "legal notice",
        "demand notice",
        "court",
        "advocate",
        "section 16",
        "penal interest",
        "statutory penalty",
        "statutory interest",
        "recovery agent",
        "police",
        "lawsuit",
        "litigation",
        "legal action",
        "legal proceedings",
        "threat"
    );

    public static boolean isAllowedTemplate(String templateKey) {
        if (templateKey == null) return false;
        return ALLOWED_TEMPLATES.contains(templateKey.trim().toLowerCase());
    }

    public static boolean containsProhibitedContent(String text) {
        if (text == null || text.isBlank()) return false;
        String lower = text.toLowerCase();
        for (String term : PROHIBITED_TERMS) {
            if (lower.contains(term)) {
                return true;
            }
        }
        return false;
    }

    public static String render(String templateKey, Map<String, String> vars) {
        String buyer = vars.getOrDefault("buyer_name", "Valued Buyer");
        String caseCode = vars.getOrDefault("case_code", "");
        String inv = vars.getOrDefault("invoice_number", "INV");
        String amt = vars.getOrDefault("amount", "");
        String balance = vars.getOrDefault("balance", "");
        String utr = vars.getOrDefault("utr_number", "");

        return switch (templateKey.toLowerCase()) {
            case DELIVERY_PROOF_REQUEST ->
                "Dear " + buyer + ", please share the signed delivery challan or lorry receipt for invoice " + inv + " to help us complete bilateral ledger reconciliation for case " + caseCode + ".";
            case PAYMENT_ALLOCATION ->
                "Dear " + buyer + ", we have recorded payment of ₹" + amt + (utr.isBlank() ? "" : " (UTR " + utr + ")") + ". Kindly confirm which invoice numbers this should be allocated against.";
            case CREDIT_NOTE_ADJUSTMENT ->
                "Dear " + buyer + ", we have updated credit note adjustment of ₹" + amt + " for invoice " + inv + ". Please confirm this aligns with your records.";
            case RECONCILED_BALANCE_CONFIRMATION ->
                "Dear " + buyer + ", following our reconciliation on case " + caseCode + ", the verified net payable balance is ₹" + balance + ". Please review at your earliest convenience.";
            case CONFIRMED_DATE_REQUEST ->
                "Dear " + buyer + ", kindly confirm the goods acceptance date for invoice " + inv + " so we can finalize mutual accounting dates.";
            case THANK_YOU ->
                "Dear " + buyer + ", thank you for the payment of ₹" + amt + ". Our ledger accounts have been successfully updated.";
            default ->
                "Dear " + buyer + ", please review our commercial accounts reconciliation for case " + caseCode + ".";
        };
    }
}
