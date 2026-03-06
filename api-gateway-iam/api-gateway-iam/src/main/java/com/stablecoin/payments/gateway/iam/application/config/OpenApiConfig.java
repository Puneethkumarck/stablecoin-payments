package com.stablecoin.payments.gateway.iam.application.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI apiGatewayIamOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("S10 API Gateway & IAM API")
                        .description("OAuth2 authentication, API key management, rate limiting, and merchant registry")
                        .version("1.0.0")
                        .contact(new Contact().name("StableBridge Platform")));
    }
}
