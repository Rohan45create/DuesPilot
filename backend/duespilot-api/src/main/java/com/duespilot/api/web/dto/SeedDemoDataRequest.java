package com.duespilot.api.web.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

public record SeedDemoDataRequest(
    @JsonProperty("acknowledged") @JsonAlias("acknowledged") @Nullable Boolean acknowledged
) {}
