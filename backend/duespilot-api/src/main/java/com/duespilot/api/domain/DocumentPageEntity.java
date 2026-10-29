package com.duespilot.api.domain;

import java.util.UUID;

public record DocumentPageEntity(
    UUID id,
    UUID documentId,
    int pageNo,
    String text
) {}
