package com.duespilot.api.service;

import com.duespilot.api.domain.AuditEventEntity;
import com.duespilot.api.domain.CaseEntity;
import com.duespilot.api.domain.DocType;
import com.duespilot.api.domain.DocumentEntity;
import com.duespilot.api.domain.DocumentPageEntity;
import com.duespilot.api.domain.SupplierCategory;
import com.duespilot.api.repository.AuditEventRepository;
import com.duespilot.api.repository.CaseRepository;
import com.duespilot.api.repository.DocumentPageRepository;
import com.duespilot.api.repository.DocumentRepository;
import com.duespilot.api.web.dto.CaseDetailResponse;
import com.duespilot.api.web.dto.CaseResponse;
import com.duespilot.api.web.dto.CreateCaseRequest;
import com.duespilot.api.web.dto.DocumentResponse;
import com.duespilot.api.web.error.CaseNotFoundException;
import com.duespilot.api.web.error.TooManyDocumentsException;
import com.duespilot.api.web.error.ValidationException;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class CaseService {

    public static final int MAX_DOCUMENTS_PER_CASE = 25; // Rule R-53

    private final CaseRepository caseRepository;
    private final DocumentRepository documentRepository;
    private final DocumentPageRepository documentPageRepository;
    private final AuditEventRepository auditEventRepository;
    private final FileStorageService fileStorageService;
    private final TextExtractionService textExtractionService;
    private final @Nullable CurrentUserService currentUserService;

    @org.springframework.beans.factory.annotation.Autowired
    public CaseService(
            CaseRepository caseRepository,
            DocumentRepository documentRepository,
            DocumentPageRepository documentPageRepository,
            AuditEventRepository auditEventRepository,
            FileStorageService fileStorageService,
            TextExtractionService textExtractionService,
            @Nullable CurrentUserService currentUserService) {
        this.caseRepository = caseRepository;
        this.documentRepository = documentRepository;
        this.documentPageRepository = documentPageRepository;
        this.auditEventRepository = auditEventRepository;
        this.fileStorageService = fileStorageService;
        this.textExtractionService = textExtractionService;
        this.currentUserService = currentUserService;
    }

    public CaseService(
            CaseRepository caseRepository,
            DocumentRepository documentRepository,
            DocumentPageRepository documentPageRepository,
            AuditEventRepository auditEventRepository,
            FileStorageService fileStorageService,
            TextExtractionService textExtractionService) {
        this(caseRepository, documentRepository, documentPageRepository, auditEventRepository, fileStorageService, textExtractionService, null);
    }

    private UUID resolveWorkspaceId() {
        if (currentUserService != null) {
            return currentUserService.getCurrentUser().workspaceId();
        }
        return CaseEntity.DEMO_WORKSPACE_ID;
    }

    @Transactional
    public CaseResponse createCase(CreateCaseRequest request) {
        if (request.title() == null || request.title().isBlank()) {
            throw new ValidationException("Title is required.");
        }

        UUID workspaceId = resolveWorkspaceId();
        String code = request.code();
        if (code == null || code.isBlank()) {
            code = caseRepository.nextCaseCode(workspaceId);
        } else {
            if (caseRepository.findByCodeAndWorkspaceId(code.trim(), workspaceId).isPresent()) {
                throw new ValidationException("Case with code '" + code.trim() + "' already exists.");
            }
            code = code.trim();
        }

        SupplierCategory category = SupplierCategory.fromString(request.supplierCategory());
        CaseEntity entity = new CaseEntity(
            UUID.randomUUID(),
            workspaceId,
            code,
            request.title().trim(),
            request.supplierName() != null ? request.supplierName().trim() : null,
            request.buyerName() != null ? request.buyerName().trim() : null,
            category,
            request.udyamRegisteredOn(),
            false,
            Instant.now(),
            0
        );

        CaseEntity saved = caseRepository.save(entity);

        // Record audit event
        auditEventRepository.save(new AuditEventEntity(
            UUID.randomUUID(),
            saved.id(),
            "SYSTEM",
            "CASE_CREATED",
            "{\"code\":\"" + saved.code() + "\",\"title\":\"" + saved.title() + "\"}",
            Instant.now()
        ));

        return CaseResponse.fromEntity(saved);
    }

    public CaseDetailResponse getCase(UUID id) {
        UUID workspaceId = resolveWorkspaceId();
        CaseEntity caseEntity = caseRepository.findByIdAndWorkspaceId(id, workspaceId)
            .orElseThrow(() -> new CaseNotFoundException(id));

        List<DocumentResponse> documents = documentRepository.findByCaseId(id).stream()
            .map(DocumentResponse::fromEntity)
            .toList();

        return CaseDetailResponse.of(caseEntity, documents, null);
    }

    public List<CaseResponse> listCases() {
        UUID workspaceId = resolveWorkspaceId();
        return caseRepository.findAllByWorkspaceId(workspaceId).stream()
            .map(CaseResponse::fromEntity)
            .toList();
    }

    @Transactional
    public List<DocumentResponse> uploadDocuments(
            UUID caseId,
            List<MultipartFile> files,
            @Nullable String docTypeStr,
            @Nullable LocalDate docDate) {

        UUID workspaceId = resolveWorkspaceId();
        CaseEntity caseEntity = caseRepository.findByIdAndWorkspaceId(caseId, workspaceId)
            .orElseThrow(() -> new CaseNotFoundException(caseId));

        if (files == null || files.isEmpty()) {
            throw new ValidationException("At least one file must be provided.");
        }

        long existingCount = documentRepository.countByCaseId(caseId);
        if (existingCount + files.size() > MAX_DOCUMENTS_PER_CASE) {
            throw new TooManyDocumentsException(
                "Case cannot exceed 25 documents (Rule R-53). Current count: " + existingCount
                + ", attempted to add: " + files.size() + "."
            );
        }

        DocType docType = DocType.fromString(docTypeStr);
        List<DocumentResponse> responses = new ArrayList<>(files.size());

        for (MultipartFile file : files) {
            byte[] bytes;
            try {
                bytes = file.getBytes();
            } catch (IOException e) {
                throw new RuntimeException("Failed to read uploaded file: " + file.getOriginalFilename(), e);
            }

            // Store file with R-53 validations (size, allow-list, sanitisation, sha256)
            FileStorageService.StoredFileInfo stored = fileStorageService.storeFile(
                bytes,
                file.getOriginalFilename(),
                file.getContentType()
            );

            // Extract pages with PDFBox or plaintext passthrough
            List<TextExtractionService.ExtractedPage> extractedPages = textExtractionService.extractPages(
                bytes,
                stored.mimeType(),
                stored.sanitizedFileName()
            );

            int pageCount = Math.max(1, extractedPages.size());

            DocumentEntity documentEntity = new DocumentEntity(
                stored.id(),
                caseEntity.id(),
                workspaceId,
                docType,
                stored.sanitizedFileName(),
                stored.mimeType(),
                stored.sha256(),
                stored.storagePath(),
                pageCount,
                docDate,
                Instant.now(),
                bytes.length,
                bytes
            );

            DocumentEntity savedDoc = documentRepository.save(documentEntity);

            // Save document pages
            List<DocumentPageEntity> pageEntities = new ArrayList<>(extractedPages.size());
            for (TextExtractionService.ExtractedPage ep : extractedPages) {
                pageEntities.add(new DocumentPageEntity(
                    UUID.randomUUID(),
                    savedDoc.id(),
                    ep.pageNo(),
                    ep.text()
                ));
            }
            documentPageRepository.saveAll(pageEntities);

            // Audit event
            auditEventRepository.save(new AuditEventEntity(
                UUID.randomUUID(),
                caseEntity.id(),
                "SYSTEM",
                "DOCUMENT_UPLOADED",
                "{\"document_id\":\"" + savedDoc.id() + "\",\"file_name\":\"" + savedDoc.fileName() + "\"}",
                Instant.now()
            ));

            responses.add(DocumentResponse.fromEntity(savedDoc));
        }

        return responses;
    }

    public record DownloadedFile(String fileName, String mimeType, byte[] data) {}

    public DownloadedFile downloadDocument(UUID caseId, UUID docId) {
        UUID workspaceId = resolveWorkspaceId();

        // Verify case belongs to caller's workspace
        caseRepository.findByIdAndWorkspaceId(caseId, workspaceId)
            .orElseThrow(() -> new CaseNotFoundException(caseId));

        var doc = documentRepository.findById(docId)
            .orElseThrow(() -> new com.duespilot.api.web.error.ApiException(
                org.springframework.http.HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND", "Document not found: " + docId
            ));

        if (!doc.caseId().equals(caseId)) {
            throw new com.duespilot.api.web.error.ApiException(
                org.springframework.http.HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND", "Document does not belong to case: " + caseId
            );
        }

        if (doc.contentBytes() != null && doc.contentBytes().length > 0) {
            return new DownloadedFile(doc.fileName(), doc.mimeType(), doc.contentBytes());
        }

        try {
            java.nio.file.Path path = java.nio.file.Path.of(doc.storagePath());
            if (!java.nio.file.Files.exists(path)) {
                throw new com.duespilot.api.web.error.ApiException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "FILE_NOT_FOUND", "Document file not found on storage"
                );
            }
            byte[] data = java.nio.file.Files.readAllBytes(path);
            return new DownloadedFile(doc.fileName(), doc.mimeType(), data);
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to read document file", e);
        }
    }
}
