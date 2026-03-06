package com.stablecoin.payments.gateway.iam.infrastructure.config;

import com.stablecoin.payments.gateway.iam.application.security.ApiKeyAuthenticationFilter;
import com.stablecoin.payments.gateway.iam.application.security.AuditLogFilter;
import com.stablecoin.payments.gateway.iam.application.security.JwtAuthenticationFilter;
import com.stablecoin.payments.gateway.iam.application.security.RateLimitFilter;
import com.stablecoin.payments.gateway.iam.application.security.UserJwtAuthenticationFilter;
import com.stablecoin.payments.gateway.iam.domain.port.AuditLogRepository;
import com.stablecoin.payments.gateway.iam.domain.port.MerchantRepository;
import com.stablecoin.payments.gateway.iam.domain.port.RateLimitEventRepository;
import com.stablecoin.payments.gateway.iam.domain.port.RateLimiter;
import com.stablecoin.payments.gateway.iam.domain.port.TokenIssuer;
import com.stablecoin.payments.gateway.iam.domain.port.TokenRevocationCache;
import com.stablecoin.payments.gateway.iam.domain.port.UserJwksProvider;
import com.stablecoin.payments.gateway.iam.domain.service.ApiKeyCommandHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    @ConditionalOnProperty(name = "app.security.enabled", havingValue = "true", matchIfMissing = true)
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationFilter jwtFilter,
                                                   ApiKeyAuthenticationFilter apiKeyFilter,
                                                   UserJwtAuthenticationFilter userJwtFilter,
                                                   RateLimitFilter rateLimitFilter,
                                                   AuditLogFilter auditLogFilter) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.POST, "/v1/auth/token").permitAll()
                        .requestMatchers(HttpMethod.POST, "/v1/auth/revoke").authenticated()
                        .requestMatchers("/.well-known/**").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(apiKeyFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(jwtFilter, ApiKeyAuthenticationFilter.class)
                .addFilterAfter(userJwtFilter, JwtAuthenticationFilter.class)
                .addFilterAfter(rateLimitFilter, UserJwtAuthenticationFilter.class)
                .addFilterAfter(auditLogFilter, RateLimitFilter.class)
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "app.security.enabled", havingValue = "false")
    public SecurityFilterChain localSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "app.security.enabled", havingValue = "true", matchIfMissing = true)
    public JwtAuthenticationFilter jwtAuthenticationFilter(TokenIssuer tokenIssuer,
                                                           TokenRevocationCache tokenRevocationCache) {
        return new JwtAuthenticationFilter(tokenIssuer, tokenRevocationCache);
    }

    @Bean
    @ConditionalOnProperty(name = "app.security.enabled", havingValue = "true", matchIfMissing = true)
    public ApiKeyAuthenticationFilter apiKeyAuthenticationFilter(ApiKeyCommandHandler apiKeyService) {
        return new ApiKeyAuthenticationFilter(apiKeyService);
    }

    @Bean
    @ConditionalOnProperty(name = "app.security.enabled", havingValue = "true", matchIfMissing = true)
    public RateLimitFilter rateLimitFilter(RateLimiter rateLimiter,
                                           MerchantRepository merchantRepository,
                                           RateLimitEventRepository rateLimitEventRepository) {
        return new RateLimitFilter(rateLimiter, merchantRepository, rateLimitEventRepository);
    }

    @Bean
    @ConditionalOnProperty(name = "app.security.enabled", havingValue = "true", matchIfMissing = true)
    public UserJwtAuthenticationFilter userJwtAuthenticationFilter(
            UserJwksProvider userJwksProvider,
            MerchantIamProperties merchantIamProperties) {
        return new UserJwtAuthenticationFilter(userJwksProvider, merchantIamProperties);
    }

    @Bean
    @ConditionalOnProperty(name = "app.security.enabled", havingValue = "true", matchIfMissing = true)
    public AuditLogFilter auditLogFilter(AuditLogRepository auditLogRepository) {
        return new AuditLogFilter(auditLogRepository);
    }
}
