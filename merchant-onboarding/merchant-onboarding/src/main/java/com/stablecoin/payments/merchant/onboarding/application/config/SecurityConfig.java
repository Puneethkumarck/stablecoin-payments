package com.stablecoin.payments.merchant.onboarding.application.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable()).authorizeHttpRequests(auth -> auth
        // Webhook endpoints use HMAC validation, not JWT
        .requestMatchers("/api/internal/webhooks/**").permitAll()
        // Actuator health/readiness
        .requestMatchers("/actuator/**").permitAll().anyRequest().authenticated());
    return http.build();
  }
}
