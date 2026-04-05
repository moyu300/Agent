package com.agent.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Document(collection = "chat_sessions") // 指定集合名称
public class ChatSession {

    @Id
    private String id; // 对应 MongoDB 的 _id
    private String sessionId;
    private String userId;
    private String title;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<Message> messages = new ArrayList<>(); // 使用定义的 Message 类
}

