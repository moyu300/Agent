package com.agent.config;

import com.agent.store.MongoDBChatMemoryStore;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

//    @Bean
//    public OpenAiChatModel openAiChatModel() {
//        return OpenAiChatModel.builder()
//                .apiKey("demo")
//                .modelName("gpt-3.5-turbo")
//                .build();
//    }

    @Bean
    public ChatMemoryProvider chatMemoryProvider(MongoDBChatMemoryStore chatMemoryStore) {
        // 为每个memoryId保留最多10条消息历史
        return memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .chatMemoryStore(chatMemoryStore)
                .maxMessages(10)
                .build();
    }
}
