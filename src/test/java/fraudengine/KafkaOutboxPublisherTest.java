package fraudengine;

import fraudengine.config.FraudKafkaProperties;
import fraudengine.events.KafkaOutboxPublisher;
import fraudengine.persistence.FraudEvaluationOutboxRepository;
import fraudengine.persistence.FraudEvaluationOutboxRepository.OutboxMessage;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaOutboxPublisherTest {

    private final FraudEvaluationOutboxRepository outboxRepository =
            mock(FraudEvaluationOutboxRepository.class);
    private final KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
    private final FraudKafkaProperties properties = new FraudKafkaProperties();
    private final KafkaOutboxPublisher publisher =
            new KafkaOutboxPublisher(outboxRepository, kafkaTemplate, properties);

    @Test
    void marksOutboxMessagePublishedAfterKafkaAcknowledges() {
        OutboxMessage message = new OutboxMessage(
                1, "event-1", "account-1", "{\"eventId\":\"event-1\"}", 0, "claim-1");
        when(outboxRepository.claimPending(eq(50), any(Instant.class))).thenReturn(List.of(message));
        when(kafkaTemplate.send(
                properties.getTopic(),
                message.accountId(),
                message.payload()
        )).thenReturn(CompletableFuture.<SendResult<String, String>>completedFuture(null));

        publisher.publishPending();

        verify(outboxRepository).markPublished(message.id(), message.claimToken());
        verify(outboxRepository, never()).markFailed(eq(message.id()), eq(message.claimToken()), any(), any());
    }

    @Test
    void schedulesRetryWhenKafkaSendFails() {
        OutboxMessage message = new OutboxMessage(
                1, "event-1", "account-1", "{\"eventId\":\"event-1\"}", 0, "claim-1");
        CompletableFuture<SendResult<String, String>> failedSend = new CompletableFuture<>();
        failedSend.completeExceptionally(new IllegalStateException("Kafka unavailable"));
        when(outboxRepository.claimPending(eq(50), any(Instant.class))).thenReturn(List.of(message));
        when(kafkaTemplate.send(
                properties.getTopic(),
                message.accountId(),
                message.payload()
        )).thenReturn(failedSend);

        publisher.publishPending();

        verify(outboxRepository).markFailed(
                eq(message.id()), eq(message.claimToken()), eq("Kafka unavailable"), any());
        verify(outboxRepository, never()).markPublished(message.id(), message.claimToken());
    }

    @Test
    void publishesBatchConcurrentlyAndAcknowledgesEachMessage() {
        OutboxMessage first = new OutboxMessage(
                1, "event-1", "account-1", "{\"eventId\":\"event-1\"}", 0, "claim-1");
        OutboxMessage second = new OutboxMessage(
                2, "event-2", "account-2", "{\"eventId\":\"event-2\"}", 0, "claim-1");
        when(outboxRepository.claimPending(eq(50), any(Instant.class))).thenReturn(List.of(first, second));
        when(kafkaTemplate.send(properties.getTopic(), first.accountId(), first.payload()))
                .thenReturn(CompletableFuture.<SendResult<String, String>>completedFuture(null));
        when(kafkaTemplate.send(properties.getTopic(), second.accountId(), second.payload()))
                .thenReturn(CompletableFuture.<SendResult<String, String>>completedFuture(null));

        publisher.publishPending();

        verify(kafkaTemplate).send(properties.getTopic(), first.accountId(), first.payload());
        verify(kafkaTemplate).send(properties.getTopic(), second.accountId(), second.payload());
        verify(outboxRepository).markPublished(first.id(), first.claimToken());
        verify(outboxRepository).markPublished(second.id(), second.claimToken());
    }

    @Test
    void claimLeaseCoversBlockingSendTimeForTheWholeBatch() {
        Instant now = Instant.now();
        when(outboxRepository.claimPending(eq(50), any(Instant.class))).thenAnswer(invocation -> {
            Instant claimUntil = invocation.getArgument(1);
            org.junit.jupiter.api.Assertions.assertTrue(
                    claimUntil.isAfter(now.plusSeconds(260)),
                    "claim must cover 50 blocking sends, publish timeout, and margin");
            return List.of();
        });

        publisher.publishPending();

        verify(outboxRepository).claimPending(eq(50), any(Instant.class));
    }

    @Test
    void handlesUnexpectedExceptionDuringSendGracefully() {
        OutboxMessage message = new OutboxMessage(
                1, "event-1", "account-1", "{\"eventId\":\"event-1\"}", 0, "claim-1");
        when(outboxRepository.claimPending(eq(50), any(Instant.class))).thenReturn(List.of(message));
        when(kafkaTemplate.send(
                properties.getTopic(),
                message.accountId(),
                message.payload()
        )).thenThrow(new IllegalArgumentException("Unexpected send error"));

        publisher.publishPending();

        verify(outboxRepository).markFailed(
                eq(message.id()), eq(message.claimToken()), eq("Unexpected send error"), any());
        verify(outboxRepository, never()).markPublished(message.id(), message.claimToken());
    }

    @Test
    void inFlightSendTimesOutWithoutPrematurelyFailingLease() {
        properties.setPublishTimeout(Duration.ofMillis(50));
        OutboxMessage message = new OutboxMessage(
                1, "event-1", "account-1", "{\"eventId\":\"event-1\"}", 0, "claim-1");
        CompletableFuture<SendResult<String, String>> slowFuture = new CompletableFuture<>();
        when(outboxRepository.claimPending(eq(50), any(Instant.class))).thenReturn(List.of(message));
        when(kafkaTemplate.send(
                properties.getTopic(),
                message.accountId(),
                message.payload()
        )).thenReturn(slowFuture);

        publisher.publishPending();

        // The send is still in-flight, so it should NOT be prematurely marked failed or published
        verify(outboxRepository, never()).markFailed(eq(message.id()), eq(message.claimToken()), any(), any());
        verify(outboxRepository, never()).markPublished(message.id(), message.claimToken());

        // When Kafka later completes in the background, it marks published with the active lease token
        slowFuture.complete(null);
        verify(outboxRepository).markPublished(message.id(), message.claimToken());
    }

    @Test
    void drainsConsecutiveBatchesWhenBacklogIsPresent() {
        properties.setOutboxBatchSize(2);
        properties.setMaxDrainBatches(3);

        OutboxMessage msg1 = new OutboxMessage(1, "e1", "a1", "{}", 0, "c1");
        OutboxMessage msg2 = new OutboxMessage(2, "e2", "a2", "{}", 0, "c1");
        OutboxMessage msg3 = new OutboxMessage(3, "e3", "a3", "{}", 0, "c2");
        OutboxMessage msg4 = new OutboxMessage(4, "e4", "a4", "{}", 0, "c2");
        OutboxMessage msg5 = new OutboxMessage(5, "e5", "a5", "{}", 0, "c3");

        when(outboxRepository.claimPending(eq(2), any(Instant.class)))
                .thenReturn(List.of(msg1, msg2)) // batch 1 (full)
                .thenReturn(List.of(msg3, msg4)) // batch 2 (full)
                .thenReturn(List.of(msg5));       // batch 3 (partial, terminates drain)

        when(kafkaTemplate.send(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publishPending();

        // Verifies 3 consecutive claims were issued in one invocation
        verify(outboxRepository, org.mockito.Mockito.times(3)).claimPending(eq(2), any(Instant.class));
        verify(outboxRepository).markPublished(1, "c1");
        verify(outboxRepository).markPublished(2, "c1");
        verify(outboxRepository).markPublished(3, "c2");
        verify(outboxRepository).markPublished(4, "c2");
        verify(outboxRepository).markPublished(5, "c3");
    }
}
