package com.cfduel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CfDuelApplication {

    public static void main(String[] args) {
        SpringApplication.run(CfDuelApplication.class, args);
    }
}
