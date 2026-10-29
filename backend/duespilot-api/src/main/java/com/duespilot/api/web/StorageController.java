package com.duespilot.api.web;

import com.duespilot.api.repository.FileBlobRepository;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller providing blob storage usage report to stay under the free-tier size.
 * Endpoint: GET /api/v1/storage/report
 */
@RestController
@RequestMapping("/api/v1/storage")
public class StorageController {

    public static final long FREE_TIER_LIMIT_BYTES = 500L * 1024 * 1024; // 500 MB (Supabase free tier DB size)

    private final FileBlobRepository fileBlobRepository;

    public StorageController(FileBlobRepository fileBlobRepository) {
        this.fileBlobRepository = fileBlobRepository;
    }

    public record StorageReportResponse(
        @JsonProperty("total_blobs") long totalBlobs,
        @JsonProperty("total_bytes") long totalBytes,
        @JsonProperty("total_megabytes") double totalMegabytes,
        @JsonProperty("free_tier_limit_bytes") long freeTierLimitBytes,
        @JsonProperty("free_tier_usage_percent") double freeTierUsagePercent
    ) {}

    @GetMapping(value = "/report", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StorageReportResponse> getStorageReport() {
        long totalBlobs = fileBlobRepository.getTotalBlobCount();
        long totalBytes = fileBlobRepository.getTotalStorageBytes();
        double totalMb = Math.round((totalBytes / (1024.0 * 1024.0)) * 1000.0) / 1000.0;
        double usagePct = FREE_TIER_LIMIT_BYTES > 0
            ? Math.round(((double) totalBytes / FREE_TIER_LIMIT_BYTES) * 10000.0) / 100.0
            : 0.0;

        StorageReportResponse response = new StorageReportResponse(
            totalBlobs,
            totalBytes,
            totalMb,
            FREE_TIER_LIMIT_BYTES,
            usagePct
        );
        return ResponseEntity.ok(response);
    }
}
