package com.example.cbs_mvp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class CbsMvpApplication {
    public static void main(String[] args) {
        var ctx = SpringApplication.run(CbsMvpApplication.class, args);
        System.out.println("========================================");
        System.out.println("Checking Beans:");
        System.out.println("FxController: " + (ctx.containsBean("fxController") ? "FOUND" : "NOT FOUND"));
        System.out.println("FxRateService: " + (ctx.containsBean("fxRateService") ? "FOUND" : "NOT FOUND"));
        String[] controllers = ctx
                .getBeanNamesForAnnotation(org.springframework.web.bind.annotation.RestController.class);
        System.out.println("RestControllers: " + java.util.Arrays.toString(controllers));
        System.out.println("========================================");
    }
}
