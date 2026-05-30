package com.exemplo.secrest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SecrestApplication {

    public static void main(String[] args) {
        SpringApplication.run(SecrestApplication.class, args);
    }
}
