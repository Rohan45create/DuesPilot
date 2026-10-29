package com.duespilot.bench.generator;

import com.duespilot.api.repository.BenchmarkDatabaseRepository;
import com.duespilot.api.service.FileStorageService;
import com.duespilot.bench.holdout.HoldoutCaseCreator;
import com.duespilot.bench.model.BenchmarkCaseData;
import com.duespilot.bench.model.GroundTruth;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Command-line entry point for benchmark data generation.
 *
 * Usage:
 *   generate --target db [--seed 42] [--count 40] [--export-dir benchmark-export]
 */
public final class BenchmarkGeneratorCli {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
        .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
        .build();

    public static void main(String[] args) {
        try {
            long seed = 42L;
            int count = 40;
            String target = "db"; // default target is db
            Path exportDir = null;
            String dataset = "all";

            int i = 0;
            if (args.length > 0 && "generate".equalsIgnoreCase(args[0])) {
                i = 1;
            }

            while (i < args.length) {
                String arg = args[i];
                if ("--target".equalsIgnoreCase(arg) && i + 1 < args.length) {
                    target = args[++i].toLowerCase();
                } else if ("--seed".equalsIgnoreCase(arg) && i + 1 < args.length) {
                    seed = Long.parseLong(args[++i]);
                } else if ("--count".equalsIgnoreCase(arg) && i + 1 < args.length) {
                    count = Integer.parseInt(args[++i]);
                } else if (("--export-dir".equalsIgnoreCase(arg) || "--out".equalsIgnoreCase(arg)) && i + 1 < args.length) {
                    exportDir = Paths.get(args[++i]);
                } else if ("--dataset".equalsIgnoreCase(arg) && i + 1 < args.length) {
                    dataset = args[++i].toLowerCase();
                } else {
                    System.err.println("Unknown argument: " + arg);
                }
                i++;
            }

            System.out.printf("Starting benchmark generator: target=%s, seed=%d, count=%d, dataset=%s%s%n",
                target, seed, count, dataset, exportDir != null ? (", exportDir=" + exportDir.toAbsolutePath()) : " (in-memory only)");

            if ("db".equalsIgnoreCase(target)) {
                try (BenchmarkDatabaseRepository dbRepo = BenchmarkDatabaseRepository.createStandalone()) {
                    if ("generated".equalsIgnoreCase(dataset) || "all".equalsIgnoreCase(dataset)) {
                        Path genExport = exportDir != null ? exportDir.resolve("generated") : null;
                        System.out.println("Rendering 40 synthetic benchmark cases in memory...");
                        List<BenchmarkCaseData> genCases = CaseGenerator.generate(seed, count, genExport);
                        dbRepo.saveDataset("generated", "v1.0", seed);
                        persistCasesToDatabase(dbRepo, "generated", genCases);
                        System.out.printf("Successfully stored %d generated cases and PDF blobs in database.%n", genCases.size());
                    }

                    if ("holdout".equalsIgnoreCase(dataset) || "all".equalsIgnoreCase(dataset)) {
                        Path holdoutExport = exportDir != null ? exportDir.resolve("holdout") : null;
                        System.out.println("Rendering 8 hand-written holdout cases in memory...");
                        List<BenchmarkCaseData> holdoutCases = HoldoutCaseCreator.generateHoldoutCases(holdoutExport);
                        dbRepo.saveDataset("holdout", "v1.0", 0L);
                        persistCasesToDatabase(dbRepo, "holdout", holdoutCases);
                        System.out.printf("Successfully stored %d holdout cases and PDF blobs in database.%n", holdoutCases.size());
                    }

                    long totalCases = dbRepo.countCases("generated") + dbRepo.countCases("holdout");
                    System.out.printf("=== Database Benchmark Generation Complete: %d total cases in DB ===%n", totalCases);
                }
            } else {
                // File-system only generation
                Path out = exportDir != null ? exportDir : Paths.get("benchmark-data", "generated");
                CaseGenerator.generate(seed, count, out);
                System.out.printf("Generated %d cases to filesystem at: %s%n", count, out.toAbsolutePath());
            }

        } catch (Exception ex) {
            System.err.println("Generator failed: " + ex.getMessage());
            ex.printStackTrace();
            System.exit(1);
        }
    }

    private static void persistCasesToDatabase(
            BenchmarkDatabaseRepository dbRepo,
            String dataset,
            List<BenchmarkCaseData> cases) throws Exception {

        for (BenchmarkCaseData c : cases) {
            // 1. Save document blobs and case files
            for (var entry : c.documentFiles().entrySet()) {
                String fileName = entry.getKey();
                byte[] bytes = entry.getValue();
                String sha256 = FileStorageService.calculateSha256(bytes);

                // Content-addressed deduplication in app.file_blobs
                dbRepo.saveFileBlob(sha256, bytes, "application/pdf", bytes.length);

                String docType = resolveDocType(c.groundTruth(), fileName);
                dbRepo.saveCaseFile(dataset, c.caseCode(), fileName, docType, sha256);
            }

            // 2. Save ground truth and case record
            String gtJson = MAPPER.writeValueAsString(c.groundTruth());
            dbRepo.saveCase(dataset, c.caseCode(), gtJson, "{}");
        }
    }

    private static String resolveDocType(GroundTruth gt, String fileName) {
        for (GroundTruth.DocumentMeta dm : gt.documents()) {
            if (fileName.equalsIgnoreCase(dm.fileName())) {
                return dm.docType();
            }
        }
        if (fileName.startsWith("INV")) return "INVOICE";
        if (fileName.startsWith("PO")) return "PO";
        if (fileName.startsWith("DC")) return "DELIVERY";
        if (fileName.startsWith("PAY")) return "PAYMENT";
        if (fileName.startsWith("CN")) return "CREDIT_NOTE";
        if (fileName.startsWith("MSG")) return "MESSAGE";
        return "DOCUMENT";
    }
}
