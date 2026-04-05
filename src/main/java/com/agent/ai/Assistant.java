package com.agent.ai;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;
import reactor.core.publisher.Flux;

// 定义AI服务接口
@AiService(
        wiringMode = AiServiceWiringMode.EXPLICIT,  // 推荐使用显式模式
        chatModel = "openAiChatModel",
        streamingChatModel = "openAiStreamingChatModel",
        chatMemoryProvider = "chatMemoryProvider",
        tools = { "chatSessionTool" }
)
public interface Assistant {

    // 定义聊天方法，通过@MemoryId实现不同用户的记忆隔离
    @SystemMessage(fromResource = "system.txt")
    String chat(@MemoryId String memoryId, @UserMessage String userMessage);

    // 添加流式聊天方法，返回Flux<String>以支持流式响应
    Flux<String> chatStream(@MemoryId String memoryId, @UserMessage String userMessage);
}
