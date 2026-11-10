package fraudengine.api.dto;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;

import fraudengine.domain.CustomerProfile;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import org.jspecify.annotations.Nullable;

@Schema(description = "Customer profile baseline attributes for anomaly detection")
public record CustomerProfileDto(
        @Schema(description = "Account registration timestamp", example = "2024-01-15T08:30:00Z")
        @Nullable Instant accountCreatedAt,

        @Schema(description = "Historical average transaction amount", example = "150.00")
        @Nullable @PositiveOrZero BigDecimal averageTransactionAmount,

        @Schema(description = "Customer trust score [0-100]", example = "85")
        @Nullable @Min(0) @Max(100) Integer trustScore,

        @Schema(description = "Set of known device identifiers associated with the account", example = "[\"DEV-1\", \"DEV-2\"]")
        @Nullable Set<String> knownDeviceIds,

        @Schema(description = "Whether customer has VIP status", example = "false")
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