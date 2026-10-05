package com.incidentpilot.triage;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/** The structured answer the LLM must return (Spring AI turns this record into a JSON schema). */
public record TriageReport(
        @JsonPropertyDescription("One or two sentence plain-English summary of what is happening")
        String summary,
        @JsonPropertyDescription("Exactly one of: UPSTREAM_TIMEOUT, CREDENTIAL_EXPIRED, DISK_FULL, TLS_HANDSHAKE, QUEUE_BACKLOG, MEMORY_EXHAUSTION, UNKNOWN")
        String rootCauseCategory,
        @JsonPropertyDescription("The most likely root cause, specific to this incident")
        String probableRootCause,
        @JsonPropertyDescription("Concrete evidence: quote the log lines / health metrics that support the root cause")
        List<String> evidence,
        @JsonPropertyDescription("Ordered next steps for the on-call engineer")
        List<String> recommendedActions,
        @JsonPropertyDescription("Runbook or past-incident file names that were used, e.g. rb-disk-full.md")
        List<String> sources,
        @JsonPropertyDescription("Confidence from 0.0 to 1.0")
        Double confidence) {
}
