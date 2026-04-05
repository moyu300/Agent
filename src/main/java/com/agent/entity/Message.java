package com.agent.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Message {
    private String role;    // user / assistant / system（多轮对话必须）
    private String content; // 内容
    private LocalDateTime timestamp; // 时间
}
