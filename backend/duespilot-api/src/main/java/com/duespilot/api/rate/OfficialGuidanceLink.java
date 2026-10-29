package com.duespilot.api.rate;

import org.jspecify.annotations.NonNull;

/**
 * Official guidance link reference for the Evidence panel (architecture §10.7).
 * Shown as references only — never as decisions.
 */
public record OfficialGuidanceLink(
    @NonNull String title,
    @NonNull String url,
    @NonNull String source,
    @NonNull String description
) {}
