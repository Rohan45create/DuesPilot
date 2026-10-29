package com.duespilot.api.web;

import com.duespilot.api.domain.AuditEventEntity;
import com.duespilot.api.domain.RateReferenceEntity;
import com.duespilot.core.rate.RateProvider;
import com.duespilot.api.repository.AuditEventRepository;
import com.duespilot.api.repository.RateReferenceRepository;
import com.duespilot.api.web.dto.ConfirmRateRequest;
import com.duespilot.api.web.dto.RateResponse;
import com.duespilot.api.web.error.ApiException;
import com.duespilot.core.rate.RateCandidate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rates")
public class RateController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RateProvider rateProvider;
    private final RateReferenceRepository rateReferenceRepository;
    private final AuditEventRepository auditEventRepository;

    public RateController(
            RateProvider rateProvider,
            RateReferenceRepository rateReferenceRepository,
            AuditEventRepository auditEventRepository) {
        this.rateProvider = rateProvider;
        this.rateReferenceRepository = rateReferenceRepository;
        this.auditEventRepository = auditEventRepository;
    }

    /**
     * GET /api/v1/rates/bank-rate
     * Returns the candidate Bank Rate with source URL, excerpt, and official guidance links.
     * Caches 24 hours. Failure returns 502 RATE_LOOKUP_FAILED.
     */
    @GetMapping("/bank-rate")
    public ResponseEntity<RateResponse> getBankRate() {
        RateCandidate candidate = rateProvider.getLatestBankRate();

        // Retrieve persisted or cached entity from DB
        RateReferenceEntity entity = rateReferenceRepository.findLatest()
            .orElseGet(() -> new RateReferenceEntity(
                UUID.randomUUID(),
                candidate.sourceUrl(),
                candidate.excerpt() != null ? candidate.excerpt() : "",
                candidate.rateBps(),
                candidate.effectiveDate(),
                Instant.now(),
                candidate.isConfirmed() ? Instant.now() : null,
                candidate.isConfirmed() ? "CONFIRMED" : null
            ));

        RateResponse response = RateResponse.fromEntity(entity, com.duespilot.api.rate.TavilyRateProvider.OFFICIAL_GUIDANCE_LINKS);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/rates/bank-rate/{id}/confirm
     * Human confirmation of the candidate Bank Rate per Rule R-19.
     * Emits RATE_CONFIRMED audit event.
     */
    @PostMapping("/bank-rate/{id}/confirm")
    public ResponseEntity<RateResponse> confirmBankRate(
            @PathVariable("id") UUID id,
            @RequestBody(required = false) ConfirmRateRequest request) {

        RateReferenceEntity entity = rateReferenceRepository.findById(id)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RATE_NOT_FOUND", "Rate reference not found: " + id));

        // Plausible range & date check (422 RATE_INVALID)
        if (entity.rateBps() < 200 || entity.rateBps() > 2000 || entity.effectiveOn().isAfter(LocalDate.now())) {
            throw new ApiException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "RATE_INVALID",
                "Rate candidate is invalid: rate must be between 200 and 2000 bps and effective date cannot be in the future"
            );
        }

        String confirmedBy = (request != null && request.confirmedBy() != null && !request.confirmedBy().isBlank())
            ? request.confirmedBy()
            : "HUMAN_OPERATOR";
        Instant now = Instant.now();

        rateReferenceRepository.confirm(id, confirmedBy, now);

        // Record audit event
        String payloadJson;
        try {
            payloadJson = MAPPER.writeValueAsString(Map.of(
                "rateRefId", id.toString(),
                "rateBps", entity.rateBps(),
                "effectiveOn", entity.effectiveOn().toString(),
                "confirmedBy", confirmedBy,
                "confirmedAt", now.toString()
            ));
        } catch (Exception e) {
            payloadJson = "{}";
        }

        auditEventRepository.save(new AuditEventEntity(
            null,
            null,
            confirmedBy,
            "RATE_CONFIRMED",
            payloadJson,
            now
        ));

        RateReferenceEntity updated = rateReferenceRepository.findById(id).orElseThrow();
        return ResponseEntity.ok(RateResponse.fromEntity(updated, com.duespilot.api.rate.TavilyRateProvider.OFFICIAL_GUIDANCE_LINKS));
    }
}
