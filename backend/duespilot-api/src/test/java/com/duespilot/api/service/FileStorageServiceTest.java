package com.duespilot.api.service;

import com.duespilot.api.repository.FileBlobRepository;
import com.duespilot.api.web.error.FileTooLargeException;
import com.duespilot.api.web.error.UnsupportedDocumentTypeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileStorageServiceTest {

    private FileBlobRepository fileBlobRepository;
    private FileStorageService storageService;

    @BeforeEach
    void setUp() {
        fileBlobRepository = org.mockito.Mockito.mock(FileBlobRepository.class);
        storageService = new FileStorageService(fileBlobRepository);
    }

    @Test
    @DisplayName("Successfully stores valid PDF and computes sha256 and sanitised filename into DB blob")
    void storeValidPdf() {
        byte[] content = "%PDF-1.4 dummy pdf content".getBytes(StandardCharsets.UTF_8);
        var stored = storageService.storeFile(content, "Invoice #101.pdf", "application/pdf");

        assertThat(stored.id()).isNotNull();
        assertThat(stored.sanitizedFileName()).isEqualTo("Invoice_101.pdf");
        assertThat(stored.mimeType()).isEqualTo("application/pdf");
        assertThat(stored.sha256()).hasSize(64);
        assertThat(stored.sizeBytes()).isEqualTo(content.length);
        assertThat(stored.storagePath()).isEqualTo("db_blob:" + stored.sha256());
        org.mockito.Mockito.verify(fileBlobRepository).save(
            org.mockito.ArgumentMatchers.eq(stored.sha256()),
            org.mockito.ArgumentMatchers.eq(content),
            org.mockito.ArgumentMatchers.eq("application/pdf"),
            org.mockito.ArgumentMatchers.eq((long) content.length)
        );
    }

    @Test
    @DisplayName("Rejects unsupported extension per Rule R-53 (400 UNSUPPORTED_TYPE)")
    void rejectsDisallowedExtension() {
        byte[] content = "malicious payload".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> storageService.storeFile(content, "script.sh", "text/x-shellscript"))
            .isInstanceOf(UnsupportedDocumentTypeException.class)
            .hasMessageContaining("sh")
            .satisfies(ex -> {
                UnsupportedDocumentTypeException e = (UnsupportedDocumentTypeException) ex;
                assertThat(e.getCode()).isEqualTo("UNSUPPORTED_TYPE");
                assertThat(e.getStatus().value()).isEqualTo(400);
            });
    }

    @Test
    @DisplayName("Rejects file exceeding 5 MB per Postgres bytea (413 FILE_TOO_LARGE)")
    void rejectsFileTooLarge() {
        byte[] largeContent = new byte[(int) FileStorageService.MAX_FILE_SIZE_BYTES + 1];

        assertThatThrownBy(() -> storageService.storeFile(largeContent, "big_doc.pdf", "application/pdf"))
            .isInstanceOf(FileTooLargeException.class)
            .satisfies(ex -> {
                FileTooLargeException e = (FileTooLargeException) ex;
                assertThat(e.getCode()).isEqualTo("FILE_TOO_LARGE");
                assertThat(e.getStatus().value()).isEqualTo(413);
            });
    }

    @Test
    @DisplayName("Sanitises path traversal and unsafe characters")
    void sanitisesFileNames() {
        assertThat(FileStorageService.sanitizeFileName("../../etc/passwd.txt")).isEqualTo("passwd.txt");
        assertThat(FileStorageService.sanitizeFileName(".hidden.pdf")).isEqualTo("doc.hidden.pdf");
        assertThat(FileStorageService.sanitizeFileName("Invoice 2024*#@!$.pdf")).isEqualTo("Invoice_2024_.pdf");
    }
}
