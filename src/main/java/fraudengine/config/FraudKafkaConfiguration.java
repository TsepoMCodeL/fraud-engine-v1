package fraudengine.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(FraudKafkaProperties.class)
public class FraudKafkaConfiguration {

    @Bean(name = "outboxPublisherExecutor")
    @ConditionalOnMissingBean(name = "outboxPublisherExecutor")
    public Executor outboxPublisherExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
