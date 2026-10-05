package com.incidentpilot.ops;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class SnapshotStore {

    private final Map<String, Snapshot> snapshots = new LinkedHashMap<>();

    public SnapshotStore(ObjectMapper mapper) throws IOException {
        Resource[] files = new PathMatchingResourcePatternResolver().getResources("classpath:snapshots/*.json");
        List<Snapshot> loaded = new ArrayList<>();
        for (Resource file : files) {
            try (InputStream in = file.getInputStream()) {
                loaded.add(mapper.readValue(in, Snapshot.class));
            }
        }
        loaded.sort(Comparator.comparing(Snapshot::id));
        loaded.forEach(s -> snapshots.put(s.id(), s));
    }

    public Snapshot get(String id) {
        Snapshot s = snapshots.get(id);
        if (s == null) {
            throw new IllegalArgumentException("Unknown snapshot: " + id + ". Known: " + snapshots.keySet());
        }
        return s;
    }

    public List<Snapshot> all() {
        return List.copyOf(snapshots.values());
    }
}
