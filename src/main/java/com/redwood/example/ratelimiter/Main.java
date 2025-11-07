/*
 * (C) Copyright 2019-2025 Redwood Technology B.V., Houten, The Netherlands
 */

package com.redwood.example.ratelimiter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// This is the main entry point for the Spring Boot application.
// @SpringBootApplication is a convenience annotation that combines
// @Configuration, @EnableAutoConfiguration, and @ComponentScan.
@SpringBootApplication
public class Main {
    public static void main(String[] args) {
        // This is the static method that launches the application.
        SpringApplication.run(Main.class, args);
    }
}