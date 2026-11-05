package fraudengine.api.dto;

import fraudengine.domain.Transaction;
import jakarta.validation.Valid;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@NullMarked
public record EvaluateTransactionRequestDto(
        @NotBlank(message = "transactionId is required")
        @Size(max = 255, message = "transactionId must be at most 255 characters")
        String transactionId,

        @NotBlank(message = "accountId is required")
        @Size(max = 255, message = "accountId must be at most 255 characters")
        String accountId,

        @NotNull(message = "amount is required")
        @Positive(message = "amount must be strictly positive")
        BigDecimal amount,

        @Nullable
        @Size(max = 3, message = "currency must be at most 3 characters")
        String currency,
        @Nullable Instant timestamp,
        @Nullable
        @Size(max = 255, message = "merchantId must be at most 255 characters")
        String merchantId,
        @Nullable
        @Size(max = 4, message = "merchantCategoryCode must be at most 4 characters")
        String merchantCategoryCode,
        @Nullable
        @Size(max = 45, message = "ipAddress must be at most 45 characters")
        String ipAddress,

        @Valid
        @Nullable
        LocationDto location,

        @Nullable
        @Size(max = 255, message = "deviceId must be at most 255 characters")
        String deviceId,
        @Nullable
        @Size(max = 20, message = "metadata must contain at most 20 entries")
        Map<@NotBlank @Size(max = 64) String, @NotNull @Size(max = 256) String> metadata,
        @Nullable CustomerProfileDto customerProfile
) {
    public Transaction toDomain() {
        var effectiveCurrency = currency != null ? currency : "ZAR";
        var effectiveTimestamp = timestamp != null ? timestamp : Instant.now();
        var effectiveLocation = location != null ? location.toDomain() : null;

        return Transaction.builder()
                .id(transactionId)
                .accountId(accountId)
                .amount(amount)
                .currency(effectiveCurrency)
                .timestamp(effectiveTimestamp)
                .merchantId(merchantId)
                .merchantCategoryCode(merchantCategoryCode)
                .ipAddress(ipAddress)
                .location(effectiveLocation)
                .deviceId(deviceId)
                .metadata(metadata)
                .build();
    }
}
