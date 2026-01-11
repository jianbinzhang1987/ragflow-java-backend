package com.ragflow.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class RagFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagFlowApplication.class, args);
    }

}
