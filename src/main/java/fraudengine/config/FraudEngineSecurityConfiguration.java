package fraudengine.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class FraudEngineSecurityConfiguration {

    @Bean
    public SecurityFilterChain fraudEngineSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/prometheus").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/fraud/blacklist").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/fraud/evaluations/**",
                                "/api/v1/fraud/transactions/**",
                                "/api/v1/fraud/accounts/**").hasRole("ADMIN")
                        .anyRequest().permitAll())
                .httpBasic(Customizer.withDefaults())
                .build();
    }

    @Bean
    public UserDetailsService fraudEngineAdminUserDetailsService(
            @Value("${fraud.engine.admin.username:}") String username,
            @Value("${fraud.engine.admin.password:}") String password,
            PasswordEncoder passwordEncoder
    ) {
        if (username.isBlank() || password.isBlank()) {
            throw new IllegalStateException(
                    "FRAUD_ADMIN_USERNAME and FRAUD_ADMIN_PASSWORD must be configured");
        }

        return new InMemoryUserDetailsManager(User.withUsername(username)
                .password(passwordEncoder.encode(password))
                .roles("ADMIN")
                .build());
    }

    @Bean
    public PasswordEncoder fraudEnginePasswordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
