package com.agent.ai.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

@Component
public class RagBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RagBootstrapRunner.class);

    private final RagIngestionService ragIngestionService;
    private final DataSource dataSource;

    @Value("${rag.bootstrap.enabled:true}")
    private Boolean bootstrapEnabled;

    @Value("${rag.pgvector.table:rag_embedding}")
    private String table;

    @Value("${rag.bootstrap.fail-fast:false}")
    private Boolean failFast;

    public RagBootstrapRunner(RagIngestionService ragIngestionService, DataSource dataSource) {
        this.ragIngestionService = ragIngestionService;
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!Boolean.TRUE.equals(bootstrapEnabled)) {
            log.info("RAG 启动入库已关闭（rag.bootstrap.enabled=false）");
            return;
        }
        if (!isVectorTableEmpty()) {
            log.info("向量表已有数据，跳过启动入库，table={}", table);
            return;
        }
        try {
            ragIngestionService.ingestFromConfiguredPath();
        } catch (RuntimeException exception) {
            if (Boolean.TRUE.equals(failFast)) {
                throw exception;
            }
            log.error("启动入库失败，已跳过以保证服务可用。可稍后通过 /api/rag/ingest 手动重试。", exception);
        }
    }

    private boolean isVectorTableEmpty() {
        String sql = "SELECT COUNT(*) FROM " + table;
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getLong(1) == 0L;
        } catch (SQLException exception) {
            throw new IllegalStateException("检查向量表数据量失败，请确认表存在且具备访问权限: " + table, exception);
        }
    }
}

