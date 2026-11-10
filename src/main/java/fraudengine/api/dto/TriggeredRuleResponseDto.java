package fraudengine.api.dto;

import fraudengine.domain.RuleResult;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.util.Map;

@Schema(description = "Details of an individual fraud rule triggered during evaluation")
public record TriggeredRuleResponseDto(
        @Schema(description = "Triggered rule identifier", example = "FR_4_AMOUNT_ANOMALY")
        String ruleId,

        @Schema(description = "Human-readable rule name", example = "Amount Baseline Deviation Check")
        String ruleName,

        @Schema(description = "Rule classification category", example = "BEHAVIORAL")
        String category,

        @Schema(description = "Calculated risk score increment", example = "70")
        int riskScore,

        @Schema(description = "Action recommended by this rule", example = "REVIEW")
        String recommendedAction,

        @Schema(description = "Diagnostic reason for trigger", example = "Transaction amount 50000.0 is 500.0x customer's average (100.0)")
        @Nullable String reason,

        @Schema(description = "Key-value contextual features and thresholds captured at trigger time")
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
