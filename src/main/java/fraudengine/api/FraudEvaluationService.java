package fraudengine.api;

import fraudengine.api.dto.BlacklistEntityRequestDto;
import fraudengine.api.dto.EvaluateTransactionRequestDto;
import fraudengine.api.dto.EvaluateTransactionResponseDto;
import fraudengine.api.dto.RuleMetadataDto;
import fraudengine.api.dto.TransactionEvaluationPageDto;
import fraudengine.api.dto.TransactionEvaluationResponseDto;
import fraudengine.api.dto.TransactionPageDto;
import fraudengine.config.FraudEngineProperties;
import fraudengine.domain.FraudDecision;
import fraudengine.domain.Transaction;
import fraudengine.engine.DefaultFraudEngine;
import fraudengine.engine.FraudEngine;
import fraudengine.enrichment.ContextEnricher;
import fraudengine.persistence.FraudEvaluationPersistenceService;
import fraudengine.persistence.FraudEvaluationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
public class FraudEvaluationService {

    private final FraudEngine fraudEngine;
    private final ContextEnricher contextEnricher;
    private final FraudEngineProperties properties;
    private final FraudEvaluationRepository evaluationRepository;
    private final FraudEvaluationPersistenceService evaluationPersistenceService;

    public FraudEvaluationService(
            FraudEngine fraudEngine,
            ContextEnricher contextEnricher,
            FraudEngineProperties properties,
            FraudEvaluationRepository evaluationRepository,
            FraudEvaluationPersistenceService evaluationPersistenceService
    ) {
        this.fraudEngine = Objects.requireNonNull(fraudEngine, "fraudEngine cannot be null");
        this.contextEnricher = Objects.requireNonNull(contextEnricher, "contextEnricher cannot be null");
        this.properties = Objects.requireNonNull(properties, "properties cannot be null");
        this.evaluationRepository = Objects.requireNonNull(evaluationRepository, "evaluationRepository cannot be null");
        this.evaluationPersistenceService = Objects.requireNonNull(
                evaluationPersistenceService,
                "evaluationPersistenceService cannot be null"
        );
    }

    public EvaluateTransactionResponseDto evaluate(EvaluateTransactionRequestDto request) {
        Objects.requireNonNull(request, "request cannot be null");
        if (request.customerProfile() != null) {
            throw new IllegalArgumentException(
                    "customerProfile cannot be supplied by clients; use a trusted server-side profile source");
        }
        if (request.timestamp() != null
                && request.timestamp().isAfter(Instant.now().plus(properties.getMaxFutureTimestampSkew()))) {
            throw new IllegalArgumentException("timestamp cannot be more than "
                    + properties.getMaxFutureTimestampSkew() + " in the future");
        }

        Transaction transaction = request.toDomain();
        FraudDecision decision = fraudEngine.evaluate(transaction);
        EvaluateTransactionResponseDto response = EvaluateTransactionResponseDto.fromDomain(decision);
        evaluationPersistenceService.save(transaction, response);

        return response;
    }

    @Transactional(readOnly = true)
    public TransactionEvaluationResponseDto getEvaluation(String evaluationId) {
        return evaluationRepository.findByEvaluationId(evaluationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evaluation not found"));
    }

    @Transactional(readOnly = true)
    public Transaction getTransaction(String transactionId) {
        return evaluationRepository.findTransactionById(transactionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));
    }

    @Transactional(readOnly = true)
    public TransactionPageDto getAccountTransactions(String accountId, int page, int size) {
        validatePage(page, size);
        return evaluationRepository.findTransactionsByAccountId(accountId, page, size);
    }

    @Transactional(readOnly = true)
    public TransactionEvaluationPageDto getTransactionEvaluations(String transactionId, int page, int size) {
        validatePage(page, size);
        return evaluationRepository.findByTransactionId(transactionId, page, size);
    }

    @Transactional(readOnly = true)
    public TransactionEvaluationPageDto getAccountEvaluations(String accountId, int page, int size) {
        validatePage(page, size);
        return evaluationRepository.findByAccountId(accountId, page, size);
    }

    public boolean isPersistenceAvailable() {
        return evaluationRepository.isAvailable();
    }

    private void validatePage(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be zero or greater");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("size must be between 1 and 100");
        }
    }

    public void addBlacklist(BlacklistEntityRequestDto request) {
        Objects.requireNonNull(request, "request cannot be null");
        switch (request.type()) {
            case ACCOUNT -> contextEnricher.addBlacklistedAccount(request.value());
            case IP -> contextEnricher.addBlacklistedIp(request.value());
            case DEVICE -> contextEnricher.addBlacklistedDevice(request.value());
        }
    }

    public List<RuleMetadataDto> getActiveRules() {
        if (fraudEngine instanceof DefaultFraudEngine defaultEngine) {
            return defaultEngine.getRegisteredRules().stream()
                    .map(RuleMetadataDto::fromRule)
                    .toList();
        }
        return List.of();
    }
}
