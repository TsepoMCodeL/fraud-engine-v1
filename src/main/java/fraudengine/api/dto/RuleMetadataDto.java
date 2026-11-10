package fraudengine.api.dto;

import fraudengine.rules.FraudRule;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Configuration and runtime metadata for a registered fraud rule")
public record RuleMetadataDto(
        @Schema(description = "Rule identifier", example = "FR_1_BLACKLIST")
        String id,

        @Schema(description = "Human-readable rule name", example = "Global Blacklist Check")
        String name,

        @Schema(description = "Evaluation priority (lower value evaluates first)", example = "1")
        int priority,

        @Schema(description = "Rule category", example = "HARD_BLOCK")
        String category,

        @Schema(description = "Whether the rule is currently active in the pipeline", example = "true")
        boolean enabled,

        @Schema(description = "Whether this rule can short-circuit remaining evaluation when triggered", example = "true")
        boolean shortCircuit
) {
    public static RuleMetadataDto fromRule(FraudRule rule) {
        return new RuleMetadataDto(
                rule.id(),
                rule.name(),
                rule.priority(),
                rule.category().name(),
                rule.isEnabled(),
                rule.isShortCircuit()
        );
    }
}
