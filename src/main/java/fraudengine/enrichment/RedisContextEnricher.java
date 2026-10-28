package fraudengine.enrichment;

import fraudengine.domain.CustomerProfile;
import fraudengine.domain.EnrichedContext;
import fraudengine.domain.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.ObjectWriter;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Enterprise Redis implementation of ContextEnricher.
 * Uses Redis Sorted Sets (ZSET) for sliding-window velocity tracking and
 * Redis Sets (SET) for blacklist queries.
 * Enrichment is read-only; pruning occurs during transaction recording.
 */
public class RedisContextEnricher implements ContextEnricher {

    private static final Logger log = LoggerFactory.getLogger(RedisContextEnricher.class);
    private static final String DEFAULT_KEY_PREFIX = "fraud:";
    private static final Duration DEFAULT_VELOCITY_TTL = Duration.ofHours(48);
    private static final Duration ONE_MINUTE = Duration.ofMinutes(1);
    private static final Duration TEN_MINUTES = Duration.ofMinutes(10);
    private static final Duration ONE_DAY = Duration.ofHours(24);
    private static final String VELOCITY_KEY_SUFFIX = "velocity:account:";
    private static final String BLACKLIST_ACCOUNTS_KEY = "blacklist:accounts";
    private static final String BLACKLIST_IPS_KEY = "blacklist:ips";
    private static final String BLACKLIST_DEVICES_KEY = "blacklist:devices";

    private final RedisOperations<String, String> redisOperations;
    private final ObjectWriter transactionWriter;
    private final ObjectReader transactionReader;
    private final String keyPrefix;
    private final Duration velocityTtl;

    public RedisContextEnricher(
            RedisOperations<String, String> redisOperations,
            ObjectMapper objectMapper,
            String keyPrefix,
            Duration velocityTtl
    ) {
        this.redisOperations = Objects.requireNonNull(redisOperations, "redisOperations cannot be null");
        this.keyPrefix = (keyPrefix != null && !keyPrefix.isBlank()) ? keyPrefix : DEFAULT_KEY_PREFIX;
        this.velocityTtl = velocityTtl != null ? velocityTtl : DEFAULT_VELOCITY_TTL;

        ObjectMapper mapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.transactionWriter = mapper.writerFor(Transaction.class);
        this.transactionReader = mapper.readerFor(Transaction.class);
    }

    public RedisContextEnricher(RedisOperations<String, String> redisOperations) {
        this(redisOperations, null, DEFAULT_KEY_PREFIX, DEFAULT_VELOCITY_TTL);
    }

    private String velocityKey(String accountId) {
        return keyPrefix + VELOCITY_KEY_SUFFIX + accountId;
    }

    private String blacklistAccountsKey() {
        return keyPrefix + BLACKLIST_ACCOUNTS_KEY;
    }

    private String blacklistIpsKey() {
        return keyPrefix + BLACKLIST_IPS_KEY;
    }

    private String blacklistDevicesKey() {
        return keyPrefix + BLACKLIST_DEVICES_KEY;
    }

    @Override
    public EnrichedContext enrich(Transaction transaction, CustomerProfile profile) {
        try {
            String vKey = velocityKey(transaction.accountId());
            Instant now = transaction.timestamp();
            double cutoff1mMs = now.minus(ONE_MINUTE).toEpochMilli();
            double cutoff10mMs = now.minus(TEN_MINUTES).toEpochMilli();
            double cutoff24hMs = now.minus(ONE_DAY).toEpochMilli();
            double eventTimeMs = now.toEpochMilli();

            // Pure read-only pipeline: fetch counts, the latest transaction, and blacklist matches.
            List<Object> pipelineResults = redisOperations.executePipelined(new SessionCallback<>() {
                @Override
                @SuppressWarnings("unchecked")
                public <K, V> Object execute(RedisOperations<K, V> operations) throws DataAccessException {
                    var zOps = (org.springframework.data.redis.core.ZSetOperations<String, String>) operations.opsForZSet();
                    var sOps = (org.springframework.data.redis.core.SetOperations<String, String>) operations.opsForSet();

                    // 1. Count transactions in last 1 minute
                    zOps.count(vKey, cutoff1mMs, eventTimeMs);

                    // 2. Count transactions in last 10 minutes
                    zOps.count(vKey, cutoff10mMs, eventTimeMs);

                    // 3. Count transactions in last 24 hours
                    zOps.count(vKey, cutoff24hMs, eventTimeMs);

                    // 4. Fetch only the latest transaction needed by velocity rules
                    zOps.reverseRangeByScore(vKey, cutoff24hMs, eventTimeMs, 0, 1);

                    // 5. Check account blacklist
                    sOps.isMember(blacklistAccountsKey(), transaction.accountId());

                    // 6. Check IP blacklist
                    if (transaction.ipAddress() != null) {
                        sOps.isMember(blacklistIpsKey(), transaction.ipAddress());
                    }

                    // 7. Check Device blacklist
                    if (transaction.deviceId() != null) {
                        sOps.isMember(blacklistDevicesKey(), transaction.deviceId());
                    }

                    return null;
                }
            });

            int expectedResultCount = 5
                    + (transaction.ipAddress() == null ? 0 : 1)
                    + (transaction.deviceId() == null ? 0 : 1);
            if (pipelineResults == null || pipelineResults.size() != expectedResultCount) {
                throw new IllegalStateException("Redis returned an incomplete enrichment response");
            }

            // Parse pipeline response objects
            int count1m = toInt(pipelineResults.get(0));
            int count10m = toInt(pipelineResults.get(1));
            int count24h = toInt(pipelineResults.get(2));

            Optional<Transaction> lastTx = Optional.empty();

            Object latestTransactions = pipelineResults.get(3);
            if (latestTransactions instanceof Set<?> transactions && !transactions.isEmpty()) {
                Object latestTransaction = transactions.iterator().next();
                if (latestTransaction instanceof String transactionJson) {
                    try {
                        lastTx = Optional.of(transactionReader.readValue(transactionJson));
                    } catch (Exception ex) {
                        throw new IllegalStateException("Redis returned an invalid transaction record", ex);
                    }
                } else {
                    throw new IllegalStateException("Redis returned a transaction record in an unexpected format");
                }
            }

            boolean isAccountBlacklisted = toBoolean(pipelineResults.get(4));
            int nextIdx = 5;
            boolean isIpBlacklisted = false;
            if (transaction.ipAddress() != null) {
                isIpBlacklisted = toBoolean(pipelineResults.get(nextIdx++));
            }
            boolean isDeviceBlacklisted = false;
            if (transaction.deviceId() != null) {
                isDeviceBlacklisted = toBoolean(pipelineResults.get(nextIdx));
            }

            boolean isDeviceRecognized = transaction.deviceId() != null &&
                    profile.knownDeviceIds().contains(transaction.deviceId());

            return new EnrichedContext(
                    count1m,
                    count10m,
                    count24h,
                    lastTx,
                    isDeviceRecognized,
                    isAccountBlacklisted,
                    isIpBlacklisted,
                    isDeviceBlacklisted,
                    Map.of("dataStore", "redis", "keyPrefix", keyPrefix)
            );

        } catch (Exception ex) {
            log.error("Redis enrichment failed for account {}: {}", transaction.accountId(), ex.getMessage(), ex);
            throw new IllegalStateException("Redis enrichment is unavailable", ex);
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            String ping = redisOperations.execute((RedisCallback<String>) RedisConnection::ping);
            return "PONG".equalsIgnoreCase(ping);
        } catch (RuntimeException ex) {
            log.warn("Redis readiness check failed", ex);
            return false;
        }
    }

    @Override
    public void recordTransaction(Transaction transaction) {
        try {
            String vKey = velocityKey(transaction.accountId());
            String json = transactionWriter.writeValueAsString(transaction);
            double score = transaction.timestamp().toEpochMilli();
            double cutoff24hMs = transaction.timestamp().minus(ONE_DAY).toEpochMilli();

            redisOperations.executePipelined(new SessionCallback<>() {
                @Override
                @SuppressWarnings("unchecked")
                public <K, V> Object execute(RedisOperations<K, V> operations) throws DataAccessException {
                    var zOps = (org.springframework.data.redis.core.ZSetOperations<String, String>) operations.opsForZSet();
                    var redis = (RedisOperations<String, String>) operations;
                    // Prune expired entries older than 24 hours during recording
                    zOps.removeRangeByScore(vKey, Double.NEGATIVE_INFINITY, cutoff24hMs);
                    zOps.add(vKey, json, score);
                    redis.expire(vKey, velocityTtl);
                    return null;
                }
            });
        } catch (Exception ex) {
            log.error("Failed to record transaction to Redis for account {}: {}", transaction.accountId(), ex.getMessage(), ex);
            throw new IllegalStateException("Failed to record transaction in Redis", ex);
        }
    }

    @Override
    public void addBlacklistedAccount(String accountId) {
        try {
            redisOperations.opsForSet().add(blacklistAccountsKey(), accountId);
        } catch (Exception ex) {
            log.error("Failed to blacklist account in Redis: {}", accountId, ex);
            throw new IllegalStateException("Failed to blacklist account", ex);
        }
    }

    @Override
    public void addBlacklistedIp(String ipAddress) {
        try {
            redisOperations.opsForSet().add(blacklistIpsKey(), ipAddress);
        } catch (Exception ex) {
            log.error("Failed to blacklist IP in Redis: {}", ipAddress, ex);
            throw new IllegalStateException("Failed to blacklist IP", ex);
        }
    }

    @Override
    public void addBlacklistedDevice(String deviceId) {
        try {
            redisOperations.opsForSet().add(blacklistDevicesKey(), deviceId);
        } catch (Exception ex) {
            log.error("Failed to blacklist device in Redis: {}", deviceId, ex);
            throw new IllegalStateException("Failed to blacklist device", ex);
        }
    }

    @Override
    public void clear() {
        try {
            redisOperations.delete(List.of(blacklistAccountsKey(), blacklistIpsKey(), blacklistDevicesKey()));
        } catch (Exception ex) {
            log.error("Failed to clear Redis blacklist keys", ex);
            throw new IllegalStateException("Failed to clear Redis blacklist keys", ex);
        }
    }

    private int toInt(Object obj) {
        if (obj instanceof Number num) {
            return num.intValue();
        }
        throw new IllegalStateException("Redis returned a non-numeric transaction count");
    }

    private boolean toBoolean(Object obj) {
        if (obj instanceof Boolean value) {
            return value;
        }
        throw new IllegalStateException("Redis returned an invalid blacklist result");
    }
}
