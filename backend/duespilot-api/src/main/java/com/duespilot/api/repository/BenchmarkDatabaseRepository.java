package com.duespilot.api.repository;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.io.Closeable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Repository for managing benchmark datasets, cases, case files, and content-addressed blobs
 * stored in the database per architecture §14.
 */
@Repository
public class BenchmarkDatabaseRepository implements Closeable {

    private final JdbcClient jdbcClient;
    private final @Nullable DataSource dataSource;

    @org.springframework.beans.factory.annotation.Autowired
    public BenchmarkDatabaseRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
        this.dataSource = null;
    }

    public BenchmarkDatabaseRepository(DataSource dataSource) {
        this.dataSource = dataSource;
        this.jdbcClient = JdbcClient.create(dataSource);
    }

    public record BenchmarkFileRecord(
        String fileName,
        String docType,
        String blobSha256
    ) {}

    public void saveDataset(@NonNull String name, @NonNull String version, long seed) {
        String sql = """
            INSERT INTO app.benchmark_datasets (name, version, seed, created_at)
            VALUES (:name, :version, :seed, NOW())
            ON CONFLICT (name, version) DO UPDATE SET
                seed = EXCLUDED.seed,
                created_at = NOW()
            """;
        jdbcClient.sql(sql)
            .param("name", name)
            .param("version", version)
            .param("seed", seed)
            .update();
    }

    public void saveCase(
            @NonNull String dataset,
            @NonNull String caseCode,
            @NonNull String groundTruthJson,
            @Nullable String metadataJson) {
        String sql = """
            INSERT INTO app.benchmark_cases (dataset, case_code, ground_truth, metadata, created_at)
            VALUES (:dataset, :caseCode, :gt::jsonb, :meta::jsonb, NOW())
            ON CONFLICT (dataset, case_code) DO UPDATE SET
                ground_truth = EXCLUDED.ground_truth,
                metadata = EXCLUDED.metadata,
                created_at = NOW()
            """;
        jdbcClient.sql(sql)
            .param("dataset", dataset)
            .param("caseCode", caseCode)
            .param("gt", groundTruthJson)
            .param("meta", metadataJson != null ? metadataJson : "{}")
            .update();
    }

    public void saveCaseFile(
            @NonNull String dataset,
            @NonNull String caseCode,
            @NonNull String fileName,
            @NonNull String docType,
            @NonNull String blobSha256) {
        String sql = """
            INSERT INTO app.benchmark_case_files (dataset, case_code, file_name, doc_type, blob_sha256, created_at)
            VALUES (:dataset, :caseCode, :fileName, :docType, :blobSha256, NOW())
            ON CONFLICT (dataset, case_code, file_name) DO UPDATE SET
                doc_type = EXCLUDED.doc_type,
                blob_sha256 = EXCLUDED.blob_sha256,
                created_at = NOW()
            """;
        jdbcClient.sql(sql)
            .param("dataset", dataset)
            .param("caseCode", caseCode)
            .param("fileName", fileName)
            .param("docType", docType)
            .param("blobSha256", blobSha256.trim())
            .update();
    }

    public void saveFileBlob(
            @NonNull String sha256,
            byte @NonNull [] bytes,
            @NonNull String mimeType,
            long sizeBytes) {
        String sql = """
            INSERT INTO app.file_blobs (sha256, bytes, mime_type, size_bytes, created_at)
            VALUES (:sha256, :bytes, :mimeType, :sizeBytes, NOW())
            ON CONFLICT (sha256) DO NOTHING
            """;
        jdbcClient.sql(sql)
            .param("sha256", sha256.trim())
            .param("bytes", bytes)
            .param("mimeType", mimeType)
            .param("sizeBytes", sizeBytes)
            .update();
    }

    public List<String> findCaseCodes(@NonNull String dataset) {
        return jdbcClient.sql("SELECT case_code FROM app.benchmark_cases WHERE dataset = :dataset ORDER BY case_code ASC")
            .param("dataset", dataset)
            .query((rs, rowNum) -> rs.getString("case_code"))
            .list();
    }

    public Optional<String> findCaseGroundTruth(@NonNull String dataset, @NonNull String caseCode) {
        return jdbcClient.sql("SELECT ground_truth::text FROM app.benchmark_cases WHERE dataset = :dataset AND case_code = :caseCode")
            .param("dataset", dataset)
            .param("caseCode", caseCode)
            .query((rs, rowNum) -> rs.getString(1))
            .optional();
    }

    public List<BenchmarkFileRecord> findCaseFiles(@NonNull String dataset, @NonNull String caseCode) {
        return jdbcClient.sql("SELECT file_name, doc_type, blob_sha256 FROM app.benchmark_case_files WHERE dataset = :dataset AND case_code = :caseCode ORDER BY file_name ASC")
            .param("dataset", dataset)
            .param("caseCode", caseCode)
            .query((rs, rowNum) -> new BenchmarkFileRecord(
                rs.getString("file_name"),
                rs.getString("doc_type"),
                rs.getString("blob_sha256").trim()
            ))
            .list();
    }

    public Optional<byte[]> findBlobBytes(@NonNull String sha256) {
        return jdbcClient.sql("SELECT bytes FROM app.file_blobs WHERE sha256 = :sha256")
            .param("sha256", sha256.trim())
            .query((rs, rowNum) -> rs.getBytes("bytes"))
            .optional();
    }

    public long countCases(@NonNull String dataset) {
        Long count = jdbcClient.sql("SELECT COUNT(*) FROM app.benchmark_cases WHERE dataset = :dataset")
            .param("dataset", dataset)
            .query(Long.class)
            .single();
        return count != null ? count : 0L;
    }

    @Override
    public void close() {
        if (dataSource instanceof HikariDataSource hikari) {
            hikari.close();
        }
    }

    public static BenchmarkDatabaseRepository createStandalone() {
        Map<String, String> envMap = loadEnvSettings();

        String url = getVal(envMap, "DATABASE_URL", "SPRING_DATASOURCE_URL");
        if (url == null || url.isBlank()) {
            String port = getVal(envMap, "POSTGRES_PORT");
            if (port == null || port.isBlank()) port = "5433";
            url = "jdbc:postgresql://localhost:" + port + "/duespilot";
        }

        String user = getVal(envMap, "SPRING_DATASOURCE_USERNAME", "POSTGRES_USER");
        if (user == null || user.isBlank()) user = "duespilot";

        String pass = getVal(envMap, "SPRING_DATASOURCE_PASSWORD", "POSTGRES_PASSWORD");
        if (pass == null) pass = "duespilot";

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(user);
        config.setPassword(pass);
        config.setSchema("app");
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(10000);

        HikariDataSource ds = new HikariDataSource(config);
        return new BenchmarkDatabaseRepository(ds);
    }

    private static String getVal(Map<String, String> map, String... keys) {
        for (String k : keys) {
            String sys = System.getenv(k);
            if (sys != null && !sys.isBlank()) return sys;
            String fileVal = map.get(k);
            if (fileVal != null && !fileVal.isBlank()) return fileVal;
        }
        return null;
    }

    private static Map<String, String> loadEnvSettings() {
        Map<String, String> map = new HashMap<>();
        for (Path envPath : List.of(Paths.get(".env"), Paths.get("..", ".env"), Paths.get("../..", ".env"))) {
            if (Files.exists(envPath)) {
                try {
                    List<String> lines = Files.readAllLines(envPath);
                    for (String line : lines) {
                        String trimmed = line.trim();
                        if (!trimmed.startsWith("#") && trimmed.contains("=")) {
                            int idx = trimmed.indexOf('=');
                            String key = trimmed.substring(0, idx).trim();
                            String val = trimmed.substring(idx + 1).trim();
                            map.putIfAbsent(key, val);
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
        return map;
    }
}
