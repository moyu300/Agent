package com.agent.ai.rag;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.splitter.DocumentByParagraphSplitter;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;

@Configuration
public class RagConfig {

    @Value("${rag.retriever.max-results:5}")
    private Integer maxResults;

    @Value("${rag.retriever.min-score:0.75}")
    private Double minScore;

    @Value("${rag.bootstrap.docs-path:src/main/resources/docs}")
    private String docsPath;

    @Resource
    private EmbeddingModel qwenEmbeddingModel;
    @Resource
    private EmbeddingStore<TextSegment> pgVectorEmbeddingStore;

    /**
     * 内容检索器：仅负责查询，向量写入由独立流程处理。
     */
    @Bean
    public ContentRetriever contentRetriever() {
//        // ------ RAG ------
//        // 1. 加载文档
//        List<Document> documents = FileSystemDocumentLoader.loadDocuments(docsPath);
//        // 2. 文档切割：将每个文档按每段进行分割，最大 1000 字符，每次重叠最多 200 个字符
//        DocumentByParagraphSplitter paragraphSplitter = new DocumentByParagraphSplitter(1000, 200);
//        // 3. 自定义文档加载器
//        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
//                .documentSplitter(paragraphSplitter)
//                // 为了提高搜索质量，为每个 TextSegment 添加文档名称
//                .textSegmentTransformer(textSegment -> TextSegment.from(
//                        textSegment.metadata().getString("file_name") + "\n" + textSegment.text(),
//                        textSegment.metadata()
//                ))
//                // 使用指定的向量模型
//                .embeddingModel(qwenEmbeddingModel)
//                .embeddingStore(pgVectorEmbeddingStore)
//                .build();
//        // 加载文档
//        ingestor.ingest(documents);
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(pgVectorEmbeddingStore)
                .embeddingModel(qwenEmbeddingModel)
                .maxResults(maxResults)
                .minScore(minScore)
                .build();
    }

}
