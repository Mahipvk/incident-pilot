package com.incidentpilot.eval;

import com.incidentpilot.ops.Snapshot;
import com.incidentpilot.ops.SnapshotStore;
import com.incidentpilot.triage.TriageResult;
import com.incidentpilot.triage.TriageService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Evaluation harness: replays every incident snapshot through the agent and scores
 * whether it found the right root-cause category, whether it cited a source, and how long it took.
 * Use it to compare models and prompt changes with numbers instead of gut feel.
 */
@Service
public class EvalService {

    public record EvalRow(String snapshotId, String expected, String actual, boolean correct,
                          boolean citedSource, int toolCalls, long millis) {
    }

    public record EvalSummary(String model, int total, int correct, double accuracy,
                              double citationRate, long avgMillis, List<EvalRow> rows) {
    }

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
            boolean cited = r.report().sources() != null && !r.report().sources().isEmpty();
            rows.add(new EvalRow(s.id(), s.expectedCategory(), actual,
                    s.expectedCategory().equalsIgnoreCase(actual), cited, r.toolTrace().size(), r.millis()));
        }
        int correct = (int) rows.stream().filter(EvalRow::correct).count();
        long cited = rows.stream().filter(EvalRow::citedSource).count();
        long avg = (long) rows.stream().mapToLong(EvalRow::millis).average().orElse(0);
        int n = rows.size();
        return new EvalSummary(model, n, correct, n == 0 ? 0 : (double) correct / n,
                n == 0 ? 0 : (double) cited / n, avg, rows);
    }
}
