package com.agent.controller;

import com.agent.ai.Assistant;
import com.agent.entity.ChatSession;
import com.agent.repository.ChatSessionRepository;
import com.agent.service.FileRecognitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
public class AssistantController {

    private final Assistant assistant;
    private final ChatSessionRepository chatSessionRepository;
    private final FileRecognitionService fileRecognitionService;

    @GetMapping("/sessions")
    public ApiResponse<List<SessionSummary>> sessions(@RequestParam(required = false) String userId) {
        log.info("查询会话列表, userId={}", StringUtils.hasText(userId) ? userId.trim() : "ALL");
        List<ChatSession> sessions = StringUtils.hasText(userId)
                ? chatSessionRepository.findByUserIdOrderByUpdatedAtDesc(userId.trim())
                : chatSessionRepository.findAllByOrderByUpdatedAtDesc();

        List<SessionSummary> data = sessions.stream()
                .sorted(Comparator.comparing(ChatSession::getUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .map(this::toSummary)
                .toList();
        log.info("会话列表查询完成, count={}", data.size());
        return ApiResponse.success(data);
    }

    @GetMapping("/sessions/detail")
    public ApiResponse<SessionDetail> sessionDetail(@RequestParam String sessionId) {
        log.info("查询会话详情, sessionId={}", sessionId);
        ChatSession session = findBySessionIdOrLegacy(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在"));
        log.info("会话详情查询完成, sessionId={}, messagesCount={}", sessionId, session.getMessages() == null ? 0 : session.getMessages().size());
        return ApiResponse.success(toDetail(session));
    }

    @PostMapping("/sessions")
    public ApiResponse<SessionSummary> createSession() {
        log.info("创建新会话");
        String sessionId = String.valueOf(System.currentTimeMillis());
        ChatSession session = new ChatSession();
        session.setSessionId(sessionId);
        session.setTitle("新对话");
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        ChatSession saved = chatSessionRepository.save(session);
        log.info("新会话创建完成, sessionId={}", sessionId);
        return ApiResponse.success(toSummary(saved));
    }

    @DeleteMapping("/sessions")
    public ApiResponse<Void> deleteSession(@RequestParam String sessionId) {
        log.info("删除会话, sessionId={}", sessionId);
        findBySessionIdOrLegacy(sessionId).ifPresent(chatSessionRepository::delete);
        log.info("会话删除完成, sessionId={}", sessionId);
        return ApiResponse.success(null);
    }

    @PostMapping("/chat")
    public ApiResponse<ChatResponse> chat(@RequestBody ChatRequest request) {
        if (request == null || !StringUtils.hasText(request.message())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message 不能为空");
        }
        log.info("用户提问, sessionId={}, message={}", request.sessionId(), request.message());

        String sessionId = StringUtils.hasText(request.sessionId())
                ? request.sessionId().trim()
                : String.valueOf(System.currentTimeMillis());

        String answer = assistant.chat(sessionId, request.message().trim());
        ChatSession session = findBySessionIdOrLegacy(sessionId).orElse(null);
        String title = session != null && StringUtils.hasText(session.getTitle()) ? session.getTitle() : "新对话";
        log.info("用户提问完成, sessionId={}, answer={}", sessionId, answer);
        return ApiResponse.success(new ChatResponse(sessionId, request.message().trim(), answer, title));
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(@RequestBody ChatRequest request) {
        if (request == null || !StringUtils.hasText(request.message())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message 不能为空");
        }
        log.info("Stream 用户提问, sessionId={}, message={}", request.sessionId(), request.message());

        String sessionId = StringUtils.hasText(request.sessionId())
                ? request.sessionId().trim()
                : String.valueOf(System.currentTimeMillis());
        log.info("用户提问完成, sessionId={}", sessionId);
        return assistant.chatStream(sessionId, request.message().trim())
                .map(token -> ServerSentEvent.<String>builder()
                        .event("message")
                        .data(token)
                        .build())
                .concatWith(Flux.just(ServerSentEvent.<String>builder()
                        .event("done")
                        .data("[DONE]")
                        .build()))
                .onErrorResume(ex -> Flux.just(ServerSentEvent.<String>builder()
                        .event("error")
                        .data(ex.getMessage())
                        .build()));
    }

    @PostMapping(value = "/files/recognize", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FileRecognizeResponse> recognizeFile(@RequestParam("file") MultipartFile file) {
        log.info("文件识别, fileName={}", file.getOriginalFilename());
        FileRecognitionService.FileRecognitionResult result = fileRecognitionService.recognize(file);
        return ApiResponse.success(new FileRecognizeResponse(
                result.fileName(),
                result.mediaType(),
                result.text(),
                result.truncated()
        ));
    }

    @PostMapping(value = "/audio/transcribe", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AudioTranscribeResponse> transcribeAudio(@RequestParam("file") MultipartFile file) {
        log.info("音频转录, fileName={}", file.getOriginalFilename());
        String text = fileRecognitionService.transcribeAudio(file);
        return ApiResponse.success(new AudioTranscribeResponse(text));
    }

    private SessionSummary toSummary(ChatSession session) {
        return new SessionSummary(
                resolvedSessionId(session),
                StringUtils.hasText(session.getTitle()) ? session.getTitle() : "新对话",
                session.getUpdatedAt() == null ? null : session.getUpdatedAt().toString()
        );
    }

    private SessionDetail toDetail(ChatSession session) {
        List<MessageView> messages = session.getMessages() == null ? List.of() : session.getMessages().stream()
                .map(message -> new MessageView(
                        message.getRole(),
                        message.getContent(),
                        message.getTimestamp() == null ? null : message.getTimestamp().toString()
                ))
                .toList();
        return new SessionDetail(
                resolvedSessionId(session),
                StringUtils.hasText(session.getTitle()) ? session.getTitle() : "新对话",
                session.getCreatedAt() == null ? null : session.getCreatedAt().toString(),
                session.getUpdatedAt() == null ? null : session.getUpdatedAt().toString(),
                messages
        );
    }

    private java.util.Optional<ChatSession> findBySessionIdOrLegacy(String sessionId) {
        return chatSessionRepository.findFirstBySessionId(sessionId)
                .or(() -> chatSessionRepository.findFirstByUserId(sessionId));
    }

    private String resolvedSessionId(ChatSession session) {
        if (StringUtils.hasText(session.getSessionId())) {
            return session.getSessionId();
        }
        return session.getUserId();
    }

    public record ChatRequest(String sessionId, String message) {
    }

    public record ChatResponse(String sessionId, String question, String answer, String title) {
    }

    public record SessionSummary(String sessionId, String title, String updatedAt) {
    }

    public record SessionDetail(String sessionId, String title, String createdAt, String updatedAt, List<MessageView> messages) {
    }

    public record MessageView(String role, String content, String time) {
    }

    public record FileRecognizeResponse(String fileName, String mediaType, String text, boolean truncated) {
    }

    public record AudioTranscribeResponse(String text) {
    }

    public record ApiResponse<T>(boolean success, String message, T data) {
        public static <T> ApiResponse<T> success(T data) {
            return new ApiResponse<>(true, "ok", data);
        }
    }
}
