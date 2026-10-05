package com.incidentpilot.ops;

import java.util.List;
import java.util.Map;

/**
 * A frozen picture of the (simulated) platform during an incident:
 * the alert that fired, service health, and the logs around that time.
 * Replaying snapshots makes the agent testable and the evals repeatable.
 */
public record Snapshot(String id,
                       String title,
                       String alert,
                       String expectedCategory,
                       Map<String, String> health,
                       List<LogEntry> logs) {
}
