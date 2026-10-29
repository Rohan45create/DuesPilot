package com.duespilot.core.statutory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Encapsulates statutory parameters governing MSME delayed-payment adjudication.
 *
 * Per architecture.md section 10.5 and Rule R-10:
 * - All statutory parameters live in configuration (statutory.yml).
 * - Each parameter tracks source, last_checked, and legal_review status.
 * - All legal_review statuses default to "PENDING".
 * - Amendment Bill provisions have in_force = false.
 */
public record StatutoryParameters(
    int agreedCapDays,
    int defaultPeriodDays,
    int interestMultiplier,
    @NonNull String compoundingRest,
    boolean amendmentBillInForce,
    @NonNull String legalReviewStatus,
    @NonNull String agreedCapSource,
    @NonNull String defaultPeriodSource,
    @NonNull String interestMultiplierSource,
    @NonNull String amendmentBillSource,
    @NonNull String lastChecked
) {
    public StatutoryParameters {
        Objects.requireNonNull(compoundingRest, "compoundingRest cannot be null");
        Objects.requireNonNull(legalReviewStatus, "legalReviewStatus cannot be null");
        Objects.requireNonNull(agreedCapSource, "agreedCapSource cannot be null");
        Objects.requireNonNull(defaultPeriodSource, "defaultPeriodSource cannot be null");
        Objects.requireNonNull(interestMultiplierSource, "interestMultiplierSource cannot be null");
        Objects.requireNonNull(amendmentBillSource, "amendmentBillSource cannot be null");
        Objects.requireNonNull(lastChecked, "lastChecked cannot be null");
    }

    /**
     * Default statutory parameters reflecting current MSMED Act 2006 law.
     */
    public static StatutoryParameters defaultParameters() {
        return new StatutoryParameters(
            45,
            15,
            3,
            "MONTHLY",
            false,
            "PENDING",
            "Micro, Small and Medium Enterprises Development Act, 2006 (Section 15)",
            "Micro, Small and Medium Enterprises Development Act, 2006 (Section 15)",
            "Micro, Small and Medium Enterprises Development Act, 2006 (Section 16)",
            "Parliamentary Gazette / PRS Legislative Research",
            "2026-10-23"
        );
    }

    /**
     * Loads statutory parameters from the classpath resource statutory.yml,
     * falling back to default parameters if resource cannot be read.
     */
    public static StatutoryParameters loadFromClasspath() {
        try (InputStream is = StatutoryParameters.class.getClassLoader().getResourceAsStream("statutory.yml")) {
            if (is == null) {
                return defaultParameters();
            }
            return parseYaml(new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            return defaultParameters();
        }
    }

    /**
     * Minimal pure-Java parser for statutory.yml avoiding external YAML dependencies.
     */
    public static StatutoryParameters parseYaml(BufferedReader reader) {
        int capDays = 45;
        int defaultDays = 15;
        int multiplier = 3;
        String rest = "MONTHLY";
        boolean amendmentInForce = false;
        String legalReview = "PENDING";
        String capSource = "Section 15 MSMED Act 2006";
        String defSource = "Section 15 MSMED Act 2006";
        String intSource = "Section 16 MSMED Act 2006";
        String amendSource = "MSME Amendment Bill 2026";
        String lastChecked = "2026-10-23";

        String currentSection = "";
        String currentKey = "";

        try {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }

                if (trimmed.endsWith(":") && !trimmed.contains(" ")) {
                    currentSection = trimmed.substring(0, trimmed.length() - 1);
                    continue;
                }

                if (trimmed.startsWith("agreed_cap_days:")) {
                    currentKey = "agreed_cap_days";
                } else if (trimmed.startsWith("default_period_days:")) {
                    currentKey = "default_period_days";
                } else if (trimmed.startsWith("rate_multiplier:")) {
                    currentKey = "rate_multiplier";
                } else if (trimmed.startsWith("compounding_rest:")) {
                    currentKey = "compounding_rest";
                } else if (trimmed.startsWith("amendment_bill_2026:")) {
                    currentKey = "amendment_bill_2026";
                }

                if (trimmed.startsWith("value:")) {
                    String val = extractYamlValue(trimmed);
                    if ("agreed_cap_days".equals(currentKey)) {
                        capDays = Integer.parseInt(val);
                    } else if ("default_period_days".equals(currentKey)) {
                        defaultDays = Integer.parseInt(val);
                    } else if ("rate_multiplier".equals(currentKey)) {
                        multiplier = Integer.parseInt(val);
                    } else if ("compounding_rest".equals(currentKey)) {
                        rest = val.replace("\"", "");
                    }
                } else if (trimmed.startsWith("in_force:")) {
                    String val = extractYamlValue(trimmed);
                    if ("amendment_bill_2026".equals(currentKey)) {
                        amendmentInForce = Boolean.parseBoolean(val);
                    }
                } else if (trimmed.startsWith("legal_review:")) {
                    legalReview = extractYamlValue(trimmed).replace("\"", "");
                } else if (trimmed.startsWith("last_checked:")) {
                    lastChecked = extractYamlValue(trimmed).replace("\"", "");
                } else if (trimmed.startsWith("source:")) {
                    String src = extractYamlValue(trimmed).replace("\"", "");
                    if ("agreed_cap_days".equals(currentKey)) {
                        capSource = src;
                    } else if ("default_period_days".equals(currentKey)) {
                        defSource = src;
                    } else if ("rate_multiplier".equals(currentKey)) {
                        intSource = src;
                    } else if ("amendment_bill_2026".equals(currentKey)) {
                        amendSource = src;
                    }
                }
            }
        } catch (Exception ex) {
            return defaultParameters();
        }

        return new StatutoryParameters(
            capDays,
            defaultDays,
            multiplier,
            rest,
            amendmentInForce,
            legalReview,
            capSource,
            defSource,
            intSource,
            amendSource,
            lastChecked
        );
    }

    private static String extractYamlValue(String line) {
        int idx = line.indexOf(':');
        if (idx >= 0 && idx < line.length() - 1) {
            return line.substring(idx + 1).trim();
        }
        return "";
    }
}
