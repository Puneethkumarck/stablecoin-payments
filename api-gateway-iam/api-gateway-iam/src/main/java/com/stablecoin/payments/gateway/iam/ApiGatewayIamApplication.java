package com.stablecoin.payments.gateway.iam;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableFeignClients
@EnableScheduling
public class ApiGatewayIamApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayIamApplication.class, args);
    }
}
