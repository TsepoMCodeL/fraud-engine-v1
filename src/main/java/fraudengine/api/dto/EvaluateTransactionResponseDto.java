package fraudengine.api.dto;

import java.time.Instant;
import java.util.List;

import fraudengine.domain.Action;
import fraudengine.domain.FraudDecision;
import fraudengine.domain.RiskLevel;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Fraud evaluation outcome and rule execution diagnostic results")
public record EvaluateTransactionResponseDto(
        @Schema(description = "Unique evaluation record identifier", example = "4efadda0-bd3c-4080-9d28-2af8cfc4d848")
        String evaluationId,

        @Schema(description = "Transaction identifier evaluated", example = "TX-984321")
        String transactionId,

        @Schema(description = "Customer account identifier", example = "ACC-100234")
        String accountId,

        @Schema(description = "Final fraud action decision (APPROVE, REVIEW, DECLINE)", example = "APPROVE")
        Action action,

        @Schema(description = "Categorized risk tier (LOW, MEDIUM, HIGH, CRITICAL)", example = "LOW")
        RiskLevel riskLevel,

        @Schema(description = "Aggregate composite risk score [0-100]", example = "20")
        int riskScore,

        @Schema(description = "Dominant rationale explaining the decision", example = "Evaluation complete")
        String primaryReason,

        @Schema(description = "Total engine evaluation latency in milliseconds", example = "0.452")
        double totalExecutionTimeMs,

        @Schema(description = "Total number of rules evaluated before decision", example = "6")
        int rulesEvaluatedCount,

        @Schema(description = "List of rules that triggered during evaluation")
        List<TriggeredRuleResponseDto> triggeredRules,

        @Schema(description = "Complete audit of all evaluated rules and execution latencies")
        List<RuleEvaluationSummaryResponseDto> allEvaluatedRules,

        @Schema(description = "Timestamp when evaluation concluded", example = "2026-11-05T14:30:00.123Z")
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
