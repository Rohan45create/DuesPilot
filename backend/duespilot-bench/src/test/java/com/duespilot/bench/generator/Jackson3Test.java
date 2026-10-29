package com.duespilot.bench.generator;

import com.duespilot.bench.model.GroundTruth;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class Jackson3Test {

    @Test
    void testJackson3Serialization() throws Exception {
        ObjectMapper mapper = JsonMapper.builder()
            .propertyNamingStrategy(tools.jackson.databind.PropertyNamingStrategies.SNAKE_CASE)
            .build();

        GroundTruth gt = new GroundTruth(
            "GEN-001",
            "Test Case",
            "CONTROL",
            new GroundTruth.SupplierMeta("Supplier A", "27AAPFU0939F1ZV", "MICRO", true, "UDYAM-MH-12-001"),
            new GroundTruth.BuyerMeta("Buyer B", "27AABCU9603R1ZM"),
            new GroundTruth.FinancialGroundTruth(100000L, 84746L, 15254L, 100000L, 0L, 0L, 0L, 0L, "RECONCILED"),
            List.of(),
            new GroundTruth.GateStatuses("ELIGIBLE_FOR_REVIEW", "ELIGIBLE_FOR_REVIEW", "ELIGIBLE_FOR_REVIEW", "NOT_ESTABLISHED"),
            List.of(),
            List.of("ARCHIVE_SETTLED_CASE"),
            List.of("Debt is legally enforceable"),
            List.of(new GroundTruth.DocumentMeta("INVOICE", "INV-001.pdf", "2024-01-01", 100000L, "INV-001", "EN"))
        );

        String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(gt);
        assertThat(json).contains("\"case_code\" : \"GEN-001\"");
        assertThat(json).contains("\"expected_conflicts\"");
        assertThat(json).contains("\"gate_statuses\"");

        GroundTruth readBack = mapper.readValue(json, GroundTruth.class);
        assertThat(readBack.caseCode()).isEqualTo("GEN-001");
        assertThat(readBack.financials().expectedBalancePaise()).isEqualTo(0L);
    }
}
