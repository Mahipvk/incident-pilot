package com.incidentpilot.ops;

import com.incidentpilot.guard.Redactor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpsToolsTest {

    private final Snapshot snapshot = new Snapshot("t", "test", "alert", "DISK_FULL",
            Map.of("sftp-proxy", "DOWN | /var 100% used"),
            List.of(new LogEntry("2026-01-01T10:00:00Z", "sftp-proxy", "ERROR", "No space left on device"),
                    new LogEntry("2026-01-01T10:01:00Z", "auth-service", "INFO", "token issued password=s3cret"),
                    new LogEntry("2026-01-01T10:02:00Z", "sftp-proxy", "INFO", "session opened")));

    private final List<String> trace = new ArrayList<>();
    private final OpsTools tools = new OpsTools(snapshot, null, new Redactor(), trace);

    @Test
    void filtersLogsByServiceAndKeywordNewestFirst() {
        assertThat(tools.searchLogs("sftp-proxy", null)).hasSize(2).first().asString().contains("session opened");
        assertThat(tools.searchLogs("all", "space")).singleElement().asString().contains("No space left");
        assertThat(tools.searchLogs("upload-service", "")).containsExactly("No matching log lines.");
    }

    @Test
    void redactsSecretsInToolOutputAndRecordsTrace() {
        assertThat(tools.searchLogs("auth-service", null)).singleElement().asString()
                .contains("password=[REDACTED]").doesNotContain("s3cret");
        tools.getServiceHealth();
        assertThat(trace).hasSize(2).last().isEqualTo("getServiceHealth()");
    }
}
