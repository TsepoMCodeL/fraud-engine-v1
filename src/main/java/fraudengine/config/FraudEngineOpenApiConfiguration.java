package fraudengine.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Fraud Engine API",
                version = "v1",
                description = "Fraud evaluation, blacklist administration, and transaction history API."
        )
)
@SecurityScheme(
        name = "adminBasicAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "basic",
        description = "Admin credentials for blacklist administration and transaction history."
)
public class FraudEngineOpenApiConfiguration {
}
