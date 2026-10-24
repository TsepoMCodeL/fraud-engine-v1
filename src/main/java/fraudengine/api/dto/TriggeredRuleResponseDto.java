package fraudengine.api.dto;

import java.util.Map;

import fraudengine.domain.RuleResult;

public record TriggeredRuleResponseDto(
        String ruleId,
        String ruleName,
        String category,
        int riskScore,
        String recommendedAction,
        String reason,
        Map<String, Object> attributes
) {
    public static TriggeredRuleResponseDto fromDomain(RuleResult result) {
        return new TriggeredRuleResponseDto(
                result.ruleId(),
                result.ruleName(),
                result.category().name(),
                result.riskScore(),
                result.recommendedAction().name(),
                result.reason(),
                result.attributes()
        );
    }
}
