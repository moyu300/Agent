const API = {
    sessions: "/api/sessions",
    sessionDetail: "/api/sessions/detail",
    chat: "/api/chat",
    chatStream: "/api/chat/stream"
};

const els = {
    sessionList: document.getElementById("sessionList"),
    newSessionBtn: document.getElementById("newSessionBtn"),
    deleteSessionBtn: document.getElementById("deleteSessionBtn"),
    titleText: document.getElementById("titleText"),
    statusText: document.getElementById("statusText"),
    messageList: document.getElementById("messageList"),
    promptInput: document.getElementById("promptInput"),
    charCount: document.getElementById("charCount"),
    sendBtn: document.getElementById("sendBtn"),
    stopBtn: document.getElementById("stopBtn")
};

const state = {
    sessions: [],
    activeSessionId: null,
    activeMessages: [],
    isSending: false,
    reader: null
};

function setStatus(text) {
    els.statusText.textContent = text;
}

function updateComposer() {
    els.sendBtn.disabled = state.isSending;
    els.stopBtn.disabled = !state.isSending;
}

function updateCharCount() {
    els.charCount.textContent = `${els.promptInput.value.length} 字`;
}

async function requestJson(url, options = {}) {
    const res = await fetch(url, options);
    if (!res.ok) {
        throw new Error(`HTTP ${res.status} ${res.statusText}`);
    }
    const payload = await res.json();
    if (!payload.success) {
        throw new Error(payload.message || "请求失败");
    }
    return payload.data;
}

async function loadSessions() {
    const data = await requestJson(API.sessions);
    state.sessions = Array.isArray(data)
        ? data.map((item) => ({
            id: item.sessionId,
            title: item.title || "新对话",
            updatedAt: item.updatedAt
        }))
        : [];
}

async function loadSessionDetail(sessionId) {
    const data = await requestJson(`${API.sessionDetail}?sessionId=${encodeURIComponent(sessionId)}`);
    state.activeSessionId = data.sessionId;
    state.activeMessages = Array.isArray(data.messages)
        ? data.messages.map((m, index) => ({
            id: `${data.sessionId}-${index}`,
            role: m.role || "assistant",
            content: m.content || ""
        }))
        : [];

    const current = state.sessions.find((s) => s.id === data.sessionId);
    if (current) {
        current.title = data.title || current.title || "新对话";
    }
}

async function createSession() {
    const data = await requestJson(API.sessions, { method: "POST" });
    state.sessions.unshift({
        id: data.sessionId,
        title: data.title || "新对话",
        updatedAt: data.updatedAt
    });
    state.activeSessionId = data.sessionId;
    state.activeMessages = [];
}

async function deleteActiveSession() {
    if (!state.activeSessionId) return;
    await requestJson(`${API.sessions}?sessionId=${encodeURIComponent(state.activeSessionId)}`, {
        method: "DELETE"
    });
    state.sessions = state.sessions.filter((s) => s.id !== state.activeSessionId);
    if (state.sessions.length > 0) {
        await loadSessionDetail(state.sessions[0].id);
    } else {
        await createSession();
    }
}

function renderSessions() {
    els.sessionList.innerHTML = "";
    for (const session of state.sessions) {
        const li = document.createElement("li");
        const btn = document.createElement("button");
        btn.type = "button";
        btn.className = "session-btn";
        if (session.id === state.activeSessionId) {
            btn.classList.add("active");
        }
        btn.textContent = session.title || "新对话";
        btn.addEventListener("click", async () => {
            try {
                setStatus("加载会话...");
                await loadSessionDetail(session.id);
                renderAll();
                setStatus("准备就绪");
            } catch (e) {
                setStatus(`加载失败: ${e.message || e}`);
            }
        });
        li.appendChild(btn);
        els.sessionList.appendChild(li);
    }
}

function renderMessages() {
    const active = state.sessions.find((s) => s.id === state.activeSessionId);
    els.titleText.textContent = active?.title || "新对话";
    els.messageList.innerHTML = "";

    if (!state.activeMessages.length) {
        const tip = document.createElement("div");
        tip.className = "empty-tip";
        tip.textContent = "开始提问吧，我会记住这个会话的历史。";
        els.messageList.appendChild(tip);
        return;
    }

    for (const msg of state.activeMessages) {
        const row = document.createElement("div");
        row.className = `message-row ${msg.role}`;
        const bubble = document.createElement("div");
        bubble.className = "bubble";
        bubble.textContent = msg.content;
        row.appendChild(bubble);
        els.messageList.appendChild(row);
    }

    els.messageList.scrollTop = els.messageList.scrollHeight;
}

function renderAll() {
    renderSessions();
    renderMessages();
    updateCharCount();
    updateComposer();
}

function addMessage(role, content) {
    const item = {
        id: `m-${Date.now()}-${Math.random().toString(16).slice(2, 7)}`,
        role,
        content
    };
    state.activeMessages.push(item);
    renderMessages();
    return item;
}

function updateMessageContent(id, content) {
    const target = state.activeMessages.find((m) => m.id === id);
    if (target) {
        target.content = content;
        renderMessages();
    }
}

async function sendMessage() {
    const text = els.promptInput.value.trim();
    if (!text || state.isSending) return;

    if (!state.activeSessionId) {
        await createSession();
    }

    addMessage("user", text);
    const assistantMsg = addMessage("assistant", "");
    els.promptInput.value = "";
    updateCharCount();

    state.isSending = true;
    updateComposer();
    setStatus("正在生成...");

    try {
        const response = await fetch(API.chatStream, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ sessionId: state.activeSessionId, message: text })
        });

        if (!response.ok || !response.body) {
            throw new Error(`HTTP ${response.status} ${response.statusText}`);
        }

        state.reader = response.body.getReader();
        const decoder = new TextDecoder("utf-8");
        let buffer = "";
        let merged = "";

        while (true) {
            const { value, done } = await state.reader.read();
            if (done) break;
            buffer += decoder.decode(value, { stream: true });

            const chunks = buffer.split("\n\n");
            buffer = chunks.pop() || "";

            for (const chunk of chunks) {
                const lines = chunk.split("\n");
                let event = "message";
                let data = "";

                for (const line of lines) {
                    if (line.startsWith("event:")) {
                        event = line.slice(6).trim();
                    } else if (line.startsWith("data:")) {
                        data += line.slice(5);
                    }
                }

                if (event === "message") {
                    merged += data;
                    updateMessageContent(assistantMsg.id, merged);
                } else if (event === "done") {
                    break;
                } else if (event === "error") {
                    throw new Error(data || "流式响应异常");
                }
            }
        }

        setStatus("完成");
        await loadSessions();
        await loadSessionDetail(state.activeSessionId);
        renderAll();
    } catch (e) {
        updateMessageContent(assistantMsg.id, `请求失败: ${e.message || e}`);
        setStatus("请求失败");
    } finally {
        state.isSending = false;
        state.reader = null;
        updateComposer();
    }
}

function stopStream() {
    if (state.reader) {
        state.reader.cancel();
        state.reader = null;
    }
    state.isSending = false;
    updateComposer();
    setStatus("已停止");
}

async function bootstrap() {
    try {
        setStatus("加载会话...");
        await loadSessions();
        if (!state.sessions.length) {
            await createSession();
        }
        await loadSessionDetail(state.sessions[0].id);
        setStatus("准备就绪");
    } catch (e) {
        setStatus(`初始化失败: ${e.message || e}`);
    }

    renderAll();

    els.newSessionBtn.addEventListener("click", async () => {
        try {
            await createSession();
            renderAll();
        } catch (e) {
            setStatus(`新建失败: ${e.message || e}`);
        }
    });

    els.deleteSessionBtn.addEventListener("click", async () => {
        try {
            await deleteActiveSession();
            renderAll();
        } catch (e) {
            setStatus(`删除失败: ${e.message || e}`);
        }
    });

    els.sendBtn.addEventListener("click", () => { sendMessage(); });
    els.stopBtn.addEventListener("click", stopStream);
    els.promptInput.addEventListener("input", updateCharCount);
    els.promptInput.addEventListener("keydown", (event) => {
        if (event.key === "Enter" && !event.shiftKey) {
            event.preventDefault();
            sendMessage();
        }
    });
}

bootstrap();

