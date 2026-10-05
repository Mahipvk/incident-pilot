package com.incidentpilot.triage;

import java.util.List;

/** What the API returns: the report plus an audit trail of every tool the agent called. */
public record TriageResult(String snapshotId, TriageReport report, List<String> toolTrace, long millis, String model) {
}
