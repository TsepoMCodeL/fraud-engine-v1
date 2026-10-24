package fraudengine;

import fraudengine.domain.CustomerProfile;
import fraudengine.domain.EnrichedContext;
import fraudengine.domain.Transaction;
import fraudengine.enrichment.ContextEnricher;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Test-scoped fixture implementing ContextEnricher for isolated unit tests.
 */
public class TestContextEnricher implements ContextEnricher {

    private final Set<String> blacklistedAccounts = ConcurrentHashMap.newKeySet();
    private final Set<String> blacklistedIps = ConcurrentHashMap.newKeySet();
    private final Set<String> blacklistedDevices = ConcurrentHashMap.newKeySet();
    private final Map<String, Deque<Transaction>> accountHistory = new ConcurrentHashMap<>();

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void addBlacklistedAccount(String accountId) {
        blacklistedAccounts.add(accountId);
    }

    @Override
    public void addBlacklistedIp(String ipAddress) {
        blacklistedIps.add(ipAddress);
    }

    @Override
    public void addBlacklistedDevice(String deviceId) {
        blacklistedDevices.add(deviceId);
    }

    @Override
    public EnrichedContext enrich(Transaction transaction, CustomerProfile profile) {
        String accountId = transaction.accountId();
        Instant now = transaction.timestamp();

        Deque<Transaction> history = accountHistory.get(accountId);

        int count1m = 0;
        int count10m = 0;
        int count24h = 0;
        Optional<Transaction> lastTx = Optional.empty();

        if (history != null && !history.isEmpty()) {
            synchronized (history) {
                lastTx = history.stream()
                        .filter(tx -> tx.timestamp().isBefore(now) || tx.timestamp().equals(now))
                        .max(Comparator.comparing(tx -> tx.timestamp()));

                Instant cutoff1m = now.minus(Duration.ofMinutes(1));
                Instant cutoff10m = now.minus(Duration.ofMinutes(10));
                Instant cutoff24h = now.minus(Duration.ofHours(24));

                for (Transaction pastTx : history) {
                    if (pastTx.timestamp().isAfter(cutoff24h)) {
                        count24h++;
                        if (pastTx.timestamp().isAfter(cutoff10m)) {
                            count10m++;
                            if (pastTx.timestamp().isAfter(cutoff1m)) {
                                count1m++;
                            }
                        }
                    }
                }
            }
        }

        boolean isDeviceRecognized = transaction.deviceId() != null &&
                profile.knownDeviceIds().contains(transaction.deviceId());

        boolean isAccountBlacklisted = blacklistedAccounts.contains(transaction.accountId());
        boolean isIpBlacklisted = transaction.ipAddress() != null && blacklistedIps.contains(transaction.ipAddress());
        boolean isDeviceBlacklisted = transaction.deviceId() != null && blacklistedDevices.contains(transaction.deviceId());

        return new EnrichedContext(
                count1m,
                count10m,
                count24h,
                lastTx,
                isDeviceRecognized,
                isAccountBlacklisted,
                isIpBlacklisted,
                isDeviceBlacklisted,
                Map.of("dataStore", "test-fixture")
        );
    }

    @Override
    public void recordTransaction(Transaction transaction) {
        accountHistory.compute(transaction.accountId(), (accId, deque) -> {
            if (deque == null) {
                deque = new ConcurrentLinkedDeque<>();
            }
            synchronized (deque) {
                deque.addFirst(transaction);
            }
            return deque;
        });
    }

    @Override
    public void clear() {
        blacklistedAccounts.clear();
        blacklistedIps.clear();
        blacklistedDevices.clear();
        accountHistory.clear();
    }
}
