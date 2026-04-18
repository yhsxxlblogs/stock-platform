package com.stock.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class StockPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(StockPlatformApplication.class, args);
    }
}
