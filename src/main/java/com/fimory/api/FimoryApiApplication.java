package com.fimory.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FimoryApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(FimoryApiApplication.class, args);
    }
}
