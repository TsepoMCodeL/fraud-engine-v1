package fraudengine.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable transaction event submitted to the fraud engine for evaluation.
 */
public record Transaction(
        String id,
        String accountId,
        BigDecimal amount,
        String currency,
        Instant timestamp,
        String merchantId,
        String merchantCategoryCode,
        String ipAddress,
        GeoLocation location,
        String deviceId,
        Map<String, String> metadata
) {
    public Transaction {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(accountId, "accountId cannot be null");
        Objects.requireNonNull(amount, "amount cannot be null");
        Objects.requireNonNull(currency, "currency cannot be null");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");

        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Transaction amount cannot be negative");
        }

        metadata = metadata == null ? Collections.emptyMap() : Collections.unmodifiableMap(metadata);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String accountId;
        private BigDecimal amount;
        private String currency = "ZAR";
        private Instant timestamp = Instant.now();
        private String merchantId;
        private String merchantCategoryCode;
        private String ipAddress;
        private GeoLocation location;
        private String deviceId;
        private Map<String, String> metadata;

        public Builder id(String id) { this.id = id; return this; }
        public Builder accountId(String accountId) { this.accountId = accountId; return this; }
        public Builder amount(BigDecimal amount) { this.amount = amount; return this; }
        public Builder amount(double amount) { this.amount = BigDecimal.valueOf(amount); return this; }
        public Builder currency(String currency) { this.currency = currency; return this; }
        public Builder timestamp(Instant timestamp) { this.timestamp = timestamp; return this; }
        public Builder merchantId(String merchantId) { this.merchantId = merchantId; return this; }
        public Builder merchantCategoryCode(String mcc) { this.merchantCategoryCode = mcc; return this; }
        public Builder ipAddress(String ipAddress) { this.ipAddress = ipAddress; return this; }
        public Builder location(GeoLocation location) { this.location = location; return this; }
        public Builder deviceId(String deviceId) { this.deviceId = deviceId; return this; }
        public Builder metadata(Map<String, String> metadata) { this.metadata = metadata; return this; }

        public Transaction build() {
            return new Transaction(id, accountId, amount, currency, timestamp,
                    merchantId, merchantCategoryCode, ipAddress, location, deviceId, metadata);
        }
    }
}
