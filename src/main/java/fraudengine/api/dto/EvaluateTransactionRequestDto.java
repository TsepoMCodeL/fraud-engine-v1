package fraudengine.api.dto;

import fraudengine.domain.Transaction;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Schema(description = "Payload for submitting a financial transaction for real-time fraud evaluation")
public record EvaluateTransactionRequestDto(
        @Schema(description = "Unique transaction identifier", example = "TX-984321")
        @NotBlank(message = "transactionId is required")
        @Size(max = 255, message = "transactionId must be at most 255 characters")
        String transactionId,

        @Schema(description = "Customer or source account identifier", example = "ACC-100234")
        @NotBlank(message = "accountId is required")
        @Size(max = 255, message = "accountId must be at most 255 characters")
        String accountId,

        @Schema(description = "Transaction monetary amount", example = "250.75")
        @NotNull(message = "amount is required")
        @Positive(message = "amount must be strictly positive")
        BigDecimal amount,

        @Schema(description = "Three-letter ISO 4217 currency code", example = "ZAR")
        @Nullable
        @Size(max = 3, message = "currency must be at most 3 characters")
        String currency,

        @Schema(description = "Transaction timestamp in ISO 8601 UTC format", example = "2026-11-05T14:30:00Z")
        @Nullable Instant timestamp,

        @Schema(description = "Identifier of receiving merchant", example = "MERCH-AMAZON-01")
        @Nullable
        @Size(max = 255, message = "merchantId must be at most 255 characters")
        String merchantId,

        @Schema(description = "Four-digit Merchant Category Code (MCC)", example = "5411")
        @Nullable
        @Size(max = 4, message = "merchantCategoryCode must be at most 4 characters")
        String merchantCategoryCode,

        @Schema(description = "Originating IPv4 or IPv6 client address", example = "192.0.2.1")
        @Nullable
        @Size(max = 45, message = "ipAddress must be at most 45 characters")
        String ipAddress,

        @Schema(description = "Originating geographical coordinates")
        @Valid
        @Nullable
        LocationDto location,

        @Schema(description = "Originating device fingerprint or hardware ID", example = "DEV-IOS-A934B")
        @Nullable
        @Size(max = 255, message = "deviceId must be at most 255 characters")
        String deviceId,

        @Schema(description = "Additional key-value metadata attributes (maximum 20 entries)")
        @Nullable
        @Size(max = 20, message = "metadata must contain at most 20 entries")
        Map<@NotBlank @Size(max = 64) String, @NotNull @Size(max = 256) String> metadata,

        @Schema(description = "Client-supplied profile (must be null; profile is resolved server-side)")
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
