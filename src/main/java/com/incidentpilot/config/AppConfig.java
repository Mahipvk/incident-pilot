package com.incidentpilot.config;

import com.incidentpilot.guard.Redactor;
import com.incidentpilot.knowledge.KnowledgeBase;
import com.incidentpilot.ops.OpsTools;
import com.incidentpilot.ops.SnapshotStore;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;

@Configuration
public class AppConfig {

    @Bean
    public Redactor redactor() {
        return new Redactor();
    }

    /**
     * Publishes the ops tools over MCP (endpoint: /sse) so any MCP client
     * (Claude Desktop, IDE agents, MCP Inspector) can call them directly.
     */
    @Bean
    public ToolCallbackProvider mcpOpsTools(SnapshotStore snapshots, KnowledgeBase knowledge, Redactor redactor,
                                            @Value("${incidentpilot.mcp-default-snapshot}") String snapshotId) {
        OpsTools tools = new OpsTools(snapshots.get(snapshotId), knowledge, redactor, new ArrayList<>());
        return MethodToolCallbackProvider.builder().toolObjects(tools).build();
    }
}
