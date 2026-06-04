package com.stock.briefing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties
public class StockBriefingApplication {

    public static void main(String[] args) {
        SpringApplication.run(StockBriefingApplication.class, args);
    }

}
