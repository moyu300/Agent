package com.agent.config;

import com.agent.ai.Assistant;
import com.agent.ai.tool.SearchTools;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Resource
    private ChatModel qwenChatModel;

    @Resource
    private StreamingChatModel qwenStreamingChatModel;

    @Resource
    private ContentRetriever contentRetriever;

    @Resource
    private ChatMemoryProvider chatMemoryProvider;

    @Resource
    private McpToolProvider mcpToolProvider;

    @Bean
    public Assistant assistant(){
        // 构造 AI Service
        return AiServices.builder(Assistant.class)
                .chatModel(qwenChatModel) // 聊天模型
                .streamingChatModel(qwenStreamingChatModel) // 流式响应
                .chatMemoryProvider(chatMemoryProvider) // 每个会话独立存储
                .contentRetriever(contentRetriever) // RAG 检索增强生成
                .tools(new SearchTools()) // 工具调用
                .toolProvider(mcpToolProvider) // MCP 工具调用
                .build();
    }
}
