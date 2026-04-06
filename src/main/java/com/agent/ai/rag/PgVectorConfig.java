package com.agent.ai.rag;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

@Configuration
public class PgVectorConfig {

    @Value("${rag.pgvector.table:rag_embedding}")
    private String table;

    @Value("${rag.pgvector.dimension:1024}")
    private Integer dimension;

    @Value("${rag.pgvector.use-index:true}")
    private Boolean useIndex;

    @Value("${rag.pgvector.index-list-size:100}")
    private Integer indexListSize;

    @Value("${rag.pgvector.create-table:true}")
    private Boolean createTable;

    @Value("${rag.pgvector.drop-table-first:false}")
    private Boolean dropTableFirst;

    @Value("${rag.pgvector.init-extension:true}")
    private Boolean initExtension;

    @Bean("pgVectorEmbeddingStore")
    public EmbeddingStore<TextSegment> pgVectorEmbeddingStore(DataSource dataSource) {
        initializeVectorExtensionIfEnabled(dataSource);
        return PgVectorEmbeddingStore.datasourceBuilder()
                .datasource(dataSource)
                .table(table)
                .dimension(dimension)
                .useIndex(useIndex)
                .indexListSize(indexListSize)
                .createTable(createTable)
                .dropTableFirst(dropTableFirst)
                .build();
    }

    private void initializeVectorExtensionIfEnabled(DataSource dataSource) {
        if (!Boolean.TRUE.equals(initExtension)) {
            return;
        }
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE EXTENSION IF NOT EXISTS vector");
        } catch (SQLException exception) {
            throw new IllegalStateException("初始化 pgvector 扩展失败，请确认数据库已安装 vector 扩展并具备权限。", exception);
        }
    }
}
