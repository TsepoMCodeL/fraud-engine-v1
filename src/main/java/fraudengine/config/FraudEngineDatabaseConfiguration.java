package fraudengine.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class FraudEngineDatabaseConfiguration {

    @Bean
    public InitializingBean fraudEngineDatabaseMigrations(DataSource dataSource) {
        return () -> Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }
}
