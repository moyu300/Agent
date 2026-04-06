package com.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentApplication.class, args);
        System.out.println("Agent Application Started Successfully!");
        System.out.println("======================================");
        System.out.println("欢迎使用Agent Application，这是一个基于Spring Boot的AI助手平台。");
        System.out.println("别让我看见BUG行不行");
        System.out.println("======================================");
    }

}
