package com.stablecoin.payments.merchant.onboarding.application.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI merchantOnboardingOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("S11 Merchant Onboarding API")
                        .description("Merchant onboarding, KYB verification, and lifecycle management")
                        .version("1.0.0")
                        .contact(new Contact().name("StableBridge Platform")));
    }
}
