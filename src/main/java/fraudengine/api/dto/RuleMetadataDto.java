package fraudengine.api.dto;

import fraudengine.rules.FraudRule;

public record RuleMetadataDto(
        String id,
        String name,
        int priority,
        String category,
        boolean enabled,
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

