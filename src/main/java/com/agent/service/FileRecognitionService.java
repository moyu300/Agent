package com.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Slf4j
@Service
public class FileRecognitionService {

    private static final int MAX_TEXT_LENGTH = 12000;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${multimodal.api.base-url:https://api.deepseek.com}")
    private String apiBaseUrl;

    @Value("${multimodal.api.api-key:}")
    private String apiKey;

    @Value("${multimodal.api.chat-model:deepseek-chat}")
    private String multimodalChatModel;

    @Value("${multimodal.api.audio-model:whisper-1}")
    private String audioModel;

    public FileRecognitionResult recognize(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "上传文件不能为空");
        }

        String fileName = StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "unknown";
        String extension = extensionOf(fileName);
        String mediaType = file.getContentType();

        try {
            String recognized = switch (extension) {
                case "txt", "md", "csv", "json", "xml", "log" -> summarizeTextByAi(fileName, new String(file.getBytes(), StandardCharsets.UTF_8));
                case "pdf" -> summarizeTextByAi(fileName, extractPdf(file.getBytes()));
                case "docx" -> summarizeTextByAi(fileName, extractDocx(file.getBytes()));
                case "doc" -> summarizeTextByAi(fileName, extractDoc(file.getBytes()));
                case "jpg", "jpeg", "png", "gif", "bmp", "webp" -> analyzeImageByAi(fileName, mediaType, file.getBytes());
                default -> throw new ResponseStatusException(BAD_REQUEST, "暂不支持的文件类型: " + extension);
            };

            String normalized = StringUtils.hasText(recognized) ? recognized.trim() : "未识别到可用文本内容。";
            boolean truncated = normalized.length() > MAX_TEXT_LENGTH;
            String finalText = truncated ? normalized.substring(0, MAX_TEXT_LENGTH) : normalized;
            return new FileRecognitionResult(fileName, mediaType, finalText, truncated);
        } catch (IOException ex) {
            log.error("文件识别失败: {}", fileName, ex);
            throw new ResponseStatusException(BAD_REQUEST, "文件解析失败: " + ex.getMessage());
        }
    }

    public String transcribeAudio(MultipartFile audioFile) {
        if (audioFile == null || audioFile.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "音频文件不能为空");
        }
        ensureApiKey();

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("model", audioModel);
            body.add("language", "zh");
            body.add("file", new ByteArrayResource(audioFile.getBytes()) {
                @Override
                public String getFilename() {
                    return StringUtils.hasText(audioFile.getOriginalFilename()) ? audioFile.getOriginalFilename() : "audio.webm";
                }
            });

            HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(apiBaseUrl + "/v1/audio/transcriptions", request, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            String text = root.path("text").asText("");
            if (!StringUtils.hasText(text)) {
                throw new ResponseStatusException(BAD_REQUEST, "语音识别结果为空");
            }
            return text.trim();
        } catch (Exception ex) {
            throw new ResponseStatusException(BAD_REQUEST, "语音识别失败: " + ex.getMessage());
        }
    }

    private String summarizeTextByAi(String fileName, String text) {
        if (!StringUtils.hasText(text)) {
            return "未识别到文本内容。";
        }

        String clipped = text.length() > MAX_TEXT_LENGTH ? text.substring(0, MAX_TEXT_LENGTH) : text;
        String prompt = "你是文档内容提取助手。请读取以下文件文本并输出：1) 关键信息摘要；2) 可直接用于继续提问的原文要点。文件名: "
                + fileName + "\n\n文本内容:\n" + clipped;
        return chatCompletion(prompt);
    }

    private String analyzeImageByAi(String fileName, String mediaType, byte[] bytes) {
        ensureApiKey();

        try {
            String mime = StringUtils.hasText(mediaType) ? mediaType : "image/png";
            String dataUrl = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);

            Map<String, Object> textContent = Map.of("type", "text", "text",
                    "请识别这张图片中的文字和关键信息，并用中文输出。文件名: " + fileName);
            Map<String, Object> imageContent = Map.of("type", "image_url", "image_url", Map.of("url", dataUrl));

            Map<String, Object> message = new HashMap<>();
            message.put("role", "user");
            message.put("content", List.of(textContent, imageContent));

            Map<String, Object> payload = new HashMap<>();
            payload.put("model", multimodalChatModel);
            payload.put("messages", List.of(message));
            payload.put("temperature", 0.2);

            HttpHeaders headers = jsonHeaders();
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(apiBaseUrl + "/v1/chat/completions", request, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            return root.path("choices").path(0).path("message").path("content").asText("未返回识别结果").trim();
        } catch (Exception ex) {
            throw new ResponseStatusException(BAD_REQUEST, "图片识别失败: " + ex.getMessage());
        }
    }

    private String chatCompletion(String prompt) {
        ensureApiKey();

        try {
            Map<String, Object> message = Map.of("role", "user", "content", prompt);
            Map<String, Object> payload = Map.of(
                    "model", multimodalChatModel,
                    "messages", List.of(message),
                    "temperature", 0.2
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, jsonHeaders());
            ResponseEntity<String> response = restTemplate.postForEntity(apiBaseUrl + "/v1/chat/completions", request, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            return root.path("choices").path(0).path("message").path("content").asText("未返回识别结果").trim();
        } catch (Exception ex) {
            throw new ResponseStatusException(BAD_REQUEST, "文档识别失败: " + ex.getMessage());
        }
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        return headers;
    }

    private void ensureApiKey() {
        if (!StringUtils.hasText(apiKey)) {
            throw new ResponseStatusException(BAD_REQUEST, "未配置 multimodal.api.api-key");
        }
    }

    private String extractPdf(byte[] bytes) throws IOException {
        try (PDDocument document = PDDocument.load(bytes)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private String extractDocx(byte[] bytes) throws IOException {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    private String extractDoc(byte[] bytes) throws IOException {
        try (HWPFDocument document = new HWPFDocument(new ByteArrayInputStream(bytes));
             WordExtractor extractor = new WordExtractor(document)) {
            return extractor.getText();
        }
    }

    private String extensionOf(String fileName) {
        int idx = fileName.lastIndexOf('.');
        if (idx < 0 || idx == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(idx + 1).toLowerCase(Locale.ROOT);
    }

    public record FileRecognitionResult(String fileName, String mediaType, String text, boolean truncated) {
    }
}

