package com.example.cbs_mvp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class CbsMvpApplication {
    public static void main(String[] args) {
        SpringApplication.run(CbsMvpApplication.class, args);
    }
}
