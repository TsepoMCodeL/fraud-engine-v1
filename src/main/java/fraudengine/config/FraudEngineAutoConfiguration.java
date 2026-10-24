package fraudengine.config;

import fraudengine.decision.DecisionEngine;
import fraudengine.decision.DecisionThresholds;
import fraudengine.decision.ThresholdDecisionEngine;
import fraudengine.engine.DefaultFraudEngine;
import fraudengine.engine.FraudAuditListener;
import fraudengine.engine.FraudEngine;
import fraudengine.engine.FraudEngineConfig;
import fraudengine.enrichment.ContextEnricher;
import fraudengine.enrichment.RedisContextEnricher;
import fraudengine.rules.BlacklistRule;
import fraudengine.rules.DeviceTrustRule;
import fraudengine.rules.FraudRule;
import fraudengine.rules.GeoVelocityAnomalyRule;
import fraudengine.rules.HighAmountAnomalyRule;
import fraudengine.rules.ShortWindowVelocityRule;
import fraudengine.rules.TransactionVelocityRule;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;

@Configuration
@EnableConfigurationProperties(FraudEngineProperties.class)
public class FraudEngineAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(FraudEngineAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(ContextEnricher.class)
    public ContextEnricher contextEnricher(
            FraudEngineProperties properties,
            ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            ObjectProvider<ObjectMapper> objectMapperProvider
    ) {
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate != null) {
            log.info("Configuring RedisContextEnricher with prefix '{}' and TTL {}h",
                    properties.getRedisKeyPrefix(), properties.getVelocityTtlHours());
            return new RedisContextEnricher(
                    redisTemplate,
                    objectMapperProvider.getIfAvailable(),
                    properties.getRedisKeyPrefix(),
                    Duration.ofHours(properties.getVelocityTtlHours())
            );
        }

        throw new IllegalStateException(
                "Redis connection bean (StringRedisTemplate) is required for RedisContextEnricher. " +
                "Ensure Redis is running or provide a custom ContextEnricher bean.");
    }

    @Bean
    @ConditionalOnMissingBean
    public DecisionEngine decisionEngine(FraudEngineProperties properties) {
        DecisionThresholds thresholds = new DecisionThresholds(
                properties.getReviewThreshold(),
                properties.getDeclineThreshold(),
                properties.isHonorHardOverrides()
        );
        return new ThresholdDecisionEngine(thresholds);
    }

    @Bean
    @ConditionalOnMissingBean
    public BlacklistRule blacklistRule() {
        return new BlacklistRule();
    }

    @Bean
    @ConditionalOnMissingBean
    public ShortWindowVelocityRule shortWindowVelocityRule() {
        return new ShortWindowVelocityRule();
    }

    @Bean
    @ConditionalOnMissingBean
    public TransactionVelocityRule transactionVelocityRule() {
        return new TransactionVelocityRule();
    }

    @Bean
    @ConditionalOnMissingBean
    public GeoVelocityAnomalyRule geoVelocityAnomalyRule() {
        return new GeoVelocityAnomalyRule();
    }

    @Bean
    @ConditionalOnMissingBean
    public HighAmountAnomalyRule highAmountAnomalyRule() {
        return new HighAmountAnomalyRule();
    }

    @Bean
    @ConditionalOnMissingBean
    public DeviceTrustRule deviceTrustRule() {
        return new DeviceTrustRule();
    }

    @Bean
    @ConditionalOnMissingBean
    public FraudAuditListener loggingAuditListener() {
        return (context, decision) -> log.info(
                "FRAUD_AUDIT: evalId={} txId={} accountId={} action={} score={} rulesEvaluated={} latencyMs={}",
                decision.evaluationId(),
                decision.transactionId(),
                decision.accountId(),
                decision.action(),
                decision.aggregateRiskScore(),
                decision.allEvaluatedRules().size(),
                String.format("%.3f", decision.totalExecutionTimeMs())
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public FraudEngine fraudEngine(
            List<FraudRule> rules,
            ContextEnricher contextEnricher,
            DecisionEngine decisionEngine,
            FraudEngineProperties properties,
            List<FraudAuditListener> auditListeners
    ) {
        FraudEngineConfig config = new FraudEngineConfig(
                properties.isShortCircuitEnabled(),
                properties.isAutoRecordTransactions(),
                properties.isFailOpenOnFatalError()
        );

        log.info("Initializing FraudEngine with {} registered rules, shortCircuit={}, enricher={}",
                rules.size(), config.shortCircuitEnabled(), contextEnricher.getClass().getSimpleName());

        return new DefaultFraudEngine(rules, contextEnricher, decisionEngine, config, auditListeners);
    }
}
