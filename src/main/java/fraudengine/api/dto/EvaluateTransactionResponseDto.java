package fraudengine.api.dto;

import java.time.Instant;
import java.util.List;

import fraudengine.domain.Action;
import fraudengine.domain.FraudDecision;
import fraudengine.domain.RiskLevel;

public record EvaluateTransactionResponseDto(
        String evaluationId,
        String transactionId,
        String accountId,
        Action action,
        RiskLevel riskLevel,
        int riskScore,
        String primaryReason,
        double totalExecutionTimeMs,
        int rulesEvaluatedCount,
        List<TriggeredRuleResponseDto> triggeredRules,
        List<RuleEvaluationSummaryResponseDto> allEvaluatedRules,
        Instant evaluatedAt
) {
    public static EvaluateTransactionResponseDto fromDomain(FraudDecision decision) {
        List<TriggeredRuleResponseDto> triggered = decision.triggeredRules().stream()
                .map(TriggeredRuleResponseDto::fromDomain)
                .toList();

        List<RuleEvaluationSummaryResponseDto> allEvaluated = decision.allEvaluatedRules().stream()
                .map(RuleEvaluationSummaryResponseDto::fromDomain)
                .toList();

        return new EvaluateTransactionResponseDto(
                decision.evaluationId(),
                decision.transactionId(),
                decision.accountId(),
                decision.action(),
                decision.riskLevel(),
                decision.aggregateRiskScore(),
                decision.primaryReason(),
                decision.totalExecutionTimeMs(),
                decision.allEvaluatedRules().size(),
                triggered,
                allEvaluated,
                decision.evaluatedAt()
        );
    }
}
