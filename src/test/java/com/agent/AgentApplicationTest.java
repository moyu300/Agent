package com.agent;

import com.agent.ai.Assistant;
import com.agent.entity.ChatSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;

@SpringBootTest(classes = com.agent.AgentApplication.class)
public class AgentApplicationTest {

    @Autowired
    private Assistant assistant;
    @Autowired
    private MongoTemplate mongoTemplate;

    @Test
    public void testAssistant() {
        // 这里可以添加测试代码来验证Assistant的接口定义
        String result = assistant.chat("1", "你是谁");
        System.out.println("AI Response: " + result);
//        System.out.println("你好");
    }
    @Test
    public void testAssistantChat() {
        // 这里可以添加测试代码来验证Assistant的chat方法
        assistant.chatStream("1", "你是谁").subscribe(response -> {
            System.out.println("AI Response: " + response);
        });
    }

    @Test
    public void testCURD() {
        String result = assistant.chat("1", "你好，你还知道我刚才说啥吗");
        System.out.println("AI Response: " + result);

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        // 查询刚才保存的消息
        String messages = assistant.chat("1", "查询刚才的聊天历史");
        System.out.println("历史记录：" + messages);
    }

    @Test
    public void testMongoDB(){
        Query query = Query.query(Criteria.where("userId").is("1"));
        List<ChatSession> sessions = mongoTemplate.find(query, ChatSession.class);

        System.out.println("找到 " + sessions.size() + " 条记录:");
        for (ChatSession session : sessions) {
            System.out.println("ID: " + session.getId());
            System.out.println("用户 ID: " + session.getUserId());
            System.out.println("标题：" + session.getTitle());
            System.out.println("创建时间：" + session.getCreatedAt());
            System.out.println("消息数量：" + session.getMessages().size());
            System.out.println("---");
        }
    }
}
