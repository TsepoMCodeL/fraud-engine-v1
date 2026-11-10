package fraudengine.api.dto;

import fraudengine.domain.RuleResult;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

@Schema(description = "Execution summary for an evaluated fraud detection rule")
public record RuleEvaluationSummaryResponseDto(
        @Schema(description = "Rule identifier", example = "FR_1_BLACKLIST")
        String ruleId,

        @Schema(description = "Human-readable rule name", example = "Global Blacklist Check")
        String ruleName,

        @Schema(description = "Rule classification category", example = "HARD_BLOCK")
        String category,

        @Schema(description = "Whether the rule condition was met (triggered)", example = "true")
        boolean triggered,

        @Schema(description = "Calculated risk score increment", example = "100")
        int riskScore,

        @Schema(description = "Action recommended by this rule", example = "DECLINE")
        String action,

        @Schema(description = "Diagnostic reason for trigger or pass", example = "Terminal entity match: Account is blacklisted")
        @Nullable String reason,

        @Schema(description = "Execution latency in milliseconds", example = "0.245")
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
