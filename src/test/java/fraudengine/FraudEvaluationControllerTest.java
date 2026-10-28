package fraudengine;

import fraudengine.api.dto.BlacklistEntityRequestDto;
import fraudengine.api.dto.EvaluateTransactionRequestDto;
import fraudengine.api.dto.LocationDto;
import fraudengine.persistence.FraudEvaluationOutboxRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

@SpringBootTest(properties = {
        "spring.main.allow-bean-definition-overriding=true",
        "spring.datasource.url=jdbc:h2:mem:fraudengine;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "fraud.kafka.publisher-enabled=false",
        "fraud.engine.admin.username=test-admin",
        "fraud.engine.admin.password=test-password"
})
@AutoConfigureMockMvc
@org.springframework.context.annotation.Import(FraudEvaluationControllerTest.TestConfig.class)
class FraudEvaluationControllerTest {

    @org.springframework.boot.test.context.TestConfiguration
    static class TestConfig {
        @org.springframework.context.annotation.Bean
        public fraudengine.enrichment.ContextEnricher contextEnricher() {
            return new TestContextEnricher();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FraudEvaluationOutboxRepository outboxRepository;

    @Test
    @DisplayName("POST /api/v1/fraud/evaluate: Legitimate transaction returns 200 with APPROVE")
    void shouldApproveCleanTransaction() throws Exception {
        EvaluateTransactionRequestDto request = new EvaluateTransactionRequestDto(
                "TX-REST-100",
                "ACC-USER-1",
                BigDecimal.valueOf(45.50),
                "ZAR",
                Instant.now(),
                "MERCH-GROCERY-1",
                "5411",
                "192.0.2.1",
                new LocationDto(40.7128, -74.0060, "ZA"),
                "DEVICE-USER-1",
                Map.of("channel", "MOBILE_APP"),
                null
        );

        mockMvc.perform(post("/api/v1/fraud/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value("TX-REST-100"))
                .andExpect(jsonPath("$.action").value("APPROVE"))
                .andExpect(jsonPath("$.riskLevel").value("LOW"))
                .andExpect(jsonPath("$.riskScore").value(20))
                .andExpect(jsonPath("$.rulesEvaluatedCount", greaterThanOrEqualTo(5)));
    }

    @Test
    @DisplayName("POST evaluation persists its transaction and admin REST reads retrieve it")
    void shouldPersistAndRetrieveEvaluations() throws Exception {
        EvaluateTransactionRequestDto request = new EvaluateTransactionRequestDto(
                "TX-HISTORY-1",
                "ACC-HISTORY-1",
                BigDecimal.valueOf(45.50),
                "ZAR",
                Instant.now(),
                "MERCH-HISTORY-1",
                "5411",
                "192.0.2.10",
                new LocationDto(40.7128, -74.0060, "ZA"),
                "DEVICE-HISTORY-1",
                Map.of("channel", "WEB"),
                null
        );

        String evaluationJson = mockMvc.perform(post("/api/v1/fraud/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String evaluationId = objectMapper.readTree(evaluationJson).get("evaluationId").stringValue();
        var event = outboxRepository.claimPending(100, Instant.now().plusSeconds(60)).stream()
                .filter(message -> message.eventId().equals(evaluationId))
                .findFirst()
                .orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("ACC-HISTORY-1", event.accountId());
        org.junit.jupiter.api.Assertions.assertTrue(event.payload().contains(evaluationId));

        String authorization = basicAuthHeader("test-admin", "test-password");

        mockMvc.perform(get("/api/v1/fraud/transactions/TX-HISTORY-1/evaluations")
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].transaction.accountId").value("ACC-HISTORY-1"))
                .andExpect(jsonPath("$.content[0].transaction.amount").value(45.5))
                .andExpect(jsonPath("$.content[0].evaluation.evaluationId").value(evaluationId))
                .andExpect(jsonPath("$.content[0].evaluation.action").value("APPROVE"));

        mockMvc.perform(get("/api/v1/fraud/accounts/ACC-HISTORY-1/evaluations")
                        .param("page", "0")
                        .param("size", "1")
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));

        mockMvc.perform(get("/api/v1/fraud/evaluations/" + evaluationId)
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluation.evaluationId").value(evaluationId));

        mockMvc.perform(get("/api/v1/fraud/transactions/TX-HISTORY-1")
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("TX-HISTORY-1"))
                .andExpect(jsonPath("$.accountId").value("ACC-HISTORY-1"));

        mockMvc.perform(get("/api/v1/fraud/accounts/ACC-HISTORY-1/transactions")
                        .param("page", "0")
                        .param("size", "1")
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value("TX-HISTORY-1"));
    }

    @Test
    @DisplayName("Evaluation history endpoints require admin authentication")
    void shouldRequireAdminForEvaluationHistory() throws Exception {
        mockMvc.perform(get("/api/v1/fraud/accounts/ACC-HISTORY-1/evaluations"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/fraud/transactions/TX-HISTORY-1"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/fraud/accounts/ACC-HISTORY-1/transactions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Prometheus metrics endpoint requires admin authentication")
    void shouldProtectPrometheusMetricsEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/actuator/prometheus")
                        .header("Authorization", basicAuthHeader("test-admin", "test-password")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string(containsString("# HELP")));
    }

    @Test
    @DisplayName("Swagger UI documents fraud and actuator endpoints")
    void shouldExposeSwaggerDocumentationForAllEndpoints() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/fraud/evaluate'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/fraud/blacklist'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/fraud/transactions/{transactionId}'].get").exists())
                .andExpect(jsonPath("$.paths['/actuator/health'].get").exists())
                .andExpect(jsonPath("$.components.securitySchemes.adminBasicAuth.scheme").value("basic"));
    }

    @Test
    @DisplayName("POST /api/v1/fraud/evaluate: Rejects timestamps too far in the future")
    void shouldRejectFutureTransactionTimestamp() throws Exception {
        String request = """
                {
                    "transactionId": "TX-FUTURE-1",
                    "accountId": "ACC-USER-1",
                    "amount": 100.00,
                    "timestamp": "2035-01-01T00:00:00Z"
                }
                """;

        mockMvc.perform(post("/api/v1/fraud/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "timestamp cannot be more than PT5M in the future"));
    }

    @Test
    @DisplayName("POST /api/v1/fraud/evaluate: Rejects client-supplied customer profile")
    void shouldRejectClientSuppliedCustomerProfile() throws Exception {
        String request = """
                {
                    "transactionId": "TX-PROFILE-1",
                    "accountId": "ACC-USER-1",
                    "amount": 100.00,
                    "customerProfile": {
                        "averageTransactionAmount": 1000000.00,
                        "trustScore": 100,
                        "knownDeviceIds": ["DEVICE-USER-1"],
                        "isVip": true
                    }
                }
                """;

        mockMvc.perform(post("/api/v1/fraud/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "customerProfile cannot be supplied by clients; use a trusted server-side profile source"));
    }

    @Test
    @DisplayName("POST /api/v1/fraud/evaluate: Negative amount returns 400 Bad Request with validation details")
    void shouldRejectInvalidRequest() throws Exception {
        // Amount is negative and accountId is blank
        String malformedJson = """
                {
                    "transactionId": "TX-ERR-1",
                    "accountId": "",
                    "amount": -25.00
                }
                """;

        mockMvc.perform(post("/api/v1/fraud/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.details", hasItem(containsString("accountId is required"))))
                .andExpect(jsonPath("$.details", hasItem(containsString("amount must be strictly positive"))));
    }

    @Test
    @DisplayName("POST /api/v1/fraud/evaluate: Rejects oversized identifiers")
    void shouldRejectOversizedTransactionIdentifier() throws Exception {
        String request = objectMapper.writeValueAsString(Map.of(
                "transactionId", "T".repeat(256),
                "accountId", "ACC-USER-1",
                "amount", 100.00
        ));

        mockMvc.perform(post("/api/v1/fraud/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details", hasItem(
                        containsString("transactionId must be at most 255 characters"))));
    }

    @Test
    @DisplayName("POST /api/v1/fraud/evaluate: Rejects oversized metadata")
    void shouldRejectOversizedMetadata() throws Exception {
        Map<String, String> metadata = new HashMap<>();
        for (int i = 0; i < 21; i++) {
            metadata.put("key" + i, "value");
        }
        String request = objectMapper.writeValueAsString(Map.of(
                "transactionId", "TX-METADATA-1",
                "accountId", "ACC-USER-1",
                "amount", 100.00,
                "metadata", metadata
        ));

        mockMvc.perform(post("/api/v1/fraud/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details", hasItem(
                        containsString("metadata must contain at most 20 entries"))));
    }

    @Test
    @DisplayName("POST /api/v1/fraud/evaluate: Rejects oversized metadata keys and values")
    void shouldRejectOversizedMetadataKeyAndValue() throws Exception {
        for (Map<String, String> metadata : List.of(
                Map.of("K".repeat(65), "value"),
                Map.of("key", "V".repeat(257))
        )) {
            String request = objectMapper.writeValueAsString(Map.of(
                    "transactionId", "TX-METADATA-2",
                    "accountId", "ACC-USER-1",
                    "amount", 100.00,
                    "metadata", metadata
            ));

            mockMvc.perform(post("/api/v1/fraud/evaluate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details", hasItem(
                            containsString("size must be between 0 and"))));
        }
    }

    @Test
    @DisplayName("POST /api/v1/fraud/blacklist: Rejects oversized values")
    void shouldRejectOversizedBlacklistValue() throws Exception {
        BlacklistEntityRequestDto request = new BlacklistEntityRequestDto(
                "A".repeat(256),
                BlacklistEntityRequestDto.BlacklistType.ACCOUNT,
                null
        );

        mockMvc.perform(post("/api/v1/fraud/blacklist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("Authorization", basicAuthHeader("test-admin", "test-password")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details", hasItem(
                        containsString("value must be at most 255 characters"))));
    }

    @Test
    @DisplayName("POST /api/v1/fraud/blacklist: Rejects oversized reason")
    void shouldRejectOversizedBlacklistReason() throws Exception {
        BlacklistEntityRequestDto request = new BlacklistEntityRequestDto(
                "ACC-USER-1",
                BlacklistEntityRequestDto.BlacklistType.ACCOUNT,
                "R".repeat(513)
        );

        mockMvc.perform(post("/api/v1/fraud/blacklist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("Authorization", basicAuthHeader("test-admin", "test-password")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details", hasItem(
                        containsString("reason must be at most 512 characters"))));
    }

    @Test
    @DisplayName("POST /api/v1/fraud/blacklist and evaluate: Blocks blacklisted account")
    void shouldBlockBlacklistedAccount() throws Exception {
        String blacklistedAccountId = "ACC-MALICIOUS-99";

        // 1. Blacklist the account
        BlacklistEntityRequestDto blacklistRequest = new BlacklistEntityRequestDto(
                blacklistedAccountId,
                BlacklistEntityRequestDto.BlacklistType.ACCOUNT,
                "Card stolen alert"
        );

        mockMvc.perform(post("/api/v1/fraud/blacklist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blacklistRequest))
                        .header("Authorization", basicAuthHeader("test-admin", "test-password")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        // 2. Submit transaction for blacklisted account
        EvaluateTransactionRequestDto txRequest = new EvaluateTransactionRequestDto(
                "TX-FRAUD-1",
                blacklistedAccountId,
                BigDecimal.valueOf(250.00),
                "ZAR",
                Instant.now(),
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        mockMvc.perform(post("/api/v1/fraud/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(txRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("DECLINE"))
                .andExpect(jsonPath("$.riskLevel").value("CRITICAL"))
                .andExpect(jsonPath("$.riskScore").value(100))
                .andExpect(jsonPath("$.primaryReason", containsString("Account is blacklisted")));
    }

    @Test
    @DisplayName("POST /api/v1/fraud/blacklist: Requires admin authentication")
    void shouldRejectUnauthenticatedBlacklistRequest() throws Exception {
        BlacklistEntityRequestDto request = new BlacklistEntityRequestDto(
                "ACC-UNAUTHORIZED",
                BlacklistEntityRequestDto.BlacklistType.ACCOUNT,
                null
        );

        mockMvc.perform(post("/api/v1/fraud/blacklist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    private String basicAuthHeader(String username, String password) {
        String credentials = username + ":" + password;
        return "Basic " + java.util.Base64.getEncoder()
                .encodeToString(credentials.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("GET /api/v1/fraud/rules: Returns ordered registered rules pipeline")
    void shouldReturnActiveRules() throws Exception {
        mockMvc.perform(get("/api/v1/fraud/rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(5))))
                .andExpect(jsonPath("$[0].priority", is(1))) // BlacklistRule is first
                .andExpect(jsonPath("$[0].shortCircuit", is(true)));
    }

    @Test
    @DisplayName("GET /api/v1/fraud/health: Returns UP status")
    void shouldReturnHealthStatus() throws Exception {
        mockMvc.perform(get("/api/v1/fraud/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("GET /api/v1/fraud/evaluations/{id}: Non-existent evaluation returns 404 Not Found")
    void shouldReturnNotFoundForMissingEvaluation() throws Exception {
        mockMvc.perform(get("/api/v1/fraud/evaluations/NON-EXISTENT-EVAL-ID")
                        .header("Authorization", basicAuthHeader("test-admin", "test-password")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Evaluation not found"));
    }

    @Test
    @DisplayName("GET /api/v1/fraud/transactions/{id}: Non-existent transaction returns 404 Not Found")
    void shouldReturnNotFoundForMissingTransaction() throws Exception {
        mockMvc.perform(get("/api/v1/fraud/transactions/NON-EXISTENT-TX-ID")
                        .header("Authorization", basicAuthHeader("test-admin", "test-password")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Transaction not found"));
    }
}
