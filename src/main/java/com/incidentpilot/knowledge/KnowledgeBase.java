package com.incidentpilot.knowledge;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.List;

/** Retrieval half of RAG: embeds the query and returns the closest chunks from pgvector. */
@Component
public class KnowledgeBase {

    public static final String RUNBOOK = "runbook";
    public static final String INCIDENT = "incident";

    private final VectorStore vectorStore;

    public KnowledgeBase(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public List<String> search(String query, String type, int topK) {
        List<Document> docs = vectorStore.similaritySearch(SearchRequest.builder()
                .query(query)
                .topK(topK)
                .filterExpression("type == '" + type + "'")
                .build());
        if (docs == null || docs.isEmpty()) {
            return List.of("No relevant " + type + " found.");
        }
        return docs.stream()
                .map(d -> "[source: " + d.getMetadata().get("source") + "]\n" + d.getText())
                .toList();
    }
}
