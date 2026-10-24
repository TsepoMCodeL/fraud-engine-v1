package fraudengine.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

import fraudengine.domain.CustomerProfile;

public record CustomerProfileDto(
        Instant accountCreatedAt,
        BigDecimal averageTransactionAmount,
        Integer trustScore,
        Set<String> knownDeviceIds,
        Boolean isVip
) {
    public CustomerProfile toDomain(String accountId) {
        return new CustomerProfile(
                accountId,
                accountCreatedAt != null ? accountCreatedAt : Instant.now(),
                averageTransactionAmount != null ? averageTransactionAmount : BigDecimal.valueOf(100.0),
                trustScore != null ? trustScore : 50,
                knownDeviceIds != null ? knownDeviceIds : Set.of(),
                Boolean.TRUE.equals(isVip)
        );
    }
}

