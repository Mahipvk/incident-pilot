package com.incidentpilot.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Ingestion half of RAG: on startup, loads runbooks and past-incident postmortems,
 * splits them into chunks, embeds them and stores the vectors in pgvector (only once).
 */
@Component
public class KnowledgeIngestor implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIngestor.class);

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbc;

    public KnowledgeIngestor(VectorStore vectorStore, JdbcTemplate jdbc) {
        this.vectorStore = vectorStore;
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        Integer existing = jdbc.queryForObject("SELECT COUNT(*) FROM vector_store", Integer.class);
        if (existing != null && existing > 0) {
            log.info("Knowledge base already loaded ({} chunks) - skipping ingestion", existing);
            return;
        }
        List<Document> docs = new ArrayList<>();
        docs.addAll(load("classpath:knowledge/runbooks/*.md", KnowledgeBase.RUNBOOK));
        docs.addAll(load("classpath:knowledge/incidents/*.md", KnowledgeBase.INCIDENT));

        List<Document> chunks = new TokenTextSplitter().apply(docs);
        vectorStore.add(chunks);
        log.info("Ingested {} documents as {} chunks into pgvector", docs.size(), chunks.size());
    }

    private List<Document> load(String pattern, String type) throws IOException {
        List<Document> docs = new ArrayList<>();
        for (Resource r : new PathMatchingResourcePatternResolver().getResources(pattern)) {
            String text = r.getContentAsString(StandardCharsets.UTF_8);
            docs.add(new Document(text, Map.of("source", r.getFilename(), "type", type)));
        }
        return docs;
    }
}
