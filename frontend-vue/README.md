# frontend-vue

基于 Vue 3 + Element Plus 的聊天前端，适配后端 `AssistantController` 接口。

## 功能

- 会话列表：查询、新建、删除
- 会话详情：加载历史消息
- 聊天：调用 `/api/chat/stream` 流式输出
- 生成中断：前端可停止当前流式请求
- 语音识别：录音后调用后端 `/api/audio/transcribe`（AI 服务商语音 API）
- 文件识别：上传图片/pdf/word/txt 等，后端调用 AI 服务商 API 识别并注入输入框

## 接口约定

后端已配置 `server.servlet.context-path: /api`，本项目默认请求：

- `GET /api/sessions`
- `GET /api/sessions/detail?sessionId=...`
- `POST /api/sessions`
- `DELETE /api/sessions?sessionId=...`
- `POST /api/chat/stream`
- `POST /api/files/recognize`
- `POST /api/audio/transcribe`

支持上传格式：

- 图片：`png/jpg/jpeg/gif/bmp/webp`
- 文档：`pdf/doc/docx/txt/md/csv/json/xml/log`

> 识别能力依赖后端 `multimodal.api.*` 配置（OpenAI 兼容接口）。

## 启动

```bash
npm install
npm run dev
```

默认访问 `http://localhost:5173`，通过 Vite 代理转发到 `http://localhost:8080`。

## 构建

```bash
npm run build
npm run preview
```

