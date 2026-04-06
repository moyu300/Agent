package com.agent.ai.memory;

import com.agent.store.MongoDBChatMemoryStore;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MemoryConfig {

    @Resource
    private MongoDBChatMemoryStore chatMemoryStore;

    @Bean
    public ChatMemoryProvider chatMemoryProvider() {
        // 为每个memoryId保留最多10条消息历史
        return memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .chatMemoryStore(chatMemoryStore)
                .maxMessages(10)
                .build();
    }
}
