package com.incidentpilot.ops;

import com.incidentpilot.guard.Redactor;
import com.incidentpilot.knowledge.KnowledgeBase;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * The tools the AI agent can call. The LLM decides WHICH tool to call and with WHAT arguments;
 * this Java code does the actual work. Every call is recorded in the trace so a human can audit
 * exactly what evidence the agent looked at.
 *
 * One instance is created per triage request, bound to one incident snapshot (thread-safe by design).
 */
public class OpsTools {

    private static final int MAX_LOG_LINES = 25;

    private final Snapshot snapshot;
    private final KnowledgeBase knowledge;
    private final Redactor redactor;
    private final List<String> trace;
    private final List<String> outputs = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

    public OpsTools(Snapshot snapshot, KnowledgeBase knowledge, Redactor redactor, List<String> trace) {
        this.snapshot = snapshot;
        this.knowledge = knowledge;
        this.redactor = redactor;
        this.trace = trace;
    }

    @Tool(description = "Get the current health status of every service on the file-transfer platform "
            + "(UP, DEGRADED or DOWN, plus key metrics). Call this first to see what is broken.")
    public Map<String, String> getServiceHealth() {
        trace.add("getServiceHealth()");
        Map<String, String> health = new TreeMap<>(snapshot.health());
        record("getServiceHealth()", health.toString());
        return health;
    }

    /** Everything the tools returned during this investigation (fed to the report-writing step). */
    public List<String> outputs() {
        return List.copyOf(outputs);
    }

    private void record(String call, String result) {
        String r = result.length() > 3000 ? result.substring(0, 3000) + "..." : result;
        outputs.add(call + " returned:\n" + r);
    }

    @Tool(description = "Search recent application logs, newest first, max 25 lines. "
            + "Filter by service name (or 'all') and an optional keyword such as ERROR, timeout, disk, auth, TLS, heap.")
    public List<String> searchLogs(
            @ToolParam(description = "Service name, e.g. upload-service, or 'all' for every service") String service,
            @ToolParam(description = "Case-insensitive keyword to match; leave empty for no keyword filter", required = false) String keyword) {

        String svc = service == null ? "all" : service.trim().toLowerCase(Locale.ROOT);
        String kw = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);

        List<String> lines = find(svc, kw);
        String note = "";
        if (lines.isEmpty() && !kw.isEmpty()) {
            // Small models often guess the wrong keyword; fall back to the service's warnings and errors.
            lines = find(svc, "").stream().filter(l -> l.contains(" ERROR ") || l.contains(" WARN ")).toList();
            note = " (no match for keyword, returned ERROR/WARN lines instead)";
        }

        trace.add("searchLogs(service=" + svc + ", keyword=" + (kw.isEmpty() ? "-" : kw) + ") -> " + lines.size() + " lines" + note);
        List<String> result = lines.isEmpty() ? List.of("No matching log lines.") : lines;
        record("searchLogs(" + svc + ", " + kw + ")" + note, String.join("\n", result));
        return result;
    }

    private List<String> find(String svc, String kw) {
        return snapshot.logs().stream()
                .filter(e -> svc.isEmpty() || svc.equals("all") || e.service().equalsIgnoreCase(svc))
                .filter(e -> kw.isEmpty() || e.line().toLowerCase(Locale.ROOT).contains(kw))
                .sorted(Comparator.comparing(LogEntry::ts).reversed())
                .limit(MAX_LOG_LINES)
                .map(e -> redactor.redact(e.line()))
                .toList();
    }

    @Tool(description = "Semantic search over the team's operational runbooks. "
            + "Use it to find the documented diagnosis steps and fix for a symptom.")
    public List<String> searchRunbooks(@ToolParam(description = "Symptom or question in plain English") String query) {
        List<String> hits = knowledge.search(query, KnowledgeBase.RUNBOOK, 3);
        trace.add("searchRunbooks(\"" + query + "\") -> " + hits.size() + " passages");
        record("searchRunbooks(" + query + ")", String.join("\n---\n", hits));
        return hits;
    }

    @Tool(description = "Find past incidents (postmortems) similar to the current symptoms, including their root cause and fix.")
    public List<String> findSimilarIncidents(@ToolParam(description = "Description of the current symptoms") String description) {
        List<String> hits = knowledge.search(description, KnowledgeBase.INCIDENT, 3);
        trace.add("findSimilarIncidents(\"" + description + "\") -> " + hits.size() + " incidents");
        record("findSimilarIncidents(" + description + ")", String.join("\n---\n", hits));
        return hits;
    }
}
