package com.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentApplication.class, args);
        System.out.println("Agent Application Started Successfully!");
        System.out.println("======================================");
//        System.out.println("API: http://localhost:8080/api/chat");
        System.out.println("WEB: http://localhost:8080/api/");
        System.out.println("======================================");
    }

}
