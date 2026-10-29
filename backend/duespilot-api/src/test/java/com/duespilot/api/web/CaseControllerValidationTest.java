package com.duespilot.api.web;

import com.duespilot.api.config.SecurityHeadersFilter;
import com.duespilot.api.service.CaseService;
import com.duespilot.api.web.dto.CaseDetailResponse;
import com.duespilot.api.web.dto.CaseResponse;
import com.duespilot.api.web.dto.DocumentResponse;
import com.duespilot.api.web.error.ApiExceptionHandler;
import com.duespilot.api.web.error.CaseNotFoundException;
import com.duespilot.api.web.error.DocumentParseException;
import com.duespilot.api.web.error.FileTooLargeException;
import com.duespilot.api.web.error.TooManyDocumentsException;
import com.duespilot.api.web.error.UnsupportedDocumentTypeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CaseController.class)
@Import({ApiExceptionHandler.class, SecurityHeadersFilter.class})
@DisplayName("CaseController MockMvc Validation & Error Contract Tests (Section 12 & Rule R-53)")
class CaseControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CaseService caseService;

    @Test
    @DisplayName("POST /api/v1/cases: blank title returns 400 VALIDATION_FAILED ProblemDetail")
    void createCaseWithBlankTitleFails() throws Exception {
        String invalidJson = """
            {
              "title": "",
              "supplier_name": "Acme Tools"
            }
            """;

        mockMvc.perform(post("/api/v1/cases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
            .andExpect(status().isBadRequest())
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.title").value("Validation Failed"));
    }

    @Test
    @DisplayName("POST /api/v1/cases: valid request returns 201 Created with Location and nosniff")
    void createCaseSuccess() throws Exception {
        UUID caseId = UUID.randomUUID();
        CaseResponse mockResponse = new CaseResponse(
            caseId,
            "DP-001",
            "Outstanding Dues Q2",
            "Acme Tools",
            "Apex Auto",
            "SMALL",
            LocalDate.of(2022, 1, 15),
            false,
            Instant.now(),
            0
        );

        when(caseService.createCase(any())).thenReturn(mockResponse);

        String validJson = """
            {
              "title": "Outstanding Dues Q2",
              "supplier_name": "Acme Tools",
              "buyer_name": "Apex Auto",
              "supplier_category": "SMALL",
              "udyam_registered_on": "2022-01-15"
            }
            """;

        mockMvc.perform(post("/api/v1/cases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validJson))
            .andExpect(status().isCreated())
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("Location", "http://localhost/api/v1/cases/" + caseId))
            .andExpect(jsonPath("$.id").value(caseId.toString()))
            .andExpect(jsonPath("$.code").value("DP-001"))
            .andExpect(jsonPath("$.supplier_category").value("SMALL"));
    }

    @Test
    @DisplayName("POST /api/v1/cases/{id}/documents: unsupported extension returns 400 UNSUPPORTED_TYPE")
    void uploadUnsupportedTypeFails() throws Exception {
        UUID caseId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
            "file", "script.exe", "application/x-msdownload", "bad content".getBytes()
        );

        when(caseService.uploadDocuments(eq(caseId), any(), any(), any()))
            .thenThrow(new UnsupportedDocumentTypeException("File extension 'exe' is not supported (Rule R-53)."));

        mockMvc.perform(multipart("/api/v1/cases/{id}/documents", caseId).file(file))
            .andExpect(status().isBadRequest())
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(jsonPath("$.code").value("UNSUPPORTED_TYPE"))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.title").value("Unsupported Document Type"));
    }

    @Test
    @DisplayName("POST /api/v1/cases/{id}/documents: file > 10 MB returns 413 FILE_TOO_LARGE")
    void uploadFileTooLargeFails() throws Exception {
        UUID caseId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
            "file", "huge.pdf", "application/pdf", new byte[100]
        );

        when(caseService.uploadDocuments(eq(caseId), any(), any(), any()))
            .thenThrow(new FileTooLargeException("Uploaded file exceeds maximum allowed size of 10 MB (Rule R-53)."));

        mockMvc.perform(multipart("/api/v1/cases/{id}/documents", caseId).file(file))
            .andExpect(status().isPayloadTooLarge())
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"))
            .andExpect(jsonPath("$.status").value(413))
            .andExpect(jsonPath("$.title").value("File Too Large"));
    }

    @Test
    @DisplayName("POST /api/v1/cases/{id}/documents: corrupt PDF returns 422 PARSE_FAILED")
    void uploadCorruptPdfFails() throws Exception {
        UUID caseId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
            "file", "corrupt.pdf", "application/pdf", "not a pdf".getBytes()
        );

        when(caseService.uploadDocuments(eq(caseId), any(), any(), any()))
            .thenThrow(new DocumentParseException("PDF could not be parsed: corrupt header."));

        mockMvc.perform(multipart("/api/v1/cases/{id}/documents", caseId).file(file))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(jsonPath("$.code").value("PARSE_FAILED"))
            .andExpect(jsonPath("$.status").value(422))
            .andExpect(jsonPath("$.title").value("Parse Failed"));
    }

    @Test
    @DisplayName("POST /api/v1/cases/{id}/documents: exceeding 25 files returns 400 TOO_MANY_DOCUMENTS")
    void uploadExceeding25FilesFails() throws Exception {
        UUID caseId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
            "file", "inv.pdf", "application/pdf", "dummy".getBytes()
        );

        when(caseService.uploadDocuments(eq(caseId), any(), any(), any()))
            .thenThrow(new TooManyDocumentsException("Case cannot exceed 25 documents (Rule R-53)."));

        mockMvc.perform(multipart("/api/v1/cases/{id}/documents", caseId).file(file))
            .andExpect(status().isBadRequest())
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(jsonPath("$.code").value("TOO_MANY_DOCUMENTS"))
            .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("POST /api/v1/cases/{id}/documents: non-existent case returns 404 CASE_NOT_FOUND")
    void uploadNonExistentCaseReturns404() throws Exception {
        UUID caseId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
            "file", "doc.pdf", "application/pdf", "dummy".getBytes()
        );

        when(caseService.uploadDocuments(eq(caseId), any(), any(), any()))
            .thenThrow(new CaseNotFoundException(caseId));

        mockMvc.perform(multipart("/api/v1/cases/{id}/documents", caseId).file(file))
            .andExpect(status().isNotFound())
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(jsonPath("$.code").value("CASE_NOT_FOUND"))
            .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("POST /api/v1/cases/{id}/documents: valid upload returns 201 with DocumentResponse list")
    void uploadValidDocumentsSuccess() throws Exception {
        UUID caseId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
            "file", "INV-101.pdf", "application/pdf", "%PDF dummy".getBytes()
        );

        DocumentResponse docResponse = new DocumentResponse(
            docId, caseId, "INVOICE", "INV-101.pdf", "application/pdf",
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            1, LocalDate.of(2024, 1, 10), Instant.now()
        );

        when(caseService.uploadDocuments(eq(caseId), any(), any(), any()))
            .thenReturn(List.of(docResponse));

        mockMvc.perform(multipart("/api/v1/cases/{id}/documents", caseId)
                .file(file)
                .param("doc_type", "INVOICE"))
            .andExpect(status().isCreated())
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(jsonPath("$[0].id").value(docId.toString()))
            .andExpect(jsonPath("$[0].doc_type").value("INVOICE"))
            .andExpect(jsonPath("$[0].file_name").value("INV-101.pdf"));
    }

    @Test
    @DisplayName("GET /api/v1/cases/{id}: non-existent case returns 404 CASE_NOT_FOUND")
    void getCaseNotFoundReturns404() throws Exception {
        UUID caseId = UUID.randomUUID();
        when(caseService.getCase(caseId)).thenThrow(new CaseNotFoundException(caseId));

        mockMvc.perform(get("/api/v1/cases/{id}", caseId))
            .andExpect(status().isNotFound())
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(jsonPath("$.code").value("CASE_NOT_FOUND"))
            .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("GET /api/v1/cases/{id}: existing case returns 200 with details")
    void getCaseSuccess() throws Exception {
        UUID caseId = UUID.randomUUID();
        CaseDetailResponse detail = new CaseDetailResponse(
            caseId, "DP-002", "Service Dispute", "Alpha Services", "Beta Tech",
            "MICRO", LocalDate.of(2023, 5, 20), false, Instant.now(), 1,
            List.of(), null
        );

        when(caseService.getCase(caseId)).thenReturn(detail);

        mockMvc.perform(get("/api/v1/cases/{id}", caseId))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(jsonPath("$.id").value(caseId.toString()))
            .andExpect(jsonPath("$.code").value("DP-002"))
            .andExpect(jsonPath("$.supplier_category").value("MICRO"));
    }
}
