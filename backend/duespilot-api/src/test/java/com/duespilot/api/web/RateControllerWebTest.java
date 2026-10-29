package com.duespilot.api.web;

import com.duespilot.api.config.SecurityHeadersFilter;
import com.duespilot.api.domain.AuditEventEntity;
import com.duespilot.api.domain.RateReferenceEntity;
import com.duespilot.api.rate.OfficialGuidanceLink;
import com.duespilot.api.repository.AuditEventRepository;
import com.duespilot.api.repository.RateReferenceRepository;
import com.duespilot.api.web.error.ApiException;
import com.duespilot.api.web.error.ApiExceptionHandler;
import com.duespilot.core.rate.RateCandidate;
import com.duespilot.core.rate.RateProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RateController.class)
@Import({ApiExceptionHandler.class, SecurityHeadersFilter.class})
@DisplayName("RateController Web Tests (Architecture §10.7 & Rule R-19)")
class RateControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RateProvider rateProvider;

    @MockitoBean
    private RateReferenceRepository rateReferenceRepository;

    @MockitoBean
    private AuditEventRepository auditEventRepository;

    private static final List<OfficialGuidanceLink> GUIDANCE_LINKS = List.of(
        new OfficialGuidanceLink("MSMED Act", "https://www.indiacode.nic.in/handle/123456789/2013", "indiacode.nic.in", "Statutory framework"),
        new OfficialGuidanceLink("MSME Samadhaan", "https://samadhaan.msme.gov.in/", "msme.gov.in", "Filing portal")
    );

    @Test
    @DisplayName("GET /api/v1/rates/bank-rate returns 200 with candidate and guidance links")
    void getBankRateSuccess() throws Exception {
        UUID rateId = UUID.randomUUID();
        RateCandidate candidate = RateCandidate.unconfirmed(
            650,
            LocalDate.of(2024, 2, 8),
            "https://www.rbi.org.in/rates",
            "Bank Rate is 6.50%"
        );

        RateReferenceEntity entity = new RateReferenceEntity(
            rateId,
            candidate.sourceUrl(),
            candidate.excerpt(),
            candidate.rateBps(),
            candidate.effectiveDate(),
            Instant.now(),
            null,
            null
        );

        when(rateProvider.getLatestBankRate()).thenReturn(candidate);
        when(rateReferenceRepository.findLatest()).thenReturn(Optional.of(entity));

        mockMvc.perform(get("/api/v1/rates/bank-rate"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(rateId.toString()))
            .andExpect(jsonPath("$.rateBps").value(650))
            .andExpect(jsonPath("$.ratePercent").value(6.5))
            .andExpect(jsonPath("$.effectiveOn").value("2024-02-08"))
            .andExpect(jsonPath("$.sourceUrl").value("https://www.rbi.org.in/rates"))
            .andExpect(jsonPath("$.rawExcerpt").value("Bank Rate is 6.50%"))
            .andExpect(jsonPath("$.isConfirmed").value(false))
            .andExpect(jsonPath("$.officialGuidanceLinks").isArray())
            .andExpect(jsonPath("$.officialGuidanceLinks[0].source").value("indiacode.nic.in"));
    }

    @Test
    @DisplayName("GET /api/v1/rates/bank-rate returns 502 RATE_LOOKUP_FAILED when provider lookup fails")
    void getBankRateLookupFailure() throws Exception {
        when(rateProvider.getLatestBankRate())
            .thenThrow(new ApiException(HttpStatus.BAD_GATEWAY, "RATE_LOOKUP_FAILED", "Tavily search returned no verified rate"));

        mockMvc.perform(get("/api/v1/rates/bank-rate"))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.code").value("RATE_LOOKUP_FAILED"))
            .andExpect(jsonPath("$.status").value(502));
    }

    @Test
    @DisplayName("POST /api/v1/rates/bank-rate/{id}/confirm records human confirmation and emits audit event")
    void confirmBankRateSuccess() throws Exception {
        UUID rateId = UUID.randomUUID();
        RateReferenceEntity unconfirmed = new RateReferenceEntity(
            rateId,
            "https://www.rbi.org.in/rates",
            "Bank Rate is 6.50%",
            650,
            LocalDate.of(2024, 2, 8),
            Instant.now(),
            null,
            null
        );

        Instant confirmedAt = Instant.now();
        RateReferenceEntity confirmed = new RateReferenceEntity(
            rateId,
            "https://www.rbi.org.in/rates",
            "Bank Rate is 6.50%",
            650,
            LocalDate.of(2024, 2, 8),
            unconfirmed.retrievedAt(),
            confirmedAt,
            "OPERATOR_JANE"
        );

        when(rateReferenceRepository.findById(rateId))
            .thenReturn(Optional.of(unconfirmed))
            .thenReturn(Optional.of(confirmed));
        when(rateReferenceRepository.confirm(eq(rateId), eq("OPERATOR_JANE"), any())).thenReturn(true);

        String requestBody = """
            {
              "confirmedBy": "OPERATOR_JANE"
            }
            """;

        mockMvc.perform(post("/api/v1/rates/bank-rate/{id}/confirm", rateId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(rateId.toString()))
            .andExpect(jsonPath("$.isConfirmed").value(true))
            .andExpect(jsonPath("$.confirmedBy").value("OPERATOR_JANE"));

        verify(rateReferenceRepository).confirm(eq(rateId), eq("OPERATOR_JANE"), any());

        ArgumentCaptor<AuditEventEntity> auditCaptor = ArgumentCaptor.forClass(AuditEventEntity.class);
        verify(auditEventRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().eventType()).isEqualTo("RATE_CONFIRMED");
        assertThat(auditCaptor.getValue().actor()).isEqualTo("OPERATOR_JANE");
        assertThat(auditCaptor.getValue().payload()).contains(rateId.toString());
    }

    @Test
    @DisplayName("POST /api/v1/rates/bank-rate/{id}/confirm returns 404 RATE_NOT_FOUND when ID missing")
    void confirmBankRateNotFound() throws Exception {
        UUID nonExistent = UUID.randomUUID();
        when(rateReferenceRepository.findById(nonExistent)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/rates/bank-rate/{id}/confirm", nonExistent)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("RATE_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /api/v1/rates/bank-rate/{id}/confirm returns 422 RATE_INVALID when rate candidate is invalid")
    void confirmBankRateInvalid() throws Exception {
        UUID rateId = UUID.randomUUID();
        RateReferenceEntity invalidEntity = new RateReferenceEntity(
            rateId,
            "https://www.rbi.org.in/rates",
            "Implausible Rate",
            100, // < 200 bps
            LocalDate.of(2024, 2, 8),
            Instant.now(),
            null,
            null
        );

        when(rateReferenceRepository.findById(rateId)).thenReturn(Optional.of(invalidEntity));

        mockMvc.perform(post("/api/v1/rates/bank-rate/{id}/confirm", rateId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("RATE_INVALID"));
    }
}
