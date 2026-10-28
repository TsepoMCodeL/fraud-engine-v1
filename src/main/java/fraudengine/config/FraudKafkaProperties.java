package fraudengine.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "fraud.kafka")
public class FraudKafkaProperties {

    @NotBlank
    private String topic = "fraud.evaluation.completed.v1";
    @Positive
    private int outboxBatchSize = 50;
    @Positive
    private int maxDrainBatches = 10;
    @Positive
    private long outboxPollIntervalMs = 1000;
    @Positive
    private int outboxRetentionDays = 7;
    @NotNull
    private Duration publishTimeout = Duration.ofSeconds(10);
    @NotNull
    private Duration outboxClaimLease = Duration.ofSeconds(30);
    @NotNull
    private Duration producerSendBlockTimeout = Duration.ofSeconds(5);

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public int getOutboxBatchSize() {
        return outboxBatchSize;
    }

    public void setOutboxBatchSize(int outboxBatchSize) {
        this.outboxBatchSize = outboxBatchSize;
    }

    public int getMaxDrainBatches() {
        return maxDrainBatches;
    }

    public void setMaxDrainBatches(int maxDrainBatches) {
        this.maxDrainBatches = maxDrainBatches;
    }

    public long getOutboxPollIntervalMs() {
        return outboxPollIntervalMs;
    }

    public void setOutboxPollIntervalMs(long outboxPollIntervalMs) {
        this.outboxPollIntervalMs = outboxPollIntervalMs;
    }

    public int getOutboxRetentionDays() {
        return outboxRetentionDays;
    }

    public void setOutboxRetentionDays(int outboxRetentionDays) {
        this.outboxRetentionDays = outboxRetentionDays;
    }

    public Duration getPublishTimeout() {
        return publishTimeout;
    }

    public void setPublishTimeout(Duration publishTimeout) {
        this.publishTimeout = publishTimeout;
    }

    public Duration getOutboxClaimLease() {
        return outboxClaimLease;
    }

    public void setOutboxClaimLease(Duration outboxClaimLease) {
        this.outboxClaimLease = outboxClaimLease;
    }

    public Duration getProducerSendBlockTimeout() {
        return producerSendBlockTimeout;
    }

    public void setProducerSendBlockTimeout(Duration producerSendBlockTimeout) {
        this.producerSendBlockTimeout = producerSendBlockTimeout;
    }
}
