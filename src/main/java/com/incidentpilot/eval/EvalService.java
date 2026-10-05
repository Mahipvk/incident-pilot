package com.incidentpilot.eval;

import com.incidentpilot.ops.Snapshot;
import com.incidentpilot.ops.SnapshotStore;
import com.incidentpilot.triage.TriageResult;
import com.incidentpilot.triage.TriageService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Evaluation harness: replays every incident snapshot through the agent and scores
 * whether it found the right root-cause category, whether it cited a source, and how long it took.
 * Use it to compare models and prompt changes with numbers instead of gut feel.
 */
@Service
public class EvalService {

    public record EvalRow(String snapshotId, String expected, String actual, boolean correct,
                          boolean citedSource, boolean citedRightRunbook, int toolCalls, long millis) {
    }

    public record EvalSummary(String model, int total, int correct, double accuracy,
                              double citationRate, double rightRunbookRate, long avgMillis, List<EvalRow> rows) {
    }

    /** The runbook a correct answer should cite for each category. Citing *a* source is easy; citing the right one is the real test. */
    private static final Map<String, String> EXPECTED_RUNBOOK = Map.of(
            "UPSTREAM_TIMEOUT", "rb-upstream-timeout.md",
            "CREDENTIAL_EXPIRED", "rb-credential-rotation.md",
            "DISK_FULL", "rb-disk-full.md",
            "TLS_HANDSHAKE", "rb-tls-handshake.md",
            "QUEUE_BACKLOG", "rb-queue-backlog.md",
            "MEMORY_EXHAUSTION", "rb-memory-oom.md");

    private final SnapshotStore snapshots;
    private final TriageService triage;

    public EvalService(SnapshotStore snapshots, TriageService triage) {
        this.snapshots = snapshots;
        this.triage = triage;
    }

    public EvalSummary runAll() {
        List<EvalRow> rows = new ArrayList<>();
        String model = "";
        for (Snapshot s : snapshots.all()) {
            TriageResult r = triage.triage(s.id(), null);
            model = r.model();
            String actual = r.report().rootCauseCategory() == null ? "UNKNOWN" : r.report().rootCauseCategory().trim();
            List<String> sources = r.report().sources() == null ? List.of() : r.report().sources();
            boolean cited = !sources.isEmpty();
            String runbook = EXPECTED_RUNBOOK.getOrDefault(s.expectedCategory(), "?");
            boolean rightRunbook = sources.stream().anyMatch(src -> src != null && src.contains(runbook));
            rows.add(new EvalRow(s.id(), s.expectedCategory(), actual,
                    s.expectedCategory().equalsIgnoreCase(actual), cited, rightRunbook, r.toolTrace().size(), r.millis()));
        }
        int correct = (int) rows.stream().filter(EvalRow::correct).count();
        long cited = rows.stream().filter(EvalRow::citedSource).count();
        long right = rows.stream().filter(EvalRow::citedRightRunbook).count();
        long avg = (long) rows.stream().mapToLong(EvalRow::millis).average().orElse(0);
        int n = rows.size();
        return new EvalSummary(model, n, correct, n == 0 ? 0 : (double) correct / n,
                n == 0 ? 0 : (double) cited / n, n == 0 ? 0 : (double) right / n, avg, rows);
    }
}
