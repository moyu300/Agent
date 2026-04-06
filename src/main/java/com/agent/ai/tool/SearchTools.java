package com.agent.ai.tool;

import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
public class SearchTools {

    private static final String BAIDU_SEARCH_API = "https://www.baidu.com/s?wd=";
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Tool("使用百度进行联网搜索，获取实时信息、新闻、知识等内容")
    public String baiduSearch(String query) {
        // 开始时间
        LocalDateTime startTime = LocalDateTime.now();
        long startMs = System.currentTimeMillis();
        String startStr = startTime.format(formatter);

        log.info("[{}] 开始执行百度搜索：{}", startStr, query);

        try {
            String encodedQuery = URLEncoder.encode(query, "UTF-8");
            String urlStr = BAIDU_SEARCH_API + encodedQuery;

            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            conn.disconnect();

            String html = sb.toString();
            int start = Math.min(2000, html.length());
            int end = Math.min(8000, html.length());

            // 耗时
            long costMs = System.currentTimeMillis() - startMs;

            return "【百度搜索结果】\n时间：" + startStr +
                    "\n关键词：" + query +
                    "\n耗时：" + costMs + "ms" +
                    "\n摘要：\n" + html.substring(start, end);

        } catch (Exception e) {
            long costMs = System.currentTimeMillis() - startMs;
            log.error("百度搜索异常：{}，耗时：{}ms", e.getMessage(), costMs);
            return "【" + startStr + "】百度搜索失败（耗时 " + costMs + "ms）：" + e.getMessage();
        } finally {
            long costMs = System.currentTimeMillis() - startMs;
            log.info("[{}] 百度搜索结束，总耗时：{} ms", LocalDateTime.now().format(formatter), costMs);
        }
    }
}