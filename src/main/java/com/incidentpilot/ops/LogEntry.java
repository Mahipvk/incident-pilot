package com.incidentpilot.ops;

public record LogEntry(String ts, String service, String level, String message) {
    public String line() {
        return ts + " " + level + " [" + service + "] " + message;
    }
}
