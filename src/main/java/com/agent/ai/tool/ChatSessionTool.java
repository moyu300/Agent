//package com.agent.ai.tool;
//
//import dev.langchain4j.agent.tool.Tool;
//import lombok.extern.slf4j.Slf4j;
//import org.apache.pdfbox.pdmodel.PDDocument;
//import org.apache.pdfbox.pdmodel.PDPage;
//import org.apache.pdfbox.pdmodel.PDPageContentStream;
//import org.apache.pdfbox.pdmodel.common.PDRectangle;
//import org.apache.pdfbox.pdmodel.font.PDFont;
//import org.apache.pdfbox.pdmodel.font.PDType0Font;
//import org.apache.pdfbox.pdmodel.font.PDType1Font;
//import org.apache.fontbox.ttf.TrueTypeCollection;
//import org.springframework.stereotype.Component;
//
//import java.io.File;
//import java.io.IOException;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.nio.file.Paths;
//import java.time.LocalDateTime;
//import java.time.format.DateTimeFormatter;
//import java.util.ArrayList;
//import java.util.List;
//
//@Slf4j
//@Component("chatSessionTool")
//public class ChatSessionTool {
//
//    @Tool("测试工具，只是看看能不能调用")
//    public String queryChat(){
//        return "我就写一个Tool工具，查询聊天记录的功能还没实现";
//    }
//
//    @Tool("可以询问用户是否需要pdf，根据回答内容生成PDF文件。必须保留原文，不要翻译；入参为标题和正文文本")
//    public String generateAnswerPdf(String title, String content) {
//        String safeTitle = sanitizeFileName(title == null || title.isBlank() ? "ai_answer" : title);
//        String text = content == null ? "" : content;
//
//        // 注意：当用户上下文为中文时，避免生成仅翻译的英文PDF
//        if (!containsCjk(title) && !containsCjk(text)) {
//            return "PDF生成失败: 内容缺少中文。请使用中文原文重新调用该工具（不要翻译）。";
//        }
//
//        Path outputDir = Paths.get(System.getProperty("user.dir"), "generated-pdf");
//        String fileName = safeTitle + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".pdf";
//        Path outputFile = outputDir.resolve(fileName);
//
//        try {
//            Files.createDirectories(outputDir);
//            createPdf(outputFile.toFile(), safeTitle, text);
//            return "PDF生成成功: " + outputFile.toAbsolutePath();
//        } catch (Exception e) {
//            log.error("生成PDF失败", e);
//            return "PDF生成失败: " + e.getMessage();
//        }
//    }
//
//    private void createPdf(File file, String title, String content) throws IOException {
//        try (PDDocument document = new PDDocument()) {
//            boolean hasNonAscii = containsNonAscii(title + content);
//            PDFont font = tryLoadUnicodeFont(document, hasNonAscii);
//            if (font instanceof PDType1Font && hasNonAscii) {
//                throw new IllegalStateException("未找到可用中文字体，请安装微软雅黑或宋体后重试");
//            }
//            float margin = 50;
//            float width = PDRectangle.A4.getWidth() - (2 * margin);
//            float leading = 18;
//
//            // 过滤字体不支持的字符（如 emoji），避免 showText 抛出 glyph 异常
//            String safeTitle = sanitizeTextForFont(title, font);
//            String safeContent = sanitizeTextForFont(content, font);
//
//            List<String> lines = new ArrayList<>();
//            lines.add("标题: " + safeTitle);
//            lines.add(" ");
//            lines.addAll(wrapText(safeContent, font, 12, width));
//
//            int index = 0;
//            while (index < lines.size()) {
//                PDPage page = new PDPage(PDRectangle.A4);
//                document.addPage(page);
//
//                float y = page.getMediaBox().getHeight() - margin;
//                int maxLinesPerPage = Math.max(1, (int) ((y - margin) / leading));
//
//                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
//                    stream.beginText();
//                    stream.setFont(font, 12);
//                    stream.newLineAtOffset(margin, y);
//
//                    int end = Math.min(lines.size(), index + maxLinesPerPage);
//                    for (int i = index; i < end; i++) {
//                        stream.showText(lines.get(i));
//                        if (i < end - 1) {
//                            stream.newLineAtOffset(0, -leading);
//                        }
//                    }
//
//                    stream.endText();
//                }
//
//                index += maxLinesPerPage;
//            }
//
//            document.save(file);
//        }
//    }
//
//    private List<String> wrapText(String text, PDFont font, float fontSize, float maxWidth) throws IOException {
//        List<String> lines = new ArrayList<>();
//        if (text == null || text.isBlank()) {
//            lines.add("(空内容)");
//            return lines;
//        }
//
//        String[] rawLines = text.replace("\r", "").split("\n");
//        for (String rawLine : rawLines) {
//            StringBuilder current = new StringBuilder();
//            for (int i = 0; i < rawLine.length(); i++) {
//                current.append(rawLine.charAt(i));
//                float textWidth = font.getStringWidth(current.toString()) / 1000 * fontSize;
//                if (textWidth > maxWidth && current.length() > 1) {
//                    char last = current.charAt(current.length() - 1);
//                    current.deleteCharAt(current.length() - 1);
//                    lines.add(current.toString());
//                    current = new StringBuilder().append(last);
//                }
//            }
//            lines.add(current.toString());
//        }
//
//        return lines;
//    }
//
//    private PDFont tryLoadUnicodeFont(PDDocument document, boolean requireCjk) {
//        String[] candidates = {
//                "C:/Windows/Fonts/simhei.ttf",
//                "C:/Windows/Fonts/simsun.ttc",
//                "C:/Windows/Fonts/msyh.ttc",
//                "C:/Windows/Fonts/simsun.ttf",
//                "C:/Windows/Fonts/arialuni.ttf",
//                "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
//        };
//
//        for (String path : candidates) {
//            File fontFile = new File(path);
//            if (fontFile.exists()) {
//                try {
//                    if (path.toLowerCase().endsWith(".ttc")) {
//                        PDFont ttcFont = loadFromTtc(document, fontFile, requireCjk);
//                        if (ttcFont != null && (!requireCjk || supportsCjk(ttcFont))) {
//                            return ttcFont;
//                        }
//                        continue;
//                    }
//                    PDFont loaded = PDType0Font.load(document, fontFile);
//                    if (!requireCjk || supportsCjk(loaded)) {
//                        return loaded;
//                    }
//                } catch (Exception ignored) {
//                    // try next font
//                }
//            }
//        }
//
//        return PDType1Font.HELVETICA;
//    }
//
//    private PDFont loadFromTtc(PDDocument document, File file, boolean requireCjk) throws IOException {
//        String[] preferredNames = {"Microsoft YaHei", "SimSun", "SimHei", "NSimSun"};
//
//        try (TrueTypeCollection collection = new TrueTypeCollection(file)) {
//            for (String name : preferredNames) {
//                try {
//                    var ttf = collection.getFontByName(name);
//                    if (ttf != null) {
//                        PDFont namedFont = PDType0Font.load(document, ttf, true);
//                        if (!requireCjk || supportsCjk(namedFont)) {
//                            return namedFont;
//                        }
//                    }
//                } catch (Exception ignored) {
//                    // try next preferred name
//                }
//            }
//
//            final PDFont[] holder = new PDFont[1];
//            collection.processAllFonts(ttf -> {
//                if (holder[0] == null) {
//                    PDFont font = PDType0Font.load(document, ttf, true);
//                    if (!requireCjk || supportsCjk(font)) {
//                        holder[0] = font;
//                    }
//                }
//            });
//            return holder[0];
//        }
//    }
//
//    private boolean supportsCjk(PDFont font) {
//        try {
//            font.encode("你");
//            font.encode("老");
//            return true;
//        } catch (Exception e) {
//            return false;
//        }
//    }
//
//    private boolean containsNonAscii(String text) {
//        if (text == null) {
//            return false;
//        }
//        for (int i = 0; i < text.length(); i++) {
//            if (text.charAt(i) > 127) {
//                return true;
//            }
//        }
//        return false;
//    }
//
//    private boolean containsCjk(String text) {
//        if (text == null || text.isBlank()) {
//            return false;
//        }
//        for (int i = 0; i < text.length(); i++) {
//            Character.UnicodeScript script = Character.UnicodeScript.of(text.charAt(i));
//            if (script == Character.UnicodeScript.HAN) {
//                return true;
//            }
//        }
//        return false;
//    }
//
//    private String sanitizeFileName(String value) {
//        return value.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
//    }
//
//    private String sanitizeTextForFont(String text, PDFont font) {
//        if (text == null || text.isEmpty()) {
//            return "";
//        }
//
//        StringBuilder builder = new StringBuilder(text.length());
//        int i = 0;
//        while (i < text.length()) {
//            int cp = text.codePointAt(i);
//            i += Character.charCount(cp);
//
//            if (cp == '\n' || cp == '\r' || cp == '\t') {
//                builder.appendCodePoint(cp);
//                continue;
//            }
//
//            String ch = new String(Character.toChars(cp));
//            try {
//                font.encode(ch);
//                builder.append(ch);
//            } catch (Exception e) {
//                builder.append('□');
//            }
//        }
//        return builder.toString();
//    }
//}