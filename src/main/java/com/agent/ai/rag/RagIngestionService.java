package com.agent.ai.rag;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.splitter.DocumentByCharacterSplitter;
import dev.langchain4j.data.document.splitter.DocumentByParagraphSplitter;
import dev.langchain4j.data.document.splitter.DocumentBySentenceSplitter;
import dev.langchain4j.data.document.splitter.DocumentByWordSplitter;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RagIngestionService {

    private static final Logger log = LoggerFactory.getLogger(RagIngestionService.class);

    private final EmbeddingModel qwenEmbeddingModel;
    private final EmbeddingStore<TextSegment> pgVectorEmbeddingStore;

    @Value("${rag.bootstrap.docs-path:src/main/resources/docs}")
    private String docsPath;

    public RagIngestionService(
            EmbeddingModel qwenEmbeddingModel,
            @Qualifier("pgVectorEmbeddingStore") EmbeddingStore<TextSegment> pgVectorEmbeddingStore) {
        this.qwenEmbeddingModel = qwenEmbeddingModel;
        this.pgVectorEmbeddingStore = pgVectorEmbeddingStore;
    }

    public synchronized IngestionResult ingestFromConfiguredPath() {
        List<Document> documents = FileSystemDocumentLoader.loadDocuments(docsPath);
        if (documents.isEmpty()) {
            log.info("未发现可入库文档，docsPath={}", docsPath);
            return new IngestionResult(docsPath, 0, 0);
        }

        DocumentSplitter splitter = buildSafeSplitter();
        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(splitter)
                .textSegmentTransformer(textSegment -> TextSegment.from(
                        textSegment.metadata().getString("file_name") + "\n" + textSegment.text(),
                        textSegment.metadata()
                ))
                .embeddingModel(qwenEmbeddingModel)
                .embeddingStore(pgVectorEmbeddingStore)
                .build();

        ingestor.ingest(documents);
        log.info("文档入库完成，docsPath={}, documents={}", docsPath, documents.size());
        return new IngestionResult(docsPath, documents.size(), -1);
    }

    private DocumentSplitter buildSafeSplitter() {
        // 多级兜底，避免上层 splitter 无法继续拆分时直接抛错。
        DocumentSplitter charSplitter = new DocumentByCharacterSplitter(200, 30);
        DocumentSplitter wordSplitter = new DocumentByWordSplitter(400, 60, charSplitter);
        DocumentSplitter sentenceSplitter = new DocumentBySentenceSplitter(800, 120, wordSplitter);
        return new DocumentByParagraphSplitter(1000, 200, sentenceSplitter);
    }

    public record IngestionResult(String docsPath, int documents, int vectors) {
    }
}

