package com.duespilot.core.conflict;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.NonNull;

/**
 * A detected commercial or evidentiary conflict.
 *
 * Per architecture.md section 10.4:
 * Rules emit a conflict with evidence IDs; the LLM may add LLM-detected conflicts,
 * but each must pass the evidence check.
 */
public record Conflict(
    @NonNull String id,
    @NonNull ConflictType type,
    @NonNull String name,
    @NonNull String summary,
    @NonNull List<String> evidenceIds,
    @NonNull Map<String, String> details
) {
    public Conflict {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(summary, "summary cannot be null");
        evidenceIds = List.copyOf(evidenceIds != null ? evidenceIds : Collections.emptyList());
        details = Map.copyOf(details != null ? details : Collections.emptyMap());
    }

    public static Conflict of(
        @NonNull String id,
        @NonNull ConflictType type,
        @NonNull String summary,
        @NonNull List<String> evidenceIds
    ) {
        return new Conflict(id, type, type.title(), summary, evidenceIds, Collections.emptyMap());
    }

    public static Conflict of(
        @NonNull String id,
        @NonNull ConflictType type,
        @NonNull String summary,
        @NonNull List<String> evidenceIds,
        @NonNull Map<String, String> details
    ) {
        return new Conflict(id, type, type.title(), summary, evidenceIds, details);
    }
}
