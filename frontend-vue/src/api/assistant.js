const API = {
  sessions: "/api/sessions",
  sessionDetail: "/api/sessions/detail",
  chatStream: "/api/chat/stream",
  recognizeFile: "/api/files/recognize",
  transcribeAudio: "/api/audio/transcribe"
};

async function requestJson(url, options = {}) {
  const response = await fetch(url, options);
  if (!response.ok) {
    throw new Error(`HTTP ${response.status} ${response.statusText}`);
  }

  const payload = await response.json();
  if (!payload.success) {
    throw new Error(payload.message || "请求失败");
  }

  return payload.data;
}

export function fetchSessions() {
  return requestJson(API.sessions);
}

export function fetchSessionDetail(sessionId) {
  return requestJson(`${API.sessionDetail}?sessionId=${encodeURIComponent(sessionId)}`);
}

export function createSession() {
  return requestJson(API.sessions, { method: "POST" });
}

export function deleteSession(sessionId) {
  return requestJson(`${API.sessions}?sessionId=${encodeURIComponent(sessionId)}`, {
    method: "DELETE"
  });
}

export function recognizeFile(file) {
  const formData = new FormData();
  formData.append("file", file);
  return requestJson(API.recognizeFile, {
    method: "POST",
    body: formData
  });
}

export function transcribeAudio(fileBlob, fileName = "audio.webm") {
  const formData = new FormData();
  formData.append("file", fileBlob, fileName);
  return requestJson(API.transcribeAudio, {
    method: "POST",
    body: formData
  });
}


export async function streamChat(payload, signal, onEvent) {
  const response = await fetch(API.chatStream, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
    signal
  });

  if (!response.ok || !response.body) {
    throw new Error(`HTTP ${response.status} ${response.statusText}`);
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder("utf-8");
  let buffer = "";

  const emitChunk = (chunk) => {
    if (!chunk || !chunk.trim()) {
      return;
    }

    const lines = chunk.split("\n");
    let event = "message";
    const dataLines = [];

    for (const rawLine of lines) {
      const line = rawLine.trimEnd();
      if (!line || line.startsWith(":")) {
        continue;
      }
      if (line.startsWith("event:")) {
        event = line.slice(6).trim();
      } else if (line.startsWith("data:")) {
        const raw = line.slice(5);
        dataLines.push(raw.startsWith(" ") ? raw.slice(1) : raw);
      }
    }

    onEvent({ event, data: dataLines.join("\n") });
  };

  while (true) {
    const { value, done } = await reader.read();
    if (done) {
      break;
    }

    buffer += decoder.decode(value, { stream: true });

    // Support both "\n\n" and "\r\n\r\n" SSE frame separators.
    const separator = /\r?\n\r?\n/;
    const chunks = buffer.split(separator);
    const frames = chunks.slice(0, -1);
    buffer = chunks[chunks.length - 1] || "";

    for (const chunk of frames) {
      emitChunk(chunk);
    }
  }

  // Flush remaining partial chunk after stream completes.
  const tail = buffer + decoder.decode();
  emitChunk(tail);
}

