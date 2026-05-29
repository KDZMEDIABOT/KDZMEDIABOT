package com.localmesalevel.aisystemtakeone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AiSystemApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiSystemApplication.class, args);
    }
}
