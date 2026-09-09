package com.bhuraksha.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the BhuRaksha backend (SIH26001 - Landslide Early Warning, NER/Sikkim).
 *
 * Run with:  mvn spring-boot:run
 * Default port: 8081
 */
@SpringBootApplication
public class BhurakshaApplication {
    public static void main(String[] args) {
        SpringApplication.run(BhurakshaApplication.class, args);
    }
}
