# Agent AI Assistant

基于 Spring Boot + LangChain4j + DeepSeek 的简洁问答页面（页面风格参考 DeepSeek 网页端）。

## 当前结构

- `src/main/java/com/agent/controller/ChatController.java`：聊天接口 `POST /api/chat`
- `src/main/java/com/agent/ai/AiService.java`：LangChain4j 会话记忆与模型调用
- `src/main/resources/static/index.html`：前端页面
- `src/main/resources/static/app.js`：会话管理与聊天请求逻辑
- `src/main/resources/static/styles.css`：DeepSeek 风格布局与样式

## 配置说明

- `src/main/resources/application.yaml` 中已配置 DeepSeek OpenAI 兼容地址：`https://api.deepseek.com`
- 默认读取环境变量 `DEEP_SEEK_API_KEY`（未配置时使用 `demo` 仅用于本地启动）
- 应用上下文路径为 `/api`

## 启动

```powershell
Set-Location "E:\project\Test\Agent"
.\mvnw.cmd spring-boot:run
```

启动后访问：`http://localhost:8080/api/`

## API 示例

`POST /api/chat`

```json
{
  "sessionId": "sess-001",
  "message": "你好，介绍一下你自己"
}
```
