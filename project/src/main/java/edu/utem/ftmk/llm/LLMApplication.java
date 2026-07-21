package edu.utem.ftmk.llm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@ComponentScan(basePackages = "edu.utem.ftmk")
public class LLMApplication {

    public static void main(String[] args) {
        SpringApplication.run(LLMApplication.class, args);
    }
}