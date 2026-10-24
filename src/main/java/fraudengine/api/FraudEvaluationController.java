package fraudengine.api;

import fraudengine.api.dto.BlacklistEntityRequestDto;
import fraudengine.api.dto.EvaluateTransactionRequestDto;
import fraudengine.api.dto.EvaluateTransactionResponseDto;
import fraudengine.api.dto.RuleMetadataDto;
import fraudengine.api.dto.TransactionEvaluationPageDto;
import fraudengine.api.dto.TransactionEvaluationResponseDto;
import fraudengine.api.dto.TransactionPageDto;
import fraudengine.domain.Transaction;
import fraudengine.enrichment.ContextEnricher;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/fraud")
@Tag(name = "Fraud Engine", description = "Fraud evaluation and transaction history operations.")
public class FraudEvaluationController {

    private final FraudEvaluationService evaluationService;
    private final ContextEnricher contextEnricher;

    public FraudEvaluationController(FraudEvaluationService evaluationService, ContextEnricher contextEnricher) {
        this.evaluationService = evaluationService;
        this.contextEnricher = contextEnricher;
    }

    /**
     * Synchronous real-time fraud evaluation endpoint.
     * Evaluates incoming transaction across all configured heuristic, velocity,
     * geographic, and behavioral fraud rules.
     */
    @PostMapping("/evaluate")
    @Operation(summary = "Evaluate a transaction", description = "Evaluates a transaction and persists its evaluation.")
    public ResponseEntity<EvaluateTransactionResponseDto> evaluateTransaction(
            @Valid @RequestBody EvaluateTransactionRequestDto request
    ) {
        EvaluateTransactionResponseDto response = evaluationService.evaluate(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Administrative endpoint to register an account, IP, or device on the real-time blacklist.
     */
    @PostMapping("/blacklist")
    @Operation(summary = "Add a blacklist entry")
    @SecurityRequirement(name = "adminBasicAuth")
    public ResponseEntity<Map<String, Object>> addBlacklist(
            @Valid @RequestBody BlacklistEntityRequestDto request
    ) {
        evaluationService.addBlacklist(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "status", "SUCCESS",
                "message", String.format("Entity '%s' (%s) added to fraud blacklist", request.value(), request.type()),
                "reason", request.reason() != null ? request.reason() : "Manual operation"
        ));
    }

    /**
     * Introspection endpoint returning the active rule pipeline, categories, and priorities.
     */
    @GetMapping("/rules")
    @Operation(summary = "List active fraud rules")
    public ResponseEntity<List<RuleMetadataDto>> getActiveRules() {
        return ResponseEntity.ok(evaluationService.getActiveRules());
    }

    @GetMapping("/evaluations/{evaluationId}")
    @Operation(summary = "Get an evaluation by ID")
    @SecurityRequirement(name = "adminBasicAuth")
    public ResponseEntity<TransactionEvaluationResponseDto> getEvaluation(
            @PathVariable String evaluationId
    ) {
        return ResponseEntity.ok(evaluationService.getEvaluation(evaluationId));
    }

    @GetMapping("/transactions/{transactionId}")
    @Operation(summary = "Get a transaction by ID")
    @SecurityRequirement(name = "adminBasicAuth")
    public ResponseEntity<Transaction> getTransaction(@PathVariable String transactionId) {
        return ResponseEntity.ok(evaluationService.getTransaction(transactionId));
    }

    @GetMapping("/accounts/{accountId}/transactions")
    @Operation(summary = "List an account's transactions")
    @SecurityRequirement(name = "adminBasicAuth")
    public ResponseEntity<TransactionPageDto> getAccountTransactions(
            @PathVariable String accountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(evaluationService.getAccountTransactions(accountId, page, size));
    }

    @GetMapping("/transactions/{transactionId}/evaluations")
    @Operation(summary = "List evaluations for a transaction")
    @SecurityRequirement(name = "adminBasicAuth")
    public ResponseEntity<TransactionEvaluationPageDto> getTransactionEvaluations(
            @PathVariable String transactionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(evaluationService.getTransactionEvaluations(transactionId, page, size));
    }

    @GetMapping("/accounts/{accountId}/evaluations")
    @Operation(summary = "List evaluations for an account")
    @SecurityRequirement(name = "adminBasicAuth")
    public ResponseEntity<TransactionEvaluationPageDto> getAccountEvaluations(
            @PathVariable String accountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(evaluationService.getAccountEvaluations(accountId, page, size));
    }

    /**
     * Readiness/Liveness health check endpoint.
     */
    @GetMapping("/health")
    @Operation(summary = "Check fraud engine readiness")
    public ResponseEntity<Map<String, String>> health() {
        boolean available = contextEnricher.isAvailable() && evaluationService.isPersistenceAvailable();
        Map<String, String> body = Map.of(
                "status", available ? "UP" : "DOWN",
                "service", "fraud-detection-engine"
        );
        return ResponseEntity.status(available ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }
}
