package com.stablecoin.payments.merchant.onboarding.application.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

  @Bean
  @ConditionalOnProperty(name = "app.security.enabled", havingValue = "true", matchIfMissing = true)
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable()).authorizeHttpRequests(auth -> auth
        // Webhook endpoints use HMAC validation, not JWT
        .requestMatchers("/api/internal/webhooks/**").permitAll()
        // Actuator health/readiness
        .requestMatchers("/actuator/**").permitAll()
        // OpenAPI / Swagger UI
        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
        .anyRequest().authenticated());
    return http.build();
  }

  @Bean
  @ConditionalOnProperty(name = "app.security.enabled", havingValue = "false")
  public SecurityFilterChain localSecurityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
    return http.build();
  }
}
