package com.agent.ai.mcp;

import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Map;

@Configuration
public class McpConfig {

    @Value("${aliyun.api-key:}")
    private String apiKey;

    @Value("${aliyun.mcp-sse-url:${langchain4j.mcp.servers.aliyun-maps.url:}}")
    private String mcpSseUrl;

    @Value("${aliyun.timeout-seconds:120}")
    private long timeoutSeconds;

    @Value("${aliyun.mcp-log-requests:false}")
    private boolean logRequests;

    @Value("${aliyun.mcp-log-responses:false}")
    private boolean logResponses;

    @Bean
    public McpToolProvider mcpToolProvider() {
        if (!StringUtils.hasText(mcpSseUrl)) {
            throw new IllegalStateException("MCP 网址缺失。配置 'aliyun.mcp-sse-url' 或 'langchain4j.mcp.servers.aliyun-maps.url'。");
        }
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("缺少Aliyun API。配置。'Aliyun. Api-ki'.");
        }

        McpTransport transport = StreamableHttpMcpTransport.builder()
                .url(mcpSseUrl.trim())
                .customHeaders(Map.of("Authorization", buildAuthorizationHeader(apiKey)))
                .timeout(Duration.ofSeconds(Math.max(10, timeoutSeconds)))
                .logRequests(logRequests)
                .logResponses(logResponses)
                .build();

        McpClient mcpClient = DefaultMcpClient.builder()
                .key("aliyun-mcp-client")
                .transport(transport)
                .build();

        return McpToolProvider.builder()
                .mcpClients(mcpClient)
                .build();
    }

    private String buildAuthorizationHeader(String rawApiKey) {
        String trimmed = rawApiKey.trim();
        if (trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return trimmed;
        }
        return "Bearer " + trimmed;
    }
}
