package com.agent.store;

import com.agent.entity.ChatSession;
import com.agent.entity.Message;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
//import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.tool.ToolExecution;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
@Slf4j
public class MongoDBChatMemoryStore implements ChatMemoryStore {

    private final MongoTemplate mongoTemplate;
    @Qualifier("qwenChatModel")
    private final ChatModel titleModel;

    @Autowired
    public MongoDBChatMemoryStore(MongoTemplate mongoTemplate, ChatModel titleModel) {
        this.mongoTemplate = mongoTemplate;
        this.titleModel = titleModel;
    }

    /**
     * 根据 memoryId（即 sessionId）获取完整对话历史
     */
    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String sessionId = memoryId.toString();
        ChatSession session = findBySessionIdOrLegacy(sessionId);

        if (session == null || session.getMessages() == null) {
            log.info("读取会话历史, sessionId={}, messagesCount=0", sessionId);
            return List.of();
        }

        List<ChatMessage> mapped = session.getMessages().stream()
                .map(msg -> {
                    String role = msg.getRole() == null ? "" : msg.getRole().trim().toLowerCase(Locale.ROOT);
                    String content = msg.getContent() == null ? "" : msg.getContent();
                    return switch (role) {
                        case "user" -> UserMessage.from(content);
                        case "assistant", "ai" -> AiMessage.from(content);
                        case "system" -> SystemMessage.from(content);
                        case "tool" -> AiMessage.from(content);
                        // 避免在持久化数据中出现历史/未知角色值时崩溃
                        default -> AiMessage.from(content);
                    };
                }).toList();
        log.info("读取会话历史, sessionId={}, messagesCount={}", sessionId, mapped.size());
        return mapped;
    }

    /**
     * 更新对话历史（LangChain4j 自动调用）
     */
    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String sessionId = memoryId.toString();
        ChatSession session = findBySessionIdOrLegacy(sessionId);

        List<Message> persisted = session == null || session.getMessages() == null
                ? List.of()
                : session.getMessages();
        List<Message> increments = toIncrementalMessages(persisted, messages);

        LocalDateTime now = LocalDateTime.now();
        Query writeQuery = session != null && session.getId() != null
                ? Query.query(Criteria.where("_id").is(session.getId()))
                : Query.query(Criteria.where("sessionId").is(sessionId));

        Update update = new Update()
                .set("updatedAt", now)
                .setOnInsert("sessionId", sessionId)
                .setOnInsert("createdAt", now);

        boolean needBackfillSessionId = session != null
                && (session.getSessionId() == null || session.getSessionId().isBlank());
        if (needBackfillSessionId) {
            // 兼容旧文档，补齐 sessionId，后续查询直接命中 sessionId。
            update.set("sessionId", sessionId);
        }

        boolean needGenerateTitle = session == null || shouldGenerateTitle(session);
        if (needGenerateTitle) {
            update.set("title", generateTitle(messages));
        }

        if (!increments.isEmpty()) {
            update.push("messages").each(increments.toArray());
        }

        mongoTemplate.upsert(writeQuery, update, ChatSession.class);
        log.info("持久化会话历史, sessionId={}, incomingWindow={}, appended={}, totalBefore={}",
                sessionId, messages == null ? 0 : messages.size(), increments.size(), persisted.size());
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
        long deleted = mongoTemplate.remove(query, ChatSession.class).getDeletedCount();
        log.info("删除会话历史, sessionId={}, deleted={}", sessionId, deleted);
    }

    private ChatSession findBySessionIdOrLegacy(String sessionId) {
        ChatSession bySessionId = mongoTemplate.findOne(
                Query.query(Criteria.where("sessionId").is(sessionId)),
                ChatSession.class
        );
        if (bySessionId != null) {
            return bySessionId;
        }
        return mongoTemplate.findOne(
                Query.query(Criteria.where("userId").is(sessionId)),
                ChatSession.class
        );
    }

    private List<Message> toIncrementalMessages(List<Message> persisted, List<ChatMessage> incoming) {
        if (incoming == null || incoming.isEmpty()) {
            return List.of();
        }

        int overlap = findSuffixPrefixOverlap(persisted, incoming);
        List<Message> result = new ArrayList<>(Math.max(0, incoming.size() - overlap));
        for (int i = overlap; i < incoming.size(); i++) {
            ChatMessage chatMsg = incoming.get(i);
            Message msg = new Message();
            msg.setRole(toRole(chatMsg));
            msg.setContent(extractText(chatMsg));
            msg.setTimestamp(LocalDateTime.now());
            result.add(msg);
        }
        return result;
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
            // 持久化层保留 tool 角色，便于会话详情区分来源。
            return "tool";
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

    private int findSuffixPrefixOverlap(List<Message> persisted, List<ChatMessage> incoming) {
        if (persisted == null || persisted.isEmpty() || incoming == null || incoming.isEmpty()) {
            return 0;
        }

        int max = Math.min(persisted.size(), incoming.size());
        for (int overlap = max; overlap > 0; overlap--) {
            boolean match = true;
            int start = persisted.size() - overlap;

            for (int i = 0; i < overlap; i++) {
                Message oldMsg = persisted.get(start + i);
                ChatMessage newMsg = incoming.get(i);

                String oldRole = normalize(oldMsg.getRole());
                String newRole = normalize(toRole(newMsg));
                String oldContent = normalize(oldMsg.getContent());
                String newContent = normalize(extractText(newMsg));

                if (!oldRole.equals(newRole) || !oldContent.equals(newContent)) {
                    match = false;
                    break;
                }
            }

            if (match) {
                return overlap;
            }
        }
        return 0;
    }

    private String normalize(String text) {
        return text == null ? "" : text.trim();
    }
}