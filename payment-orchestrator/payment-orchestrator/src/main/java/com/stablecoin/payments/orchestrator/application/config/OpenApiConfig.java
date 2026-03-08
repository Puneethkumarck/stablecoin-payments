package com.stablecoin.payments.orchestrator.application.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI orchestratorOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("S1 Payment Orchestrator API")
                        .description("Central payment lifecycle orchestration: initiate, track, and complete cross-border stablecoin payments")
                        .version("1.0.0")
                        .contact(new Contact().name("StableBridge Platform")));
    }
}
