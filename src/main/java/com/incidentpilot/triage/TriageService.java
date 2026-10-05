package com.incidentpilot.triage;

import com.incidentpilot.guard.Redactor;
import com.incidentpilot.knowledge.KnowledgeBase;
import com.incidentpilot.ops.OpsTools;
import com.incidentpilot.ops.Snapshot;
import com.incidentpilot.ops.SnapshotStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The agent, in two steps:
 *  1. INVESTIGATE - the LLM calls tools (health, logs, runbooks, past incidents) and writes free-text findings.
 *  2. REPORT      - a second LLM call (no tools, JSON mode) turns the findings + raw tool output into a typed TriageReport.
 * Splitting the steps makes small local models far more reliable than asking for tools and strict JSON in one call.
 */
@Service
public class TriageService {

    private static final Logger log = LoggerFactory.getLogger(TriageService.class);

    static final String INVESTIGATOR_PROMPT = """
            You are IncidentPilot, a senior site-reliability engineer for a secure file-transfer platform.
            Services: edge-gateway, upload-service, transfer-worker, notification-service, auth-service, sftp-proxy.
            Investigate the alert like an expert on-call engineer:
            1. Call getServiceHealth to see what is degraded or down.
            2. Call searchLogs for EVERY service that is DEGRADED or DOWN, with keyword ERROR, then WARN.
               Also search the logs of every service named in the alert, and of services that call it or that it depends on.
            3. Call searchRunbooks and findSimilarIncidents using the specific error messages you found.
            Then write your findings in plain English: what is broken, the exact log lines that prove it,
            the most likely root cause, and which runbook or past incident matches.
            Past incidents are hints, not answers: the current health and logs decide the root cause.
            Only state facts supported by tool output. Never invent log lines or file names.
            """;

    static final String REPORTER_PROMPT = """
            You write incident triage reports as JSON. Use ONLY the alert, the investigation notes and the tool output given.
            rootCauseCategory must be exactly one of these (pick the one the CURRENT logs and health prove):
            - UPSTREAM_TIMEOUT: gateway 504s / requests exceeding a timeout because a backend is slow
            - CREDENTIAL_EXPIRED: auth failures (401, Auth fail) caused by rotated, expired or stale credentials
            - DISK_FULL: "No space left on device", ENOSPC, a filesystem at or near 100%
            - TLS_HANDSHAKE: TLS handshake failures, no shared cipher, protocol version mismatch
            - QUEUE_BACKLOG: messages piling up, delayed or expiring because a consumer is down, crashing or too slow,
              including when the consumer crashes because one of ITS dependencies (SMTP relay, database) is unreachable
            - MEMORY_EXHAUSTION: OutOfMemoryError, OOMKilled, heap exhaustion
            - UNKNOWN: the evidence does not support any of the above
            Pick the category of the main failure mode the alert describes; explain the deeper cause in probableRootCause.
            In sources, list only the runbooks and incidents that match the chosen category and the evidence.
            Past incidents are only hints. If a past incident's symptoms do not appear in the current logs, ignore it
            and do not cite it. Low similarity scores (below about 0.6) usually mean a weak match.
            Quote real log lines as evidence. List the runbook / incident file names you relied on as sources
            (they appear in the tool output as [source: file-name.md]).
            """;

    private static final String INVESTIGATE_TEMPLATE = """
            ALERT: {alert}

            Investigate with your tools, then write your findings.
            """;

    private static final String REPORT_TEMPLATE = """
            ALERT:
            {alert}

            INVESTIGATION NOTES:
            {notes}

            TOOL OUTPUT:
            {evidence}

            Write the triage report.
            {format}
            """;

    private final ChatClient investigator;
    private final ChatClient reporter;
    private final SnapshotStore snapshots;
    private final KnowledgeBase knowledge;
    private final Redactor redactor;
    private final String model;

    public TriageService(ChatModel chatModel, SnapshotStore snapshots, KnowledgeBase knowledge,
                         Redactor redactor, @Value("${spring.ai.ollama.chat.options.model}") String model) {
        this.investigator = ChatClient.builder(chatModel).defaultSystem(INVESTIGATOR_PROMPT).build();
        this.reporter = ChatClient.builder(chatModel).defaultSystem(REPORTER_PROMPT).build();
        this.snapshots = snapshots;
        this.knowledge = knowledge;
        this.redactor = redactor;
        this.model = model;
    }

    public TriageResult triage(String snapshotId, String alertOverride) {
        Snapshot snapshot = snapshots.get(snapshotId);
        String alert = redactor.redact(alertOverride == null || alertOverride.isBlank() ? snapshot.alert() : alertOverride);

        List<String> trace = Collections.synchronizedList(new ArrayList<>());
        OpsTools tools = new OpsTools(snapshot, knowledge, redactor, trace);
        long start = System.currentTimeMillis();

        // Step 1: investigate with tools
        String notes = investigator.prompt()
                .user(u -> u.text(INVESTIGATE_TEMPLATE).param("alert", alert))
                .tools(tools)
                .call()
                .content();
        if (notes == null || notes.isBlank()) {
            notes = "(the investigator returned no notes - rely on the tool output)";
        }

        // Step 2: write the structured report from the evidence (no tools, JSON mode)
        BeanOutputConverter<TriageReport> converter = new BeanOutputConverter<>(TriageReport.class);
        String evidence = String.join("\n\n", tools.outputs());
        String finalNotes = notes;
        String raw = reporter.prompt()
                .options(OllamaOptions.builder().format("json").build())
                .user(u -> u.text(REPORT_TEMPLATE)
                        .param("alert", alert)
                        .param("notes", finalNotes)
                        .param("evidence", evidence.isBlank() ? "(no tools were called)" : evidence)
                        .param("format", converter.getFormat()))
                .call()
                .content();
        long millis = System.currentTimeMillis() - start;

        TriageReport report;
        try {
            report = converter.convert(raw);
        } catch (RuntimeException e) {
            log.warn("Report step did not return valid JSON: {}", raw);
            report = new TriageReport(finalNotes, "UNKNOWN", "Model output was not valid JSON",
                    List.of(), List.of(), List.of(), 0.0);
        }
        log.info("Triage {} -> {} in {} ms, tools: {}", snapshotId, report.rootCauseCategory(), millis, trace);
        return new TriageResult(snapshotId, report, List.copyOf(trace), millis, model);
    }
}
