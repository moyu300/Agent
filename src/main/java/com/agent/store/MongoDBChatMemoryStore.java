package com.agent.store;

import com.agent.entity.ChatSession;
import com.agent.entity.Message;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class MongoDBChatMemoryStore implements ChatMemoryStore {

    private final MongoTemplate mongoTemplate;
    @Qualifier("openAiChatModel")
    private final ChatLanguageModel titleModel;

    @Autowired
    public MongoDBChatMemoryStore(MongoTemplate mongoTemplate,
                                  @Qualifier("openAiChatModel") ChatLanguageModel titleModel) {
        this.mongoTemplate = mongoTemplate;
        this.titleModel = titleModel;
    }

    /**
     * 根据 memoryId（即 sessionId）获取完整对话历史
     */
    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String sessionId = memoryId.toString();
        Query query = Query.query(new Criteria().orOperator(
                Criteria.where("sessionId").is(sessionId),
                Criteria.where("userId").is(sessionId)
        ));
        ChatSession session = mongoTemplate.findOne(query, ChatSession.class);

        if (session == null || session.getMessages() == null) {
            return List.of();
        }

        return session.getMessages().stream()
                .map(msg -> {
                    String role = msg.getRole() == null ? "" : msg.getRole().trim().toLowerCase(Locale.ROOT);
                    String content = msg.getContent() == null ? "" : msg.getContent();
                    return switch (role) {
                        case "user" -> UserMessage.from(content);
                        case "assistant", "ai" -> AiMessage.from(content);
                        case "system" -> SystemMessage.from(content);
                        // 避免在持久化数据中出现历史/未知角色值时崩溃
                        default -> AiMessage.from(content);
                    };
                }).toList();
    }

    /**
     * 更新对话历史（LangChain4j 自动调用）
     */
    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String sessionId = memoryId.toString();
        Query query = Query.query(new Criteria().orOperator(
                Criteria.where("sessionId").is(sessionId),
                Criteria.where("userId").is(sessionId)
        ));
        ChatSession session = mongoTemplate.findOne(query, ChatSession.class);

        if (session == null) {
            session = new ChatSession();

            session.setSessionId(sessionId);
            session.setTitle("新对话");
            session.setCreatedAt(LocalDateTime.now());
        } else if (session.getSessionId() == null || session.getSessionId().isBlank()) {
            // 兼容历史文档：将旧的 userId 会话键补齐到 sessionId 字段。
            session.setSessionId(sessionId);
        }

        if (session.getMessages() == null) {
            session.setMessages(new ArrayList<>());
        }

        session.getMessages().clear();
        for (ChatMessage chatMsg : messages) {
            Message msg = new Message();
            msg.setRole(toRole(chatMsg));
            msg.setContent(extractText(chatMsg));
            msg.setTimestamp(LocalDateTime.now());
            session.getMessages().add(msg);
        }

        if (shouldGenerateTitle(session)) {
            session.setTitle(generateTitle(messages));
        }
        session.setUpdatedAt(LocalDateTime.now());
        mongoTemplate.save(session);
    }

    /**
     * 删除会话
     */
    @Override
    public void deleteMessages(Object memoryId) {
        String sessionId = memoryId.toString();
        Query query = Query.query(new Criteria().orOperator(
                Criteria.where("sessionId").is(sessionId),
                Criteria.where("userId").is(sessionId)
        ));
        mongoTemplate.remove(query, ChatSession.class);
    }

    private String toRole(ChatMessage chatMsg) {
        if (chatMsg instanceof UserMessage) {
            return "user";
        }
        if (chatMsg instanceof AiMessage) {
            return "assistant";
        }
        if (chatMsg instanceof SystemMessage) {
            return "system";
        }
        if (chatMsg instanceof ToolExecutionResultMessage) {
            // 作为助手坚持存在，以保持后续OpenAI负载有效且不包含tool_call元数据。
            return "assistant";
        }
        return chatMsg.type().name().toLowerCase();
    }

    private String extractText(ChatMessage chatMsg) {
        if (chatMsg instanceof UserMessage userMessage) {
            return userMessage.singleText();
        }
        if (chatMsg instanceof AiMessage aiMessage) {
            return aiMessage.text();
        }
        if (chatMsg instanceof SystemMessage systemMessage) {
            return systemMessage.text();
        }
        if (chatMsg instanceof ToolExecutionResultMessage toolMessage) {
            return toolMessage.text();
        }
        return chatMsg.toString();
    }

    private boolean shouldGenerateTitle(ChatSession session) {
        String title = session.getTitle();
        if (title == null || title.isBlank()) {
            return true;
        }
        String normalized = title.trim();
        return "新对话".equals(normalized) || "新会话".equals(normalized);
    }

    private String generateTitle(List<ChatMessage> messages) {
        String firstUserQuestion = messages.stream()
                .filter(UserMessage.class::isInstance)
                .map(UserMessage.class::cast)
                .map(UserMessage::singleText)
                .filter(text -> text != null && !text.isBlank())
                .findFirst()
                .orElse("");

        if (firstUserQuestion.isBlank()) {
            return "新对话";
        }

        String fallback = truncateTitle(firstUserQuestion);
        try {
            String prompt = "请把这段用户问题总结为一个简短会话标题。要求：不超过12个中文字符，不要标点，不要引号，只输出标题本身。问题："
                    + firstUserQuestion;
            String generated = titleModel.chat(prompt);
            return truncateTitle(generated);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String truncateTitle(String text) {
        if (text == null || text.isBlank()) {
            return "新对话";
        }
        String cleaned = text.replaceAll("[\\r\\n]+", " ").trim();
        if (cleaned.length() > 20) {
            return cleaned.substring(0, 20);
        }
        return cleaned;
    }
}