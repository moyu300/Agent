package com.agent.service;

import com.agent.entity.ChatSession;
import com.agent.entity.Message;
import com.agent.repository.ChatSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ChatSessionService {

    private final ChatSessionRepository chatSessionRepository;

    /**
     * 创建新会话
     */
    public ChatSession createSession(String userId) {
        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setTitle("新会话");
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        return chatSessionRepository.save(session);
    }


    /**
     * 根据 sessionId 获取会话（用于继续多轮对话）
     */
    public Optional<ChatSession> findById(String sessionId) {
        return chatSessionRepository.findById(sessionId);
    }
    /**
     * 获取某个用户的所有历史会话列表
     */
    public List<ChatSession> findSessionsByUserId(String userId) {
        return chatSessionRepository.findByUserIdOrderByUpdatedAtDesc(userId);
    }
    /**
     * 追加用户消息到会话
     */
    public void addUserMessage(String sessionId, String content) {
        ChatSession session = findById(sessionId)
                .orElseThrow(() -> new RuntimeException("会话不存在: " + sessionId));

        Message msg = new Message();
        msg.setRole("user");
        msg.setContent(content);
        msg.setTimestamp(LocalDateTime.now());

        session.getMessages().add(msg);
        session.setUpdatedAt(LocalDateTime.now());
        chatSessionRepository.save(session);
    }

    /**
     * 追加AI回复到会话
     */
    public void addAssistantMessage(String sessionId, String content) {
        ChatSession session = findById(sessionId)
                .orElseThrow(() -> new RuntimeException("会话不存在: " + sessionId));

        Message msg = new Message();
        msg.setRole("assistant");
        msg.setContent(content);
        msg.setTimestamp(LocalDateTime.now());

        session.getMessages().add(msg);
        session.setUpdatedAt(LocalDateTime.now());
        chatSessionRepository.save(session);
    }

    /**
     * 获取会话历史（给大模型当上下文）
     */
    public List<Message> getHistoryMessages(String sessionId) {
        ChatSession session = findById(sessionId)
                .orElseThrow(() -> new RuntimeException("会话不存在: " + sessionId));
        return session.getMessages();
    }

    /**
     * 删除会话
     */
    public void deleteById(String sessionId) {
        chatSessionRepository.deleteById(sessionId);
    }
}
