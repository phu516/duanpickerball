package com.pickleball;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class PickleballApplication {
    public static void main(String[] args) {
        SpringApplication.run(PickleballApplication.class, args);
    }
}
