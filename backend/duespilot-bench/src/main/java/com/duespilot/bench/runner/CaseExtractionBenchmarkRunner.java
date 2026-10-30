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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * directly from the database and print field-level accuracy against ground truth.
 *
 * Files live in the database only — there is no filesystem fallback.
 * Implements architecture.md section 14 and rules R-26, R-27, R-32, R-61.
 *
 * Usage:
 *   extract --case GEN-001 [--mock]
 */
public final class CaseExtractionBenchmarkRunner {

    private static final Logger log = LoggerFactory.getLogger(CaseExtractionBenchmarkRunner.class);
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    public static void main(String[] args) {
        String caseCode = "GEN-001";
        boolean mock = false;

        int i = 0;
        if (args.length > 0 && "extract".equalsIgnoreCase(args[0])) {
            i = 1;
        }

        while (i < args.length) {
            String arg = args[i];
            if ("--case".equalsIgnoreCase(arg) && i + 1 < args.length) {
                caseCode = args[++i];
            } else if ("--mock".equalsIgnoreCase(arg)) {
                mock = true;
            }
            i++;
        }

        // Load from database — files live in DB only (no filesystem fallback)
        try (BenchmarkDatabaseRepository dbRepo = BenchmarkDatabaseRepository.createStandalone()) {
            String gtJson = null;
            String dataset = caseCode.startsWith("HOLD-") ? "holdout" : "generated";
            var gtOpt = dbRepo.findCaseGroundTruth(dataset, caseCode);
            if (gtOpt.isPresent()) {
                gtJson = gtOpt.get();
            } else {
                String otherDataset = dataset.equals("holdout") ? "generated" : "holdout";
                gtOpt = dbRepo.findCaseGroundTruth(otherDataset, caseCode);
                if (gtOpt.isPresent()) {
                    gtJson = gtOpt.get();
                    dataset = otherDataset;
                }
            }

            if (gtJson == null) {
                log.error("Case {} not found in database (tried both 'generated' and 'holdout' datasets). " +
                          "Ensure the case was seeded via the benchmark pipeline.", caseCode);
                return;
            }

            log.info("Loaded case {} from database (dataset: {})", caseCode, dataset);
            JsonNode groundTruth = MAPPER.readTree(gtJson);

            List<BenchmarkDatabaseRepository.BenchmarkFileRecord> fileRecs = dbRepo.findCaseFiles(dataset, caseCode);
            Map<String, byte[]> fileMap = new HashMap<>();
            for (var fr : fileRecs) {
                dbRepo.findBlobBytes(fr.blobSha256()).ifPresent(bytes -> fileMap.put(fr.fileName(), bytes));
            }

            runExtractionAndScoreInMemory(caseCode, groundTruth, fileMap, mock);
        } catch (Exception e) {
            log.error("Benchmark extraction failed for case {}: {}", caseCode, e.getMessage(), e);
        }
    }

    public static void runExtractionAndScoreInMemory(
            String caseCode,
            JsonNode groundTruth,
            Map<String, byte[]> fileMap,
            boolean mock) throws Exception {

        log.info("================================================================");
        log.info("DuesPilot Live Benchmark Extraction Evaluation: {}", caseCode);
        log.info("================================================================");

        String apiKey = resolveApiKey();
        if ((apiKey == null || apiKey.isBlank()) && !mock) {
            log.info("[NOTE] Live extraction requires NEBIUS_API_KEY in environment or .env. Running in mock mode.");
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
            log.error("No documents listed in ground truth for case {}", caseCode);
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
                log.warn("Skipping missing file in blob storage: {}", fileName);
                continue;
            }

            Map<Integer, String> pages = extractPages(fileBytes);
            log.info("Document: {} ({}, {} pages)", fileName, docType.name(), pages.size());

            var result = router.extractDocument(docType, fileName, pages, null, UUID.randomUUID());
            log.info("Model used: {} | Escalations: {}", result.finalModel(), result.escalationHistory());

            Map<String, VerifiedField> extractedMap = new LinkedHashMap<>();
            for (VerifiedField vf : result.verifiedFields()) {
                extractedMap.put(vf.fieldName().toUpperCase(Locale.ROOT), vf);
            }

            List<FieldCheck> checks = buildFieldChecks(docType, docNode, groundTruth);

            log.info("------------------------------------------------------------------------------------------------");
            log.info(String.format("%-20s | %-25s | %-25s | %-12s | %-8s", "FIELD", "EXPECTED", "EXTRACTED", "STATUS", "MATCH"));
            log.info("------------------------------------------------------------------------------------------------");

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

                log.info(String.format("%-20s | %-25s | %-25s | %-12s | %-8s",
                    check.fieldName(),
                    truncate(check.expectedValue(), 25),
                    truncate(extractedVal, 25),
                    status,
                    isMatch ? "PASS" : "FAIL"
                ));
            }
            log.info("Document Score: {} / {} fields matched", docMatched, checks.size());
        }

        double accuracy = totalEvaluated > 0 ? (totalMatched * 100.0 / totalEvaluated) : 0.0;
        log.info("OVERALL EXTRACTION ACCURACY for {}: {} / {} ({} %)", caseCode, totalMatched, totalEvaluated,
                 String.format("%.1f", accuracy));
        log.info("================================================================");
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
        // Only NEBIUS_API_KEY is supported — NVIDIA_API_KEY alias removed
        String key = System.getenv("NEBIUS_API_KEY");
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
                    }
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private static LlmProperties createProperties(String apiKey) {
        // Model IDs match Token Factory (api.tokenfactory.nebius.com/v1)
        var nano = new LlmProperties.ModelConfig("nvidia/nemotron-nano-3-30b-a3b", 32768, 200000, 400000, 512, 2048);
        var superM = new LlmProperties.ModelConfig("nvidia/nemotron-3-super-120b-a12b", 131072, 800000, 2400000, 1024, 4096);
        var ultra = new LlmProperties.ModelConfig("nvidia/nemotron-3-ultra-550b-a55b", 131072, 3000000, 9000000, 2048, 8192);

        LlmProperties props = new LlmProperties();
        props.setApiKey(apiKey != null ? apiKey : "mock-key");
        props.setBaseUrl("https://api.tokenfactory.nebius.com/v1");
        props.getModels().put("extract", nano);
        props.getModels().put("reason", superM);
        props.getModels().put("adjudicate", ultra);
        props.setDailyCostCapMicros(5000000L);
        props.setPerCaseCostCapMicros(250000L);
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
