package fraudengine.events;

import fraudengine.config.FraudKafkaProperties;
import fraudengine.persistence.FraudEvaluationOutboxRepository;
import fraudengine.persistence.FraudEvaluationOutboxRepository.OutboxMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@ConditionalOnProperty(prefix = "fraud.kafka", name = "publisher-enabled", havingValue = "true", matchIfMissing = true)
public class KafkaOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaOutboxPublisher.class);
    private static final int MAX_RETRY_DELAY_SECONDS = 300;
    private static final int MAX_ERROR_LENGTH = 2000;

    private final FraudEvaluationOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final FraudKafkaProperties properties;

    public KafkaOutboxPublisher(
            FraudEvaluationOutboxRepository outboxRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            FraudKafkaProperties properties
    ) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${fraud.kafka.outbox-poll-interval-ms:1000}")
    public void publishPending() {
        int batchCount = 0;
        int processed;
        do {
            processed = publishBatch();
            batchCount++;
        } while (processed == properties.getOutboxBatchSize() && batchCount < properties.getMaxDrainBatches());
    }

    private int publishBatch() {
        List<PendingSend> pendingSends = new ArrayList<>();
        Duration requiredLease = properties.getProducerSendBlockTimeout()
                .multipliedBy(properties.getOutboxBatchSize())
                .plus(properties.getPublishTimeout())
                .plusSeconds(5);
        Duration claimLease = properties.getOutboxClaimLease().compareTo(requiredLease) > 0
                ? properties.getOutboxClaimLease()
                : requiredLease;
        Instant claimUntil = Instant.now().plus(claimLease);

        List<OutboxMessage> claimedMessages = outboxRepository.claimPending(properties.getOutboxBatchSize(), claimUntil);
        for (OutboxMessage message : claimedMessages) {
            try {
                CompletableFuture<Throwable> result = kafkaTemplate
                        .send(properties.getTopic(), message.accountId(), message.payload())
                        .handle((sendResult, failure) -> {
                            try {
                                if (failure == null) {
                                    outboxRepository.markPublished(message.id(), message.claimToken());
                                } else {
                                    markFailed(message, failure);
                                }
                            } catch (Exception ex) {
                                log.error("Failed to update outbox state for event {}", message.eventId(), ex);
                            }
                            return failure;
                        });
                pendingSends.add(new PendingSend(message, result));
            } catch (Exception ex) {
                markFailed(message, ex);
            }
        }

        if (pendingSends.isEmpty()) {
            return 0;
        }

        try {
            CompletableFuture.allOf(pendingSends.stream()
                            .map(PendingSend::result)
                            .toArray(CompletableFuture[]::new))
                    .get(properties.getPublishTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException ex) {
            // Individual send failures are handled in the handle callback above.
        } catch (TimeoutException ex) {
            log.warn("Kafka publish batch exceeded the {} timeout; in-flight sends remain protected by lease until {}",
                    properties.getPublishTimeout(), claimUntil);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing fraud evaluation events", ex);
        }

        return claimedMessages.size();
    }

    @Scheduled(cron = "${fraud.kafka.outbox-cleanup-cron:0 0 2 * * *}")
    public void purgePublishedEvents() {
        outboxRepository.deletePublishedBefore(
                Instant.now().minusSeconds(properties.getOutboxRetentionDays() * 24L * 60 * 60));
    }

    private void markFailed(OutboxMessage message, Throwable failure) {
        Instant retryAt = Instant.now().plusSeconds(retryDelaySeconds(message.attempts()));
        outboxRepository.markFailed(
                message.id(),
                message.claimToken(),
                errorMessage(failure),
                retryAt
        );
        log.error("Failed to publish fraud evaluation event {}; retry scheduled at {}",
                message.eventId(), retryAt, failure);
    }

    private long retryDelaySeconds(int attempts) {
        long baseDelay = Math.min(MAX_RETRY_DELAY_SECONDS, 1L << Math.min(attempts, 8));
        long half = Math.max(1, baseDelay / 2);
        return half + ThreadLocalRandom.current().nextLong(half + 1);
    }

    private String errorMessage(Throwable ex) {
        Throwable cause = ex instanceof ExecutionException && ex.getCause() != null ? ex.getCause() : ex;
        if (cause instanceof java.util.concurrent.CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        String message = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
    }

    private record PendingSend(OutboxMessage message, CompletableFuture<Throwable> result) {}
}
