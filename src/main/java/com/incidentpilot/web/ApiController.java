package com.incidentpilot.web;

import com.incidentpilot.eval.EvalService;
import com.incidentpilot.ops.SnapshotStore;
import com.incidentpilot.triage.TriageResult;
import com.incidentpilot.triage.TriageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {

    public record TriageRequest(String snapshotId, String alert) {
    }

    public record SnapshotInfo(String id, String title, String alert) {
    }

    private final SnapshotStore snapshots;
    private final TriageService triage;
    private final EvalService evals;

    public ApiController(SnapshotStore snapshots, TriageService triage, EvalService evals) {
        this.snapshots = snapshots;
        this.triage = triage;
        this.evals = evals;
    }

    @GetMapping("/snapshots")
    public List<SnapshotInfo> snapshots() {
        return snapshots.all().stream().map(s -> new SnapshotInfo(s.id(), s.title(), s.alert())).toList();
    }

    @PostMapping("/triage")
    public TriageResult triage(@RequestBody TriageRequest request) {
        return triage.triage(request.snapshotId(), request.alert());
    }

    @PostMapping("/evals")
    public EvalService.EvalSummary runEvals() {
        return evals.runAll();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
