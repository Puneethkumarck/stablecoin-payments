package com.stablecoin.payments.compliance.application.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI complianceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("S2 Compliance & Travel Rule API")
                        .description("Per-transaction compliance gate: KYC, sanctions screening, AML, risk scoring, Travel Rule")
                        .version("1.0.0")
                        .contact(new Contact().name("StableBridge Platform")));
    }
}
