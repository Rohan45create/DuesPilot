package com.duespilot.bench.runner;

import com.duespilot.api.domain.DocType;
import com.duespilot.api.llm.ExtractionPolicy;
import com.duespilot.api.llm.JsonValidator;
import com.duespilot.api.llm.LlmClient;
import com.duespilot.api.llm.LlmProperties;
import com.duespilot.api.llm.ModelRouter;
import com.duespilot.api.llm.RestClientLlmClient;
import com.duespilot.api.repository.BenchmarkDatabaseRepository;
import com.duespilot.api.repository.LlmCallRepository;
import com.duespilot.core.extract.VerifiedField;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Opt-in live benchmark runner to extract one generated benchmark case
 * directly from the database (or filesystem fallback) and print field-level accuracy against ground truth.
 *
 * Implements architecture.md section 14 and rules R-26, R-27, R-32, R-61.
 *
 * Usage:
 *   extract --case GEN-001 [--mock]
 */
public final class CaseExtractionBenchmarkRunner {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    public static void main(String[] args) {
        String caseCode = "GEN-001";
        Path dataDir = Paths.get("benchmark-data", "generated");
        boolean mock = false;

        int i = 0;
        if (args.length > 0 && "extract".equalsIgnoreCase(args[0])) {
            i = 1;
        }

        while (i < args.length) {
            String arg = args[i];
            if ("--case".equalsIgnoreCase(arg) && i + 1 < args.length) {
                caseCode = args[++i];
            } else if ("--data".equalsIgnoreCase(arg) && i + 1 < args.length) {
                dataDir = Paths.get(args[++i]);
            } else if ("--mock".equalsIgnoreCase(arg)) {
                mock = true;
            }
            i++;
        }

        // 1. Try loading from database first
        try (BenchmarkDatabaseRepository dbRepo = BenchmarkDatabaseRepository.createStandalone()) {
            String gtJson = null;
            String dataset = caseCode.startsWith("HOLD-") ? "holdout" : "generated";
            var gtOpt = dbRepo.findCaseGroundTruth(dataset, caseCode);
            if (gtOpt.isPresent()) {
                gtJson = gtOpt.get();
            } else {
                // Try the other dataset as fallback
                String otherDataset = dataset.equals("holdout") ? "generated" : "holdout";
                gtOpt = dbRepo.findCaseGroundTruth(otherDataset, caseCode);
                if (gtOpt.isPresent()) {
                    gtJson = gtOpt.get();
                    dataset = otherDataset;
                }
            }

            if (gtJson != null) {
                System.out.printf("Loaded case %s from database (dataset: %s)%n", caseCode, dataset);
                JsonNode groundTruth = MAPPER.readTree(gtJson);

                List<BenchmarkDatabaseRepository.BenchmarkFileRecord> fileRecs = dbRepo.findCaseFiles(dataset, caseCode);
                Map<String, byte[]> fileMap = new HashMap<>();
                for (var fr : fileRecs) {
                    dbRepo.findBlobBytes(fr.blobSha256()).ifPresent(bytes -> fileMap.put(fr.fileName(), bytes));
                }

                runExtractionAndScoreInMemory(caseCode, groundTruth, fileMap, mock);
                return;
            }
        } catch (Exception e) {
            System.out.println("[NOTE] Could not load case from DB: " + e.getMessage() + ". Trying filesystem.");
        }

        // 2. Fallback to filesystem
        Path casePath = dataDir.resolve(caseCode);
        if (!Files.exists(casePath)) {
            Path parentCandidate = Paths.get("..").resolve(dataDir).resolve(caseCode);
            if (Files.exists(parentCandidate)) {
                casePath = parentCandidate;
            } else {
                System.err.println("Error: Case not found in database or directory: " + casePath.toAbsolutePath());
                return;
            }
        }

        Path groundTruthPath = casePath.resolve("ground_truth.json");
        if (!Files.exists(groundTruthPath)) {
            System.err.println("Error: ground_truth.json not found in " + casePath.toAbsolutePath());
            return;
        }

        try {
            runExtractionAndScore(caseCode, casePath, groundTruthPath, mock);
        } catch (Exception ex) {
            System.err.println("Benchmark extraction execution failed: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    public static void runExtractionAndScoreInMemory(
            String caseCode,
            JsonNode groundTruth,
            Map<String, byte[]> fileMap,
            boolean mock) throws Exception {

        System.out.println("========================================================================================");
        System.out.printf("DuesPilot Live Benchmark Extraction Evaluation (from Database): %s%n", caseCode);
        System.out.println("========================================================================================");

        String apiKey = resolveApiKey();
        if ((apiKey == null || apiKey.isBlank()) && !mock) {
            System.out.println("[NOTE] Live extraction requires NEBIUS_API_KEY or NVIDIA_API_KEY in environment or .env.");
            System.out.println("No API key detected. Running in verification/fixture mode (--mock).");
            mock = true;
        }

        LlmClient llmClient;
        LlmProperties properties = createProperties(apiKey);
        JsonValidator jsonValidator = new JsonValidator();

        if (mock) {
            llmClient = createMockLlmClient(groundTruth);
        } else {
            llmClient = new RestClientLlmClient(properties, new NoopLlmCallRepository(), org.springframework.web.client.RestClient.builder());
        }

        ExtractionPolicy extractionPolicy = new ExtractionPolicy(llmClient, properties, new NoopLlmCallRepository(), jsonValidator);
        ModelRouter router = new ModelRouter(extractionPolicy, null, null);

        JsonNode docsNode = groundTruth.get("documents");
        if (docsNode == null || !docsNode.isArray()) {
            System.err.println("Error: No documents listed in ground truth");
            return;
        }

        int totalEvaluated = 0;
        int totalMatched = 0;

        for (JsonNode docNode : docsNode) {
            String fileName = docNode.path("file_name").asText();
            String docTypeStr = docNode.path("doc_type").asText();
            DocType docType = DocType.fromString(docTypeStr);
            byte[] fileBytes = fileMap.get(fileName);

            if (fileBytes == null) {
                System.out.println("Skipping missing file in blob storage: " + fileName);
                continue;
            }

            Map<Integer, String> pages = extractPages(fileBytes);
            System.out.printf("%nDocument: %s (%s, %d pages)%n", fileName, docType.name(), pages.size());

            var result = router.extractDocument(docType, fileName, pages, null, UUID.randomUUID());
            System.out.printf("Model used: %s | Escalations: %s%n", result.finalModel(), result.escalationHistory());

            Map<String, VerifiedField> extractedMap = new LinkedHashMap<>();
            for (VerifiedField vf : result.verifiedFields()) {
                extractedMap.put(vf.fieldName().toUpperCase(Locale.ROOT), vf);
            }

            List<FieldCheck> checks = buildFieldChecks(docType, docNode, groundTruth);

            System.out.println("------------------------------------------------------------------------------------------------");
            System.out.printf("%-20s | %-25s | %-25s | %-12s | %-8s%n", "FIELD", "EXPECTED", "EXTRACTED", "STATUS", "MATCH");
            System.out.println("------------------------------------------------------------------------------------------------");

            int docMatched = 0;
            for (FieldCheck check : checks) {
                totalEvaluated++;
                VerifiedField extracted = findField(extractedMap, check.fieldName());
                String extractedVal = extracted != null ? (extracted.valueText() != null ? extracted.valueText() : "") : "<MISSING>";
                String status = extracted != null ? extracted.status().name() : "MISSING";

                boolean isMatch = check.matches(extracted);
                if (isMatch) {
                    docMatched++;
                    totalMatched++;
                }

                System.out.printf("%-20s | %-25s | %-25s | %-12s | %-8s%n",
                    check.fieldName(),
                    truncate(check.expectedValue(), 25),
                    truncate(extractedVal, 25),
                    status,
                    isMatch ? "PASS" : "FAIL"
                );
            }
            System.out.printf("Document Score: %d / %d fields matched%n", docMatched, checks.size());
        }

        System.out.println("========================================================================================");
        double accuracy = totalEvaluated > 0 ? (totalMatched * 100.0 / totalEvaluated) : 0.0;
        System.out.printf("OVERALL EXTRACTION ACCURACY for %s: %d / %d (%.1f%%)%n", caseCode, totalMatched, totalEvaluated, accuracy);
        System.out.println("========================================================================================");
    }

    public static void runExtractionAndScore(String caseCode, Path casePath, Path groundTruthPath, boolean mock) throws Exception {
        JsonNode groundTruth = MAPPER.readTree(groundTruthPath.toFile());
        System.out.println("========================================================================================");
        System.out.printf("DuesPilot Live Benchmark Extraction Evaluation: %s%n", caseCode);
        System.out.printf("Case Path: %s%n", casePath.toAbsolutePath());
        System.out.println("========================================================================================");

        String apiKey = resolveApiKey();
        if ((apiKey == null || apiKey.isBlank()) && !mock) {
            System.out.println("[NOTE] Live extraction requires NEBIUS_API_KEY or NVIDIA_API_KEY in environment or .env.");
            System.out.println("No API key detected. Running in verification/fixture mode (--mock).");
            mock = true;
        }

        LlmClient llmClient;
        LlmProperties properties = createProperties(apiKey);
        JsonValidator jsonValidator = new JsonValidator();

        if (mock) {
            llmClient = createMockLlmClient(groundTruth);
        } else {
            llmClient = new RestClientLlmClient(properties, new NoopLlmCallRepository(), org.springframework.web.client.RestClient.builder());
        }

        ExtractionPolicy extractionPolicy = new ExtractionPolicy(llmClient, properties, new NoopLlmCallRepository(), jsonValidator);
        ModelRouter router = new ModelRouter(extractionPolicy, null, null);

        JsonNode docsNode = groundTruth.get("documents");
        if (docsNode == null || !docsNode.isArray()) {
            System.err.println("Error: No documents listed in ground truth");
            return;
        }

        int totalEvaluated = 0;
        int totalMatched = 0;

        for (JsonNode docNode : docsNode) {
            String fileName = docNode.path("file_name").asText();
            String docTypeStr = docNode.path("doc_type").asText();
            DocType docType = DocType.fromString(docTypeStr);
            Path filePath = casePath.resolve(fileName);
            if (!Files.exists(filePath)) {
                filePath = casePath.resolve("documents").resolve(fileName);
            }

            if (!Files.exists(filePath)) {
                System.out.println("Skipping missing file: " + fileName);
                continue;
            }

            Map<Integer, String> pages = extractPages(Files.readAllBytes(filePath));
            System.out.printf("%nDocument: %s (%s, %d pages)%n", fileName, docType.name(), pages.size());

            var result = router.extractDocument(docType, fileName, pages, null, UUID.randomUUID());
            System.out.printf("Model used: %s | Escalations: %s%n", result.finalModel(), result.escalationHistory());

            Map<String, VerifiedField> extractedMap = new LinkedHashMap<>();
            for (VerifiedField vf : result.verifiedFields()) {
                extractedMap.put(vf.fieldName().toUpperCase(Locale.ROOT), vf);
            }

            List<FieldCheck> checks = buildFieldChecks(docType, docNode, groundTruth);

            System.out.println("------------------------------------------------------------------------------------------------");
            System.out.printf("%-20s | %-25s | %-25s | %-12s | %-8s%n", "FIELD", "EXPECTED", "EXTRACTED", "STATUS", "MATCH");
            System.out.println("------------------------------------------------------------------------------------------------");

            int docMatched = 0;
            for (FieldCheck check : checks) {
                totalEvaluated++;
                VerifiedField extracted = findField(extractedMap, check.fieldName());
                String extractedVal = extracted != null ? (extracted.valueText() != null ? extracted.valueText() : "") : "<MISSING>";
                String status = extracted != null ? extracted.status().name() : "MISSING";

                boolean isMatch = check.matches(extracted);
                if (isMatch) {
                    docMatched++;
                    totalMatched++;
                }

                System.out.printf("%-20s | %-25s | %-25s | %-12s | %-8s%n",
                    check.fieldName(),
                    truncate(check.expectedValue(), 25),
                    truncate(extractedVal, 25),
                    status,
                    isMatch ? "PASS" : "FAIL"
                );
            }
            System.out.printf("Document Score: %d / %d fields matched%n", docMatched, checks.size());
        }

        System.out.println("========================================================================================");
        double accuracy = totalEvaluated > 0 ? (totalMatched * 100.0 / totalEvaluated) : 0.0;
        System.out.printf("OVERALL EXTRACTION ACCURACY for %s: %d / %d (%.1f%%)%n", caseCode, totalMatched, totalEvaluated, accuracy);
        System.out.println("========================================================================================");
    }

    private static VerifiedField findField(Map<String, VerifiedField> map, String name) {
        String n = name.toUpperCase(Locale.ROOT);
        if (map.containsKey(n)) return map.get(n);
        if ("INVOICE_NUMBER".equals(n) && map.containsKey("DOCUMENT_NUMBER")) return map.get("DOCUMENT_NUMBER");
        if ("TOTAL_AMOUNT".equals(n) && map.containsKey("TOTAL_INVOICE_AMOUNT")) return map.get("TOTAL_INVOICE_AMOUNT");
        if ("TOTAL_AMOUNT".equals(n) && map.containsKey("PAYMENT_AMOUNT")) return map.get("PAYMENT_AMOUNT");
        return null;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }

    private static Map<Integer, String> extractPages(byte[] pdfBytes) throws IOException {
        Map<Integer, String> pages = new LinkedHashMap<>();
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            int count = doc.getNumberOfPages();
            for (int p = 1; p <= count; p++) {
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                String text = stripper.getText(doc);
                pages.put(p, text != null ? text.trim() : "");
            }
        }
        return pages;
    }

    private static record FieldCheck(String fieldName, String expectedValue, java.util.function.Predicate<VerifiedField> matcher) {
        public boolean matches(VerifiedField vf) {
            if (vf == null || !vf.isVerified()) return false;
            return matcher.test(vf);
        }
    }

    private static List<FieldCheck> buildFieldChecks(DocType docType, JsonNode docNode, JsonNode groundTruth) {
        List<FieldCheck> list = new ArrayList<>();

        if (docType == DocType.INVOICE) {
            String ref = docNode.path("doc_reference").asText();
            list.add(new FieldCheck("INVOICE_NUMBER", ref, vf -> vf.valueText() != null && vf.valueText().contains(ref)));

            String date = docNode.path("doc_date").asText();
            list.add(new FieldCheck("INVOICE_DATE", date, vf -> vf.valueDate().isValue() && vf.valueDate().getOrThrow().toString().equals(date)));

            long amountPaise = docNode.path("amount_paise").asLong();
            list.add(new FieldCheck("TOTAL_AMOUNT", amountPaise + " paise", vf -> vf.valuePaise().isValue() && vf.valuePaise().getOrThrow().value() == amountPaise));

            String supplier = groundTruth.path("supplier").path("name").asText();
            list.add(new FieldCheck("SUPPLIER_NAME", supplier, vf -> vf.valueText() != null && supplier.toLowerCase().contains(vf.valueText().toLowerCase())));
        } else if (docType == DocType.PO) {
            String ref = docNode.path("doc_reference").asText();
            list.add(new FieldCheck("PO_NUMBER", ref, vf -> vf.valueText() != null && vf.valueText().contains(ref)));

            long amountPaise = docNode.path("amount_paise").asLong();
            list.add(new FieldCheck("TOTAL_AMOUNT", amountPaise + " paise", vf -> vf.valuePaise().isValue() && vf.valuePaise().getOrThrow().value() == amountPaise));
        } else if (docType == DocType.PAYMENT) {
            String ref = docNode.path("doc_reference").asText();
            list.add(new FieldCheck("UTR_NUMBER", ref, vf -> vf.valueText() != null && vf.valueText().contains(ref)));

            long amountPaise = docNode.path("amount_paise").asLong();
            list.add(new FieldCheck("PAYMENT_AMOUNT", amountPaise + " paise", vf -> vf.valuePaise().isValue() && vf.valuePaise().getOrThrow().value() == amountPaise));
        } else if (docType == DocType.DELIVERY) {
            String ref = docNode.path("doc_reference").asText();
            list.add(new FieldCheck("DELIVERY_CHALLAN", ref, vf -> vf.valueText() != null && vf.valueText().contains(ref)));
        }

        return list;
    }

    private static String resolveApiKey() {
        String key = System.getenv("NEBIUS_API_KEY");
        if (key != null && !key.isBlank()) return key;
        key = System.getenv("NVIDIA_API_KEY");
        if (key != null && !key.isBlank()) return key;

        for (Path envPath : List.of(Paths.get(".env"), Paths.get("..", ".env"), Paths.get("../..", ".env"))) {
            if (Files.exists(envPath)) {
                try {
                    List<String> lines = Files.readAllLines(envPath);
                    for (String line : lines) {
                        String trimmed = line.trim();
                        if (trimmed.startsWith("NEBIUS_API_KEY=")) {
                            return trimmed.substring("NEBIUS_API_KEY=".length()).trim();
                        }
                        if (trimmed.startsWith("NVIDIA_API_KEY=")) {
                            return trimmed.substring("NVIDIA_API_KEY=".length()).trim();
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private static LlmProperties createProperties(String apiKey) {
        var nano = new LlmProperties.ModelConfig("nvidia/nemotron-nano-3-30b-a3b", 32768, 60, 240, 512, 2048);
        var superM = new LlmProperties.ModelConfig("nvidia/nemotron-3-super-120b-a12b", 1048576, 300, 900, 1024, 4096);
        var ultra = new LlmProperties.ModelConfig("nvidia/nemotron-3-ultra-550b-a55b", 1048576, 1000, 3000, 2048, 8192);

        String baseUrl = (apiKey != null && apiKey.startsWith("nvapi-"))
            ? "https://integrate.api.nvidia.com/v1"
            : "https://api.tokenfactory.nebius.com/v1";

        LlmProperties props = new LlmProperties();
        props.setApiKey(apiKey != null ? apiKey : "mock-key");
        props.setBaseUrl(baseUrl);
        props.getModels().put("extract", nano);
        props.getModels().put("reason", superM);
        props.getModels().put("adjudicate", ultra);
        props.setDailyCostCapMicros(50000000L);
        props.setPerCaseCostCapMicros(2000000L);
        props.setMaxInflight(2);
        return props;
    }

    private static LlmClient createMockLlmClient(JsonNode groundTruth) {
        return request -> {
            String userMsg = request.messages().stream()
                .filter(m -> "user".equals(m.role()))
                .map(LlmClient.Message::content)
                .findFirst()
                .orElse("");

            String pageText = userMsg;
            int docStart = userMsg.indexOf("DOCUMENT CONTENT:");
            if (docStart >= 0) {
                pageText = userMsg.substring(docStart + "DOCUMENT CONTENT:".length());
            }
            int endDoc = pageText.indexOf("JSON Output:");
            if (endDoc > 0) {
                pageText = pageText.substring(0, endDoc);
            }

            String docRef = "";
            for (JsonNode doc : groundTruth.path("documents")) {
                String ref = doc.path("doc_reference").asText();
                if (pageText.contains(ref)) {
                    docRef = ref;
                    break;
                }
            }

            List<String> fieldJsons = new ArrayList<>();

            if (!docRef.isEmpty()) {
                int start = pageText.indexOf(docRef);
                int end = start + docRef.length();
                String fieldName = userMsg.contains("PO") ? "PO_NUMBER" : (userMsg.contains("PAYMENT") ? "UTR_NUMBER" : (userMsg.contains("DELIVERY") ? "DELIVERY_CHALLAN" : "INVOICE_NUMBER"));
                fieldJsons.add(String.format("{\"field_name\":\"%s\",\"value\":\"%s\",\"page\":1,\"char_start\":%d,\"char_end\":%d,\"quote\":\"%s\",\"confidence\":\"HIGH\"}",
                    fieldName, docRef, start, end, docRef));
            }

            int dateIdx = pageText.indexOf("2024-");
            if (dateIdx < 0) dateIdx = pageText.indexOf("/2024");
            if (dateIdx >= 0) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d{4}-\\d{2}-\\d{2}|\\d{1,2}/\\d{1,2}/\\d{4}").matcher(pageText);
                if (m.find()) {
                    fieldJsons.add(String.format("{\"field_name\":\"INVOICE_DATE\",\"value\":\"%s\",\"page\":1,\"char_start\":%d,\"char_end\":%d,\"quote\":\"%s\",\"confidence\":\"HIGH\"}",
                        m.group(), m.start(), m.end(), m.group()));
                }
            }

            java.util.regex.Matcher amtMatcher = java.util.regex.Pattern.compile("(?i)(?:₹|INR|Rs\\.?)\\s*[0-9,]+(?:\\.[0-9]{2})?").matcher(pageText);
            if (amtMatcher.find()) {
                String amtStr = amtMatcher.group();
                int start = amtMatcher.start();
                int end = amtMatcher.end();
                String cleanNum = amtStr.replaceAll("[^0-9.]", "");
                if (!cleanNum.isEmpty()) {
                    fieldJsons.add(String.format("{\"field_name\":\"TOTAL_AMOUNT\",\"value\":\"%s\",\"page\":1,\"char_start\":%d,\"char_end\":%d,\"quote\":\"%s\",\"confidence\":\"HIGH\"}",
                        cleanNum, start, end, amtStr));
                }
            }

            String json = "{\"fields\":[" + String.join(",", fieldJsons) + "]}";

            return new LlmClient.LlmResponse(
                request.model(),
                json,
                new LlmClient.Usage(100, 50, 0, 0L),
                150,
                "mock-sha256",
                LlmClient.LlmOutcome.OK
            );
        };
    }

    private static class NoopLlmCallRepository extends LlmCallRepository {
        public NoopLlmCallRepository() {
            super(null);
        }

        @Override
        public long countSuccessfulCallsByRunIdAndModel(UUID runId, String model) {
            return 0L;
        }

        @Override
        public long findTotalCostToday() {
            return 0L;
        }

        @Override
        public long findTotalCostByRunId(UUID runId) {
            return 0L;
        }
    }
}
