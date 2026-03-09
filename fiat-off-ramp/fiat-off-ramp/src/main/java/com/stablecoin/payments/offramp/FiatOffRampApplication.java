package com.stablecoin.payments.offramp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableFeignClients
@EnableScheduling
public class FiatOffRampApplication {

    public static void main(String[] args) {
        SpringApplication.run(FiatOffRampApplication.class, args);
    }
}
