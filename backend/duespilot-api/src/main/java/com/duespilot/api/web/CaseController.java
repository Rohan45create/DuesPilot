package com.duespilot.api.web;

import com.duespilot.api.service.CaseService;
import com.duespilot.api.web.dto.CaseDetailResponse;
import com.duespilot.api.web.dto.CaseResponse;
import com.duespilot.api.web.dto.CreateCaseRequest;
import com.duespilot.api.web.dto.DocumentResponse;
import com.duespilot.api.web.error.ValidationException;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cases")
public class CaseController {

    private final CaseService caseService;

    public CaseController(CaseService caseService) {
        this.caseService = caseService;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<CaseResponse>> listCases() {
        return ResponseEntity.ok(caseService.listCases());
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CaseResponse> createCase(
            @Valid @RequestBody CreateCaseRequest request,
            UriComponentsBuilder uriBuilder) {
        CaseResponse response = caseService.createCase(request);
        URI location = uriBuilder.path("/api/v1/cases/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CaseDetailResponse> getCase(@PathVariable UUID id) {
        CaseDetailResponse response = caseService.getCase(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{caseId}/documents/{docId}/download")
    public ResponseEntity<byte[]> downloadDocument(
            @PathVariable UUID caseId,
            @PathVariable UUID docId) {
        var downloaded = caseService.downloadDocument(caseId, docId);
        org.springframework.http.ContentDisposition cd = org.springframework.http.ContentDisposition.inline()
            .filename(downloaded.fileName(), java.nio.charset.StandardCharsets.UTF_8)
            .build();
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(downloaded.mimeType()))
            .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, cd.toString())
            .header("X-Content-Type-Options", "nosniff")
            .body(downloaded.data());
    }

    @PostMapping(value = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<DocumentResponse>> uploadDocuments(
            @PathVariable UUID id,
            @RequestParam(value = "files", required = false) List<MultipartFile> filesList,
            @RequestParam(value = "file", required = false) MultipartFile singleFile,
            @RequestParam(value = "doc_type", required = false) String docType,
            @RequestParam(value = "doc_date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate docDate) {

        List<MultipartFile> files = new ArrayList<>();
        if (filesList != null && !filesList.isEmpty()) {
            files.addAll(filesList);
        }
        if (singleFile != null && !singleFile.isEmpty()) {
            files.add(singleFile);
        }

        if (files.isEmpty()) {
            throw new ValidationException("At least one non-empty file must be provided.");
        }

        List<DocumentResponse> responses = caseService.uploadDocuments(id, files, docType, docDate);
        return ResponseEntity.status(HttpStatus.CREATED).body(responses);
    }
}
