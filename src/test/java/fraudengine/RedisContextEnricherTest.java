package fraudengine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ZSetOperations;

import fraudengine.domain.CustomerProfile;
import fraudengine.domain.EnrichedContext;
import fraudengine.domain.GeoLocation;
import fraudengine.domain.Transaction;
import fraudengine.enrichment.RedisContextEnricher;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisContextEnricherTest {

    @Mock
    private RedisOperations<String, String> redisOperations;

    @Mock
    private SetOperations<String, String> setOperations;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    private ObjectMapper objectMapper;
    private RedisContextEnricher redisEnricher;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        redisEnricher = new RedisContextEnricher(
                redisOperations,
                objectMapper,
                "fraud:",
                Duration.ofHours(48)
        );
    }

    @Test
    @DisplayName("enrich: parses pipelined read-only Redis responses into EnrichedContext")
    void shouldParsePipelinedRedisResults() throws Exception {
        Transaction tx = Transaction.builder()
                .id("TX-CURRENT")
                .accountId("ACC-100")
                .amount(85.00)
                .timestamp(Instant.now())
                .ipAddress("192.0.2.1")
                .deviceId("DEV-100")
                .location(new GeoLocation(40.7128, -74.0060, "ZA"))
                .build();

        Transaction priorTx = Transaction.builder()
                .id("TX-PRIOR")
                .accountId("ACC-100")
                .amount(50.00)
                .timestamp(Instant.now().minus(2, ChronoUnit.MINUTES))
                .ipAddress("192.0.2.1")
                .deviceId("DEV-100")
                .location(new GeoLocation(40.7128, -74.0060, "ZA"))
                .build();

        String priorTxJson = objectMapper.writeValueAsString(priorTx);

        // Simulated read-only pipeline results:
        // [0]: count1m (1L)
        // [1]: count10m (2L)
        // [2]: count24h (4L)
        // [3]: latest transaction JSON
        // [4]: isAccountBlacklisted (false)
        // [5]: isIpBlacklisted (false)
        // [6]: isDeviceBlacklisted (false)
        List<Object> mockPipelineResults = List.of(
                1L,
                2L,
                4L,
                Set.of(priorTxJson),
                Boolean.FALSE,
                Boolean.FALSE,
                Boolean.FALSE
        );

        when(redisOperations.executePipelined(any(SessionCallback.class))).thenReturn(mockPipelineResults);

        CustomerProfile profile = new CustomerProfile(
                "ACC-100", Instant.now().minus(30, ChronoUnit.DAYS),
                BigDecimal.valueOf(80.0), 90, Set.of("DEV-100"), false
        );

        EnrichedContext enriched = redisEnricher.enrich(tx, profile);

        assertNotNull(enriched);
        assertEquals(1, enriched.transactionsLast1Minute());
        assertEquals(2, enriched.transactionsLast10Minutes());
        assertEquals(4, enriched.transactionsLast24Hours());
        assertTrue(enriched.lastKnownTransaction().isPresent());
        assertEquals("TX-PRIOR", enriched.lastKnownTransaction().get().id());
        assertTrue(enriched.isDeviceRecognized());
        assertFalse(enriched.isAccountBlacklisted());
        assertFalse(enriched.isIpBlacklisted());
        assertFalse(enriched.isDeviceBlacklisted());
        assertEquals("redis", enriched.customFeatures().get("dataStore"));
    }

    @Test
    @DisplayName("enrich: detects blacklisted account from Redis pipeline result")
    void shouldDetectBlacklistedAccountFromPipeline() {
        Transaction tx = Transaction.builder()
                .id("TX-BAD")
                .accountId("ACC-FRAUD-1")
                .amount(100.00)
                .build();

        List<Object> mockResults = List.of(
                0L, 0L, 0L,
                Collections.emptySet(),
                Boolean.TRUE // Account is blacklisted!
        );

        when(redisOperations.executePipelined(any(SessionCallback.class))).thenReturn(mockResults);

        EnrichedContext enriched = redisEnricher.enrich(tx, CustomerProfile.defaultProfile("ACC-FRAUD-1"));

        assertTrue(enriched.isAccountBlacklisted());
    }

    @Test
    @DisplayName("enrich: bounds velocity reads to the event timestamp without pruning writes")
    void shouldBoundVelocityQueriesToEventTimestamp() throws Exception {
        Instant eventTime = Instant.parse("2026-10-21T10:00:00Z");
        Transaction tx = Transaction.builder()
                .id("TX-EVENT-TIME")
                .accountId("ACC-EVENT-TIME")
                .amount(50.00)
                .timestamp(eventTime)
                .build();
        List<Object> pipelineResults = List.of(0L, 0L, 0L, Collections.emptySet(), Boolean.FALSE);
        when(redisOperations.executePipelined(any(SessionCallback.class))).thenReturn(pipelineResults);
        when(redisOperations.opsForZSet()).thenReturn(zSetOperations);
        when(redisOperations.opsForSet()).thenReturn(setOperations);

        redisEnricher.enrich(tx, CustomerProfile.defaultProfile(tx.accountId()));

        ArgumentCaptor<SessionCallback<?>> callbackCaptor = ArgumentCaptor.forClass(SessionCallback.class);
        verify(redisOperations).executePipelined(callbackCaptor.capture());
        callbackCaptor.getValue().execute(redisOperations);

        String key = "fraud:velocity:account:ACC-EVENT-TIME";
        double eventTimeMs = eventTime.toEpochMilli();
        verify(zSetOperations, times(3)).count(eq(key), anyDouble(), eq(eventTimeMs));
        verify(zSetOperations).reverseRangeByScore(
                eq(key), anyDouble(), eq(eventTimeMs), eq(0L), eq(1L));
        // Verify no writes (removeRangeByScore) are issued during enrichment
        verify(zSetOperations, never()).removeRangeByScore(anyString(), anyDouble(), anyDouble());
    }

    @Test
    @DisplayName("enrich: fails explicitly when Redis is unavailable")
    void shouldFailWhenRedisIsUnavailable() {
        Transaction tx = Transaction.builder()
                .id("TX-FAILOVER")
                .accountId("ACC-OFFLINE")
                .amount(50.00)
                .build();

        // Simulate Redis network partition / timeout
        when(redisOperations.executePipelined(any(SessionCallback.class)))
                .thenThrow(new RedisConnectionFailureException("Connection refused: 127.0.0.1:6379"));

        assertThrows(IllegalStateException.class,
                () -> redisEnricher.enrich(tx, CustomerProfile.defaultProfile("ACC-OFFLINE")));
    }

    @Test
    @DisplayName("blacklist: writes to Redis Set keys")
    void shouldAddEntitiesToRedisBlacklist() {
        when(redisOperations.opsForSet()).thenReturn(setOperations);

        redisEnricher.addBlacklistedAccount("ACC-BLOCKED-1");
        redisEnricher.addBlacklistedIp("198.51.100.5");
        redisEnricher.addBlacklistedDevice("DEV-HACK-1");

        verify(setOperations).add("fraud:blacklist:accounts", "ACC-BLOCKED-1");
        verify(setOperations).add("fraud:blacklist:ips", "198.51.100.5");
        verify(setOperations).add("fraud:blacklist:devices", "DEV-HACK-1");
    }

    @Test
    @DisplayName("blacklist: propagates Redis write failures")
    void shouldPropagateBlacklistWriteFailure() {
        when(redisOperations.opsForSet()).thenReturn(setOperations);
        when(setOperations.add("fraud:blacklist:accounts", "ACC-BLOCKED-1"))
                .thenThrow(new RedisConnectionFailureException("Connection refused"));

        assertThrows(IllegalStateException.class,
                () -> redisEnricher.addBlacklistedAccount("ACC-BLOCKED-1"));
    }

    @Test
    @DisplayName("recordTransaction: executes pipelined prune, zadd, and expire")
    void shouldRecordTransactionViaPipeline() {
        Transaction tx = Transaction.builder()
                .id("TX-REC")
                .accountId("ACC-REC")
                .amount(25.00)
                .timestamp(Instant.now())
                .build();
        when(redisOperations.opsForZSet()).thenReturn(zSetOperations);

        redisEnricher.recordTransaction(tx);

        ArgumentCaptor<SessionCallback<?>> captor = ArgumentCaptor.forClass(SessionCallback.class);
        verify(redisOperations).executePipelined(captor.capture());

        captor.getValue().execute(redisOperations);
        String vKey = "fraud:velocity:account:ACC-REC";
        verify(zSetOperations).removeRangeByScore(eq(vKey), eq(Double.NEGATIVE_INFINITY), anyDouble());
        verify(zSetOperations).add(eq(vKey), anyString(), eq((double) tx.timestamp().toEpochMilli()));
        verify(redisOperations).expire(eq(vKey), eq(Duration.ofHours(48)));
    }
}
