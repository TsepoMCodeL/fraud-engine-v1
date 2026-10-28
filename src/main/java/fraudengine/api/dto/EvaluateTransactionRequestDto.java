package fraudengine.api.dto;

import fraudengine.domain.Transaction;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

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

        @Size(max = 3, message = "currency must be at most 3 characters")
        String currency,
        Instant timestamp,
        @Size(max = 255, message = "merchantId must be at most 255 characters")
        String merchantId,
        @Size(max = 4, message = "merchantCategoryCode must be at most 4 characters")
        String merchantCategoryCode,
        @Size(max = 45, message = "ipAddress must be at most 45 characters")
        String ipAddress,

        @Valid
        LocationDto location,

        @Size(max = 255, message = "deviceId must be at most 255 characters")
        String deviceId,
        @Size(max = 20, message = "metadata must contain at most 20 entries")
        Map<@NotBlank @Size(max = 64) String, @NotNull @Size(max = 256) String> metadata,
        CustomerProfileDto customerProfile
) {
    public Transaction toDomain() {
        return Transaction.builder()
                .id(transactionId)
                .accountId(accountId)
                .amount(amount)
                .currency(currency != null ? currency : "ZAR")
                .timestamp(timestamp != null ? timestamp : Instant.now())
                .merchantId(merchantId)
                .merchantCategoryCode(merchantCategoryCode)
                .ipAddress(ipAddress)
                .location(location != null ? location.toDomain() : null)
                .deviceId(deviceId)
                .metadata(metadata)
                .build();
    }
}
