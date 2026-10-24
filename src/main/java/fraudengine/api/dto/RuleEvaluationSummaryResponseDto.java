package fraudengine.api.dto;

import fraudengine.domain.RuleResult;

public record RuleEvaluationSummaryResponseDto(
        String ruleId,
        String ruleName,
        String category,
        boolean triggered,
        int riskScore,
        String action,
        String reason,
        double executionTimeMs
) {
    public static RuleEvaluationSummaryResponseDto fromDomain(RuleResult result) {
        return new RuleEvaluationSummaryResponseDto(
                result.ruleId(),
                result.ruleName(),
                result.category().name(),
                result.triggered(),
                result.riskScore(),
                result.recommendedAction().name(),
                result.reason(),
                result.executionTimeNanos() / 1_000_000.0
        );
    }
}
