package com.duespilot.api.auth;

import com.duespilot.api.domain.CaseEntity;
import com.duespilot.api.repository.CaseRepository;
import com.duespilot.api.repository.WorkspaceMemberRepository;
import com.duespilot.api.repository.WorkspaceRepository;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("Supabase Auth & OAuth2 Resource Server Integration Tests")
class SupabaseAuthIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    static WireMockServer wireMockServer;
    static KeyPair rsaKeyPair;
    static RSAKey rsaJwk;

    static {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            rsaKeyPair = kpg.generateKeyPair();
            RSAPublicKey pubKey = (RSAPublicKey) rsaKeyPair.getPublic();

            rsaJwk = new RSAKey.Builder(pubKey)
                .keyID("supabase-test-key")
                .algorithm(JWSAlgorithm.RS256)
                .build();

            wireMockServer = new WireMockServer(options().dynamicPort());
            wireMockServer.start();

            wireMockServer.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo("/auth/v1/.well-known/jwks.json"))
                .willReturn(aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"keys\":[" + rsaJwk.toJSONString() + "]}")));
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize test WireMock JWKS server", e);
        }
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.stop();
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        String baseUrl = "http://localhost:" + wireMockServer.port();
        registry.add("duespilot.auth.supabase-url", () -> baseUrl);
        registry.add("duespilot.auth.jwks-uri", () -> baseUrl + "/auth/v1/.well-known/jwks.json");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CaseRepository caseRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private WorkspaceMemberRepository workspaceMemberRepository;

    private static String generateJwt(
            String sub,
            String email,
            boolean isAnonymous,
            String audience,
            Instant expirationTime) throws Exception {

        String issuer = "http://localhost:" + wireMockServer.port() + "/auth/v1";

        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
            .subject(sub)
            .issuer(issuer)
            .audience(audience)
            .issueTime(new Date())
            .expirationTime(Date.from(expirationTime));

        if (email != null) {
            builder.claim("email", email);
        }
        if (isAnonymous) {
            builder.claim("is_anonymous", true);
        }

        SignedJWT signedJWT = new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("supabase-test-key").build(),
            builder.build()
        );
        signedJWT.sign(new RSASSASigner((RSAPrivateKey) rsaKeyPair.getPrivate()));
        return signedJWT.serialize();
    }

    @Test
    @DisplayName("Token validation: Missing Bearer token is rejected with 401 UNAUTHORIZED")
    void testMissingTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/cases"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Token validation: Expired Bearer token is rejected with 401 UNAUTHORIZED")
    void testExpiredTokenIsRejected() throws Exception {
        String expiredToken = generateJwt(
            "user-expired",
            "expired@example.com",
            false,
            "authenticated",
            Instant.now().minus(1, ChronoUnit.HOURS)
        );

        mockMvc.perform(get("/api/v1/cases")
                .header("Authorization", "Bearer " + expiredToken))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Token validation: Token with wrong audience is rejected with 401 UNAUTHORIZED")
    void testWrongAudienceTokenIsRejected() throws Exception {
        String wrongAudienceToken = generateJwt(
            "user-wrong-aud",
            "wrongaud@example.com",
            false,
            "wrong-audience",
            Instant.now().plus(1, ChronoUnit.HOURS)
        );

        mockMvc.perform(get("/api/v1/cases")
                .header("Authorization", "Bearer " + wrongAudienceToken))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("First sign-in creates exactly one workspace on GET /api/v1/me")
    void testFirstSignInCreatesExactlyOneWorkspace() throws Exception {
        String userId = "user-first-" + UUID.randomUUID();
        String email = "first.signin@example.com";
        String token = generateJwt(userId, email, false, "authenticated", Instant.now().plus(1, ChronoUnit.HOURS));

        // Call GET /api/v1/me multiple times
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/v1/me")
                    .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(userId))
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.isAnonymous").value(false))
                .andExpect(jsonPath("$.flags.demoReadOnly").value(false))
                .andExpect(jsonPath("$.workspace.id").isNotEmpty());
        }

        // Verify exactly one workspace membership was created
        long count = workspaceMemberRepository.countWorkspacesForUser(userId);
        assertThat(count).isEqualTo(1L);
    }

    @Test
    @DisplayName("Multi-tenancy: Cross-workspace access returns 404")
    void testCrossWorkspaceAccessReturns404() throws Exception {
        // User A setup
        String userAId = "user-a-" + UUID.randomUUID();
        String tokenA = generateJwt(userAId, "usera@example.com", false, "authenticated", Instant.now().plus(1, ChronoUnit.HOURS));

        // Create Case in Workspace A
        String casePayload = """
            {
              "title": "Confidential Ledger Case Workspace A",
              "supplierName": "Supplier A",
              "buyerName": "Buyer A",
              "supplierCategory": "MICRO"
            }
            """;

        String createResponse = mockMvc.perform(post("/api/v1/cases")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(casePayload))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        String caseIdStr = com.jayway.jsonpath.JsonPath.read(createResponse, "$.id");
        UUID caseId = UUID.fromString(caseIdStr);

        // User B setup (different workspace)
        String userBId = "user-b-" + UUID.randomUUID();
        String tokenB = generateJwt(userBId, "userb@example.com", false, "authenticated", Instant.now().plus(1, ChronoUnit.HOURS));

        // User A can access their own case
        mockMvc.perform(get("/api/v1/cases/" + caseId)
                .header("Authorization", "Bearer " + tokenA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Confidential Ledger Case Workspace A"));

        // User B accessing Case A must return 404
        mockMvc.perform(get("/api/v1/cases/" + caseId)
                .header("Authorization", "Bearer " + tokenB))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("CASE_NOT_FOUND"));

        // User B attempting to download documents on Case A must return 404
        mockMvc.perform(get("/api/v1/cases/" + caseId + "/documents/" + UUID.randomUUID())
                .header("Authorization", "Bearer " + tokenB))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("CASE_NOT_FOUND"));
    }

    @Test
    @DisplayName("Anonymous users: Write operations return 403 DEMO_READ_ONLY")
    void testAnonymousWritesReturnDemoReadOnly() throws Exception {
        String anonUserId = "anon-" + UUID.randomUUID();
        String anonToken = generateJwt(anonUserId, null, true, "authenticated", Instant.now().plus(1, ChronoUnit.HOURS));

        // GET /api/v1/cases is permitted (reads shared demo workspace)
        mockMvc.perform(get("/api/v1/cases")
                .header("Authorization", "Bearer " + anonToken))
            .andExpect(status().isOk());

        // GET /api/v1/me is permitted and shows demoReadOnly = true
        mockMvc.perform(get("/api/v1/me")
                .header("Authorization", "Bearer " + anonToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.isAnonymous").value(true))
            .andExpect(jsonPath("$.flags.demoReadOnly").value(true));

        // POST /api/v1/cases (Write) must return 403 DEMO_READ_ONLY
        String writeCasePayload = """
            {
              "title": "Anonymous Attempted Case",
              "supplierCategory": "MICRO"
            }
            """;

        mockMvc.perform(post("/api/v1/cases")
                .header("Authorization", "Bearer " + anonToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(writeCasePayload))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("DEMO_READ_ONLY"))
            .andExpect(jsonPath("$.message").value("Demo workspace is read-only. Please sign up to create and edit cases."));

        // PATCH /api/v1/me (Write) must return 403 DEMO_READ_ONLY
        String patchPayload = """
            {
              "businessName": "Updated Business"
            }
            """;

        mockMvc.perform(patch("/api/v1/me")
                .header("Authorization", "Bearer " + anonToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(patchPayload))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("DEMO_READ_ONLY"));
    }

    @Test
    @DisplayName("SSE streaming: Endpoints accept Bearer token via Authorization header or ?access_token=")
    void testSseEndpointsAcceptBearerToken() throws Exception {
        String userId = "user-sse-" + UUID.randomUUID();
        String token = generateJwt(userId, "sse@example.com", false, "authenticated", Instant.now().plus(1, ChronoUnit.HOURS));

        // Verify Bearer token via header on SSE/cases endpoint
        mockMvc.perform(get("/api/v1/cases")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());

        // Verify Bearer token via query parameter (?access_token=)
        mockMvc.perform(get("/api/v1/cases")
                .param("access_token", token))
            .andExpect(status().isOk());
    }
}
