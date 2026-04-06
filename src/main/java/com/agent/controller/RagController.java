package com.agent.controller;

import com.agent.ai.rag.RagIngestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RagController {

    private final RagIngestionService ragIngestionService;

    @PostMapping("/rag/ingest")
    public IngestResponse ingest() {
        RagIngestionService.IngestionResult result = ragIngestionService.ingestFromConfiguredPath();
        return new IngestResponse(
                "ok",
                result.docsPath(),
                result.documents(),
                result.vectors()
        );
    }

    public record IngestResponse(String message, String docsPath, int documents, int vectors) {
    }
}

