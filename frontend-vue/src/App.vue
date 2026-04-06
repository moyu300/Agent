<script setup>
import { computed, nextTick, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  createSession,
  deleteSession,
  fetchSessionDetail,
  fetchSessions,
  recognizeFile,
  streamChat,
  transcribeAudio
} from "./api/assistant";

const sessions = ref([]);
const activeSessionId = ref("");
const messages = ref([]);
const inputText = ref("");
const loading = ref(false);
const sending = ref(false);
const recognizingFile = ref(false);
const transcribingAudio = ref(false);
const recording = ref(false);
const statusText = ref("准备就绪");
const theme = ref("light");
const messagePanelRef = ref();
let abortController = null;
let mediaRecorder = null;
let audioChunks = [];

const themeLabel = computed(() => (theme.value === "light" ? "深色模式" : "浅色模式"));

const activeSessionTitle = computed(() => {
  const found = sessions.value.find((item) => item.id === activeSessionId.value);
  return found?.title || "新对话";
});

const charCount = computed(() => inputText.value.length);

function applyTheme(nextTheme) {
  theme.value = nextTheme;
  document.documentElement.setAttribute("data-theme", nextTheme);
  localStorage.setItem("agent-theme", nextTheme);
}

function toggleTheme() {
  applyTheme(theme.value === "light" ? "dark" : "light");
}

function initTheme() {
  const saved = localStorage.getItem("agent-theme");
  if (saved === "light" || saved === "dark") {
    applyTheme(saved);
    return;
  }

  const prefersDark = window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches;
  applyTheme(prefersDark ? "dark" : "light");
}

async function handleRecordAudio() {
  if (transcribingAudio.value) {
    return;
  }

  if (recording.value && mediaRecorder) {
    mediaRecorder.stop();
    return;
  }

  if (!navigator.mediaDevices?.getUserMedia) {
    ElMessage.warning("当前浏览器不支持录音");
    return;
  }

  try {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    mediaRecorder = new MediaRecorder(stream);
    audioChunks = [];

    mediaRecorder.ondataavailable = (event) => {
      if (event.data && event.data.size > 0) {
        audioChunks.push(event.data);
      }
    };

    mediaRecorder.onstop = async () => {
      recording.value = false;
      stream.getTracks().forEach((track) => track.stop());
      if (!audioChunks.length) {
        return;
      }

      transcribingAudio.value = true;
      statusText.value = "语音识别中...";
      try {
        const blob = new Blob(audioChunks, { type: "audio/webm" });
        const result = await transcribeAudio(blob, `record-${Date.now()}.webm`);
        const transcript = result?.text || "";
        if (transcript.trim()) {
          inputText.value = `${inputText.value}${inputText.value ? "\n" : ""}${transcript.trim()}`;
          statusText.value = "语音识别完成";
          ElMessage.success("语音已转文字");
        } else {
          statusText.value = "语音识别结果为空";
          ElMessage.warning("未识别到语音文本");
        }
      } catch (error) {
        statusText.value = "语音识别失败";
        ElMessage.error(error.message || "语音识别失败");
      } finally {
        transcribingAudio.value = false;
        audioChunks = [];
      }
    };

    mediaRecorder.start();
    recording.value = true;
    statusText.value = "录音中...再次点击结束";
  } catch (error) {
    ElMessage.error(error.message || "无法启动录音");
  }
}

function appendRecognizedText(result) {
  const header = `【文件识别: ${result.fileName}】`;
  const suffix = result.truncated ? "\n（内容较长，已截断）" : "";
  const block = `${header}\n${result.text}${suffix}`;
  inputText.value = `${inputText.value}${inputText.value ? "\n\n" : ""}${block}`;
}

async function handleFileChange(uploadFile) {
  if (!uploadFile?.raw || recognizingFile.value) {
    return;
  }

  recognizingFile.value = true;
  statusText.value = "文件识别中...";
  try {
    const result = await recognizeFile(uploadFile.raw);
    appendRecognizedText(result);
    statusText.value = "文件识别完成";
    ElMessage.success(`已识别 ${result.fileName}`);
  } catch (error) {
    statusText.value = "文件识别失败";
    ElMessage.error(error.message || "文件识别失败");
  } finally {
    recognizingFile.value = false;
  }
}


function normalizeSessions(data) {
  return Array.isArray(data)
    ? data.map((item) => ({
        id: item.sessionId,
        title: item.title || "新对话",
        updatedAt: item.updatedAt || ""
      }))
    : [];
}

function normalizeMessages(data) {
  return Array.isArray(data)
    ? data.map((item, index) => ({
        id: `history-${index}-${Math.random().toString(16).slice(2, 8)}`,
        role: item.role || "assistant",
        content: item.content || ""
      }))
    : [];
}

async function scrollToBottom() {
  await nextTick();
  const panel = messagePanelRef.value;
  if (panel && panel.wrapRef) {
    panel.setScrollTop(panel.wrapRef.scrollHeight);
  }
}

function upsertSessionMeta(sessionId, title) {
  const target = sessions.value.find((item) => item.id === sessionId);
  if (target) {
    target.title = title || target.title || "新对话";
    target.updatedAt = new Date().toISOString();
  }
}

async function loadSessions() {
  const data = await fetchSessions();
  sessions.value = normalizeSessions(data);
}

async function selectSession(sessionId) {
  if (!sessionId) {
    return;
  }

  if (sending.value) {
    ElMessage.warning("请先停止当前输出");
    return;
  }

  loading.value = true;
  statusText.value = "加载会话...";
  try {
    const detail = await fetchSessionDetail(sessionId);
    activeSessionId.value = detail.sessionId;
    messages.value = normalizeMessages(detail.messages);
    upsertSessionMeta(detail.sessionId, detail.title);
    statusText.value = "准备就绪";
    await scrollToBottom();
  } finally {
    loading.value = false;
  }
}

async function initSessions() {
  loading.value = true;
  statusText.value = "加载会话...";
  try {
    await loadSessions();
    if (!sessions.value.length) {
      const created = await createSession();
      sessions.value.unshift({
        id: created.sessionId,
        title: created.title || "新对话",
        updatedAt: created.updatedAt || ""
      });
    }

    await selectSession(sessions.value[0].id);
  } catch (error) {
    statusText.value = "初始化失败";
    ElMessage.error(error.message || "初始化失败");
  } finally {
    loading.value = false;
  }
}

async function handleCreateSession() {
  try {
    const created = await createSession();
    sessions.value.unshift({
      id: created.sessionId,
      title: created.title || "新对话",
      updatedAt: created.updatedAt || ""
    });
    await selectSession(created.sessionId);
  } catch (error) {
    ElMessage.error(error.message || "新建会话失败");
  }
}

async function handleDeleteSession() {
  if (!activeSessionId.value) {
    return;
  }

  try {
    await ElMessageBox.confirm("删除后无法恢复，是否继续？", "删除会话", {
      type: "warning",
      confirmButtonText: "删除",
      cancelButtonText: "取消"
    });

    await deleteSession(activeSessionId.value);
    sessions.value = sessions.value.filter((item) => item.id !== activeSessionId.value);

    if (!sessions.value.length) {
      const created = await createSession();
      sessions.value = [
        {
          id: created.sessionId,
          title: created.title || "新对话",
          updatedAt: created.updatedAt || ""
        }
      ];
    }

    await selectSession(sessions.value[0].id);
  } catch (error) {
    if (error !== "cancel" && error !== "close") {
      ElMessage.error(error.message || "删除会话失败");
    }
  }
}

function stopChat() {
  if (abortController) {
    abortController.abort();
    abortController = null;
  }
  sending.value = false;
  statusText.value = "已停止";
}

async function handleSend() {
  const text = inputText.value.trim();
  if (!text || sending.value) {
    return;
  }

  if (!activeSessionId.value) {
    await handleCreateSession();
  }

  const userMessage = reactive({
    id: `user-${Date.now()}`,
    role: "user",
    content: text
  });
  const assistantMessage = reactive({
    id: `assistant-${Date.now()}`,
    role: "assistant",
    content: ""
  });

  messages.value.push(userMessage, assistantMessage);
  inputText.value = "";
  await scrollToBottom();

  sending.value = true;
  statusText.value = "正在生成...";
  abortController = new AbortController();
  let streamFinished = false;

  try {
    await streamChat(
      {
        sessionId: activeSessionId.value,
        message: text
      },
      abortController.signal,
      async ({ event, data }) => {
        if (event === "message") {
          assistantMessage.content += data;
          await scrollToBottom();
        } else if (event === "done") {
          streamFinished = true;
        } else if (event === "error") {
          throw new Error(data || "流式响应异常");
        }
      }
    );

    if (!streamFinished) {
      throw new Error("流式输出中断");
    }

    statusText.value = "完成";
    await loadSessions();
    const detail = await fetchSessionDetail(activeSessionId.value);
    messages.value = normalizeMessages(detail.messages);
    upsertSessionMeta(detail.sessionId, detail.title);
    await scrollToBottom();
  } catch (error) {
    if (error.name === "AbortError") {
      statusText.value = "已停止";
    } else {
      assistantMessage.content = `请求失败: ${error.message || error}`;
      statusText.value = "请求失败";
      ElMessage.error(error.message || "发送失败");
    }
  } finally {
    sending.value = false;
    abortController = null;
  }
}

onMounted(() => {
  initTheme();
  initSessions();
});
</script>

<template>
  <el-container class="page">
    <el-aside class="sidebar" width="280px">
      <div class="brand-wrap">
        <div class="brand">Agent</div>
        <p class="brand-subtitle">你的智能会话工作台</p>
      </div>

      <el-button type="primary" class="full-width" @click="handleCreateSession">+ 新对话</el-button>

      <div class="session-meta">共 {{ sessions.length }} 个会话</div>

      <el-scrollbar class="session-list">
        <div
          v-for="session in sessions"
          :key="session.id"
          class="session-item"
          :class="{ active: session.id === activeSessionId }"
          @click="selectSession(session.id)"
        >
          <div class="session-title">{{ session.title }}</div>
          <div class="session-time">{{ session.updatedAt || "--" }}</div>
        </div>
      </el-scrollbar>
    </el-aside>

    <el-main class="main">
      <div class="header">
        <div class="header-main">
          <h2>{{ activeSessionTitle }}</h2>
          <span class="status-text">{{ statusText }}</span>
        </div>
        <div class="header-actions">
          <el-button @click="toggleTheme">{{ themeLabel }}</el-button>
          <el-button :disabled="loading || sending" @click="handleDeleteSession">删除会话</el-button>
        </div>
      </div>

      <el-scrollbar ref="messagePanelRef" class="message-panel" v-loading="loading">
        <div v-if="!messages.length" class="empty-tip">开始提问吧，我会记住这个会话的历史。</div>
        <div v-for="item in messages" :key="item.id" class="message-row" :class="item.role">
          <div class="bubble">{{ item.content }}</div>
        </div>
      </el-scrollbar>

      <div class="composer">
        <el-input
          v-model="inputText"
          type="textarea"
          :rows="4"
          resize="none"
          placeholder="输入你的问题，Enter 发送，Shift+Enter 换行"
          @keydown.enter.exact.prevent="handleSend"
        />
        <div class="composer-footer">
          <span class="composer-hint">{{ charCount }} 字 · Enter 发送 · Shift+Enter 换行</span>
          <div class="composer-actions">
            <el-upload
              :auto-upload="false"
              :show-file-list="false"
              accept=".png,.jpg,.jpeg,.gif,.bmp,.webp,.pdf,.doc,.docx,.txt,.md,.csv,.json,.xml,.log"
              :on-change="handleFileChange"
            >
              <el-button :loading="recognizingFile">上传识别</el-button>
            </el-upload>
            <el-button :loading="transcribingAudio" :type="recording ? 'warning' : 'default'" @click="handleRecordAudio">
              {{ recording ? "结束录音" : "语音输入" }}
            </el-button>
            <el-button :disabled="!sending" @click="stopChat">停止</el-button>
            <el-button type="primary" :loading="sending" @click="handleSend">发送</el-button>
          </div>
        </div>
      </div>
    </el-main>
  </el-container>
</template>

