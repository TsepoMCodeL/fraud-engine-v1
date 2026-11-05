package fraudengine.api.dto;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;

import fraudengine.domain.CustomerProfile;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record CustomerProfileDto(
        @Nullable Instant accountCreatedAt,
        @Nullable @PositiveOrZero BigDecimal averageTransactionAmount,
        @Nullable @Min(0) @Max(100) Integer trustScore,
        @Nullable Set<String> knownDeviceIds,
        @Nullable Boolean isVip
) {

    public CustomerProfile toDomain(String accountId, Clock clock) {
    return new CustomerProfile(
            accountId,
            accountCreatedAt != null ? accountCreatedAt : clock.instant(),
            averageTransactionAmount != null
                    ? averageTransactionAmount
                    : CustomerProfile.DEFAULT_AVERAGE_TRANSACTION_AMOUNT,
            trustScore != null ? trustScore : CustomerProfile.DEFAULT_TRUST_SCORE,
            knownDeviceIds != null ? knownDeviceIds : Set.of(),
            Boolean.TRUE.equals(isVip));
    }
}