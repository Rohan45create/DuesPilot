package com.duespilot.api.llm;

import com.duespilot.core.extract.RawExtraction;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ExtractionResponseDto(
    @JsonProperty("fields") List<FieldItemDto> fields
) {
    public ExtractionResponseDto {
        if (fields == null) {
            fields = List.of();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FieldItemDto(
        @JsonProperty("field_name") @JsonAlias("fieldName") String fieldName,
        @JsonProperty("value") @Nullable String value,
        @JsonProperty("page") @JsonAlias("page_no") int page,
        @JsonProperty("char_start") @JsonAlias("charStart") @Nullable Integer charStart,
        @JsonProperty("char_end") @JsonAlias("charEnd") @Nullable Integer charEnd,
        @JsonProperty("quote") @Nullable String quote,
        @JsonProperty("confidence") @Nullable String confidence
    ) {
        public RawExtraction toRawExtraction() {
            return new RawExtraction(
                fieldName != null ? fieldName : "UNKNOWN_FIELD",
                value,
                page > 0 ? page : 1,
                charStart,
                charEnd,
                quote,
                confidence != null ? confidence : "HIGH"
            );
        }
    }

    public List<RawExtraction> toRawExtractions() {
        List<RawExtraction> rawList = new ArrayList<>(fields.size());
        for (FieldItemDto item : fields) {
            if (item.fieldName() != null && !item.fieldName().isBlank()) {
                rawList.add(item.toRawExtraction());
            }
        }
        return rawList;
    }
}
