package com.stablecoin.payments.merchant.iam.application.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI merchantIamOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("S13 Merchant IAM API")
                        .description("User authentication, roles, permissions, and team management")
                        .version("1.0.0")
                        .contact(new Contact().name("StableBridge Platform")));
    }
}
