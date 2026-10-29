package com.duespilot.api.service;

import com.duespilot.api.domain.FileBlobEntity;
import com.duespilot.api.repository.FileBlobRepository;
import com.duespilot.api.web.error.FileTooLargeException;
import com.duespilot.api.web.error.UnsupportedDocumentTypeException;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * File storage service storing all files and evidence in content-addressed database tables (app.file_blobs).
 * No PDF or document file may live on the server's disk or in the git repository.
 * Temporary data stays in memory.
 */
@Service
public class FileStorageService {

    public static final long MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024; // 5 MB limit

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "txt", "png", "jpg", "jpeg");
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
        "application/pdf",
        "text/plain",
        "image/png",
        "image/jpeg",
        "application/octet-stream" // browser fallback when uploading text or pdf
    );

    private final @Nullable FileBlobRepository fileBlobRepository;

    @Autowired
    public FileStorageService(@Nullable FileBlobRepository fileBlobRepository) {
        this.fileBlobRepository = fileBlobRepository;
    }

    public FileStorageService() {
        this(null);
    }

    public record StoredFileInfo(
        UUID id,
        String sanitizedFileName,
        String mimeType,
        String sha256,
        String storagePath,
        long sizeBytes
    ) {}

    public StoredFileInfo storeFile(byte[] content, String originalFilename, String rawContentType) {
        if (content.length > MAX_FILE_SIZE_BYTES) {
            throw new FileTooLargeException(
                "Uploaded file exceeds maximum allowed size of 5 MB (actual: " + content.length + " bytes) (Postgres bytea limit)."
            );
        }

        String sanitizedName = sanitizeFileName(originalFilename);
        String extension = getFileExtension(sanitizedName);

        if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT))) {
            throw new UnsupportedDocumentTypeException(
                "File extension '" + extension + "' is not supported. Allowed extensions: " + ALLOWED_EXTENSIONS + " (Rule R-53)."
            );
        }

        String mimeType = normalizeMimeType(rawContentType, extension);
        String sha256 = calculateSha256(content);
        UUID documentId = UUID.randomUUID();

        // Persist content-addressed blob into database (zero filesystem storage)
        if (fileBlobRepository != null) {
            fileBlobRepository.save(sha256, content, mimeType, content.length);
        }

        return new StoredFileInfo(
            documentId,
            sanitizedName,
            mimeType,
            sha256,
            "db_blob:" + sha256,
            content.length
        );
    }

    public Optional<byte[]> getFileBytes(String sha256) {
        if (fileBlobRepository == null || sha256 == null) {
            return Optional.empty();
        }
        return fileBlobRepository.findBytesBySha256(sha256.trim());
    }

    public static String sanitizeFileName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "document.bin";
        }
        // Normalize slashes and strip any directory path traversal
        String name = originalFilename.replace('\\', '/');
        int lastSlash = name.lastIndexOf('/');
        if (lastSlash >= 0) {
            name = name.substring(lastSlash + 1);
        }
        // Strip leading .. or .
        while (name.startsWith("../") || name.startsWith("..\\")) {
            name = name.substring(3);
        }
        // Replace dangerous characters with underscore
        String sanitized = name.replaceAll("[^a-zA-Z0-9._-]", "_");
        // Collapse consecutive underscores
        sanitized = sanitized.replaceAll("_{2,}", "_");
        if (sanitized.startsWith(".")) {
            sanitized = "doc" + sanitized;
        }
        if (sanitized.isBlank() || sanitized.equals("_")) {
            return "document.bin";
        }
        return sanitized.length() > 150 ? sanitized.substring(sanitized.length() - 150) : sanitized;
    }

    public static String getFileExtension(String filename) {
        if (filename == null) return "";
        String name = filename.replace('\\', '/');
        int lastSlash = name.lastIndexOf('/');
        if (lastSlash >= 0) {
            name = name.substring(lastSlash + 1);
        }
        int lastDot = name.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < name.length() - 1) {
            return name.substring(lastDot + 1).toLowerCase(Locale.ROOT);
        }
        return "";
    }

    public static String normalizeMimeType(String contentType, String extension) {
        if (contentType != null && !contentType.isBlank() && !contentType.equals("application/octet-stream")) {
            return contentType;
        }
        return switch (extension.toLowerCase(Locale.ROOT)) {
            case "pdf" -> "application/pdf";
            case "txt" -> "text/plain";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            default -> "application/octet-stream";
        };
    }

    public static String calculateSha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 message digest algorithm not available", e);
        }
    }

    public boolean deleteFile(String storagePath) {
        // Files are content-addressed blobs in app.file_blobs, unreferenced or purged by CaseRetentionService
        return true;
    }
}
