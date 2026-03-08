package com.stablecoin.payments.orchestrator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableFeignClients(basePackages = {
        "com.stablecoin.payments.orchestrator",
        "com.stablecoin.payments.compliance.client",
        "com.stablecoin.payments.fx.client"
})
@EnableScheduling
public class PaymentOrchestratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentOrchestratorApplication.class, args);
    }
}
