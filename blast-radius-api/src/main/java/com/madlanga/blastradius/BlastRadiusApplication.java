package com.madlanga.blastradius;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BlastRadiusApplication {

    public static void main(String[] args) {
        SpringApplication.run(BlastRadiusApplication.class, args);
    }
}
