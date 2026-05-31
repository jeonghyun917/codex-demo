package com.kingyurina.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CodexDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodexDemoApplication.class, args);
    }
}
