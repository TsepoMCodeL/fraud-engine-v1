package fraudengine.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import fraudengine.domain.Transaction;

public record EvaluateTransactionRequestDto(
        @NotBlank(message = "transactionId is required")
        String transactionId,

        @NotBlank(message = "accountId is required")
        String accountId,

        @NotNull(message = "amount is required")
        @Positive(message = "amount must be strictly positive")
        BigDecimal amount,

        String currency,
        Instant timestamp,
        String merchantId,
        String merchantCategoryCode,
        String ipAddress,

        @Valid
        LocationDto location,

        String deviceId,
        Map<String, String> metadata,
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
