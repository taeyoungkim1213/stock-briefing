package com.stock.briefing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties
@EnableScheduling
public class StockBriefingApplication {

    public static void main(String[] args) {
        SpringApplication.run(StockBriefingApplication.class, args);
    }

}
