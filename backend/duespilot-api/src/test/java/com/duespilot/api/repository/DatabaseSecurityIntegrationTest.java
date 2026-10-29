package com.duespilot.api.repository;

import com.duespilot.api.domain.CaseEntity;
import com.duespilot.api.domain.DocType;
import com.duespilot.api.domain.DocumentEntity;
import com.duespilot.api.domain.SupplierCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("Database Security, Schema Isolation, Bytea Storage & Health Check Integration Tests")
class DatabaseSecurityIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private CaseRepository caseRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private MockMvc mockMvc;

    private static final List<String> APPLICATION_TABLES = List.of(
        "cases",
        "documents",
        "document_pages",
        "extracted_fields",
        "analysis_runs",
        "ledger_entries",
        "reconciliation_results",
        "conflicts",
        "eligibility_assessments",
        "rate_references",
        "interest_estimates",
        "proposed_actions",
        "llm_calls",
        "benchmark_runs",
        "benchmark_results",
        "audit_events"
    );

    @Test
    @DisplayName("Security: No application tables exist in or are readable through the public schema")
    void testNoApplicationTableInPublicSchema() {
        List<String> publicTables = jdbcClient.sql("""
            SELECT table_name
            FROM information_schema.tables
            WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
            """)
            .query(String.class)
            .list();

        // The only application-managed table permitted in public is keep_alive
        for (String appTable : APPLICATION_TABLES) {
            assertThat(publicTables)
                .as("Application table '%s' must NOT exist in the public schema", appTable)
                .doesNotContain(appTable);
        }

        assertThat(publicTables)
            .as("public.keep_alive must exist in public schema for Supabase keep-alive ping")
            .contains("keep_alive");

        // Directly querying public.cases must fail because the relation does not exist in public
        assertThatThrownBy(() -> jdbcClient.sql("SELECT * FROM public.cases").query().listOfRows())
            .isInstanceOf(BadSqlGrammarException.class);
    }

    @Test
    @DisplayName("Security: All application tables reside in 'app' schema with Row Level Security enabled")
    void testApplicationTablesResideInAppSchemaWithRlsEnabled() {
        List<Map<String, Object>> appTables = jdbcClient.sql("""
            SELECT tablename, rowsecurity
            FROM pg_tables
            WHERE schemaname = 'app'
            """)
            .query()
            .listOfRows();

        List<String> foundTableNames = appTables.stream()
            .map(row -> (String) row.get("tablename"))
            .toList();

        for (String expectedTable : APPLICATION_TABLES) {
            assertThat(foundTableNames)
                .as("Table '%s' must reside in the private 'app' schema", expectedTable)
                .contains(expectedTable);
        }

        // Verify that EVERY application table in app has Row Level Security (RLS) enabled
        for (Map<String, Object> row : appTables) {
            String tableName = (String) row.get("tablename");
            Boolean rlsEnabled = (Boolean) row.get("rowsecurity");
            assertThat(rlsEnabled)
                .as("Table 'app.%s' must have Row Level Security (RLS) enabled", tableName)
                .isTrue();
        }
    }

    @Test
    @DisplayName("Security: Application tables have zero public policies (cannot be read via Data API)")
    void testNoPoliciesOnAppTables() {
        List<Map<String, Object>> policies = jdbcClient.sql("""
            SELECT tablename, policyname
            FROM pg_policies
            WHERE schemaname = 'app'
            """)
            .query()
            .listOfRows();

        assertThat(policies)
            .as("Application tables in schema 'app' must have zero policies so Data API cannot read them")
            .isEmpty();
    }

    @Test
    @DisplayName("Keep-Alive: public.keep_alive has RLS enabled, public read policy, and 1 initial row")
    void testKeepAliveIsPubliclyReadable() {
        Boolean rlsEnabled = jdbcClient.sql("""
            SELECT rowsecurity
            FROM pg_tables
            WHERE schemaname = 'public' AND tablename = 'keep_alive'
            """)
            .query(Boolean.class)
            .single();

        assertThat(rlsEnabled).isTrue();

        List<String> policies = jdbcClient.sql("""
            SELECT policyname
            FROM pg_policies
            WHERE schemaname = 'public' AND tablename = 'keep_alive'
            """)
            .query(String.class)
            .list();

        assertThat(policies).isNotEmpty();

        Long count = jdbcClient.sql("SELECT count(*) FROM public.keep_alive")
            .query(Long.class)
            .single();

        assertThat(count).isGreaterThanOrEqualTo(1L);
    }

    @Test
    @DisplayName("Postgres bytea: Files stay in Postgres bytea (5 MB limit) and can be saved and retrieved intact")
    void testDocumentByteaStorageAndRetrieval() {
        CaseEntity caseEntity = caseRepository.save(new CaseEntity(
            UUID.randomUUID(),
            "DP-BYTEA-" + UUID.randomUUID().toString().substring(0, 8),
            "Bytea Test Case",
            "Bytea Supplier",
            "Bytea Buyer",
            SupplierCategory.MICRO,
            null,
            false,
            Instant.now(),
            0
        ));

        byte[] originalContent = "PDF-1.4 % MSME Invoice Binary Bytea Content ₹50,000".getBytes(StandardCharsets.UTF_8);

        DocumentEntity doc = new DocumentEntity(
            UUID.randomUUID(),
            caseEntity.id(),
            DocType.INVOICE,
            "test_bytea_invoice.pdf",
            "application/pdf",
            "sha256_dummy_bytea_hash",
            "bytea",
            1,
            LocalDate.of(2024, 6, 1),
            Instant.now(),
            originalContent.length,
            originalContent
        );

        DocumentEntity saved = documentRepository.save(doc);
        assertThat(saved.id()).isEqualTo(doc.id());
        assertThat(saved.fileSizeBytes()).isEqualTo(originalContent.length);
        assertThat(saved.contentBytes()).isEqualTo(originalContent);

        DocumentEntity reloaded = documentRepository.findById(saved.id()).orElseThrow();
        assertThat(reloaded.contentBytes()).isEqualTo(originalContent);
        assertThat(reloaded.fileSizeBytes()).isEqualTo(originalContent.length);
    }

    @Test
    @DisplayName("Actuator: /actuator/health reports database reachability as UP")
    void testActuatorHealthReportsDatabaseUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.components.db.status").value("UP"));
    }
}
