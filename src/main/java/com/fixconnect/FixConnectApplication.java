package com.fixconnect;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync // e-mails are sent in the background
public class FixConnectApplication {
    public static void main(String[] args) {
        SpringApplication.run(FixConnectApplication.class, args);
    }
}
