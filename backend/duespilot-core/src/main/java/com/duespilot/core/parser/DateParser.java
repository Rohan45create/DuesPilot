package com.duespilot.core.parser;

import com.duespilot.core.model.Known;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Parser for dates in Indian commercial and communication contexts.
 *
 * Per architecture.md section 10.2 and Rule R-16:
 * - Dates parse to ISO YYYY-MM-DD.
 * - Relative dates in messages ("next Friday", "in 5 days") resolve only if the message date
 *   is known; otherwise returns Known.Unknown("UNRESOLVED: ...").
 * - Supports ISO formats, common Indian formats (DD/MM/YYYY, DD-MM-YYYY), textual months,
 *   Devanagari numerals, and relative date offsets.
 */
public final class DateParser {

    private static final List<DateTimeFormatter> EXPLICIT_FORMATTERS = List.of(
        DateTimeFormatter.ofPattern("uuuu-MM-dd", Locale.ENGLISH).withResolverStyle(java.time.format.ResolverStyle.STRICT),
        DateTimeFormatter.ofPattern("uuuu/MM/dd", Locale.ENGLISH).withResolverStyle(java.time.format.ResolverStyle.STRICT),
        DateTimeFormatter.ofPattern("uuuu.MM.dd", Locale.ENGLISH).withResolverStyle(java.time.format.ResolverStyle.STRICT),
        DateTimeFormatter.ofPattern("d/M/uuuu", Locale.ENGLISH).withResolverStyle(java.time.format.ResolverStyle.STRICT),
        DateTimeFormatter.ofPattern("d-M-uuuu", Locale.ENGLISH).withResolverStyle(java.time.format.ResolverStyle.STRICT),
        DateTimeFormatter.ofPattern("d.M.uuuu", Locale.ENGLISH).withResolverStyle(java.time.format.ResolverStyle.STRICT),
        new java.time.format.DateTimeFormatterBuilder()
            .appendPattern("d/M/")
            .appendValueReduced(java.time.temporal.ChronoField.YEAR, 2, 2, 2000)
            .toFormatter(Locale.ENGLISH)
            .withResolverStyle(java.time.format.ResolverStyle.STRICT),
        new java.time.format.DateTimeFormatterBuilder()
            .appendPattern("d-M-")
            .appendValueReduced(java.time.temporal.ChronoField.YEAR, 2, 2, 2000)
            .toFormatter(Locale.ENGLISH)
            .withResolverStyle(java.time.format.ResolverStyle.STRICT),
        DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.ENGLISH).withResolverStyle(java.time.format.ResolverStyle.STRICT),
        DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH).withResolverStyle(java.time.format.ResolverStyle.STRICT),
        DateTimeFormatter.ofPattern("d-MMM-uuuu", Locale.ENGLISH).withResolverStyle(java.time.format.ResolverStyle.STRICT),
        DateTimeFormatter.ofPattern("MMMM d, uuuu", Locale.ENGLISH).withResolverStyle(java.time.format.ResolverStyle.STRICT),
        DateTimeFormatter.ofPattern("MMM d, uuuu", Locale.ENGLISH).withResolverStyle(java.time.format.ResolverStyle.STRICT)
    );

    // Matches explicit date tokens in freeform text: e.g. 12/03/2024, 2024-03-12, 12 March 2024
    private static final Pattern EXPLICIT_DATE_PATTERN = Pattern.compile(
        "\\b(\\d{4}[-/.]\\d{1,2}[-/.]\\d{1,2}|\\d{1,2}[-/.]\\d{1,2}[-/.]\\d{2,4}|\\d{1,2}(?:st|nd|rd|th)?\\s+(?:Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:tember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)[- ]+\\d{2,4}|(?:Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:tember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)\\s+\\d{1,2}(?:st|nd|rd|th)?,?\\s+\\d{4})\\b",
        Pattern.CASE_INSENSITIVE
    );

    private DateParser() {
    }

    public static Known<LocalDate> parse(@Nullable String text) {
        return parse(text, (LocalDate) null);
    }

    public static Known<LocalDate> parse(@Nullable String text, @NonNull Known<LocalDate> referenceDate) {
        Objects.requireNonNull(referenceDate, "referenceDate cannot be null");
        return parse(text, referenceDate.orNull());
    }

    /**
     * Parses a date string or extracts a date from a quote.
     *
     * @param text input date string or text
     * @param referenceDate reference date (e.g. message timestamp) required for relative dates
     * @return Known with ISO LocalDate, or Known.Unknown if missing, unresolvable or ambiguous.
     */
    public static Known<LocalDate> parse(@Nullable String text, @Nullable LocalDate referenceDate) {
        if (text == null || text.isBlank()) {
            return Known.unknown("Date text is empty or null");
        }

        String normalized = AmountParser.normalizeDevanagariDigits(text.trim());

        // Check for relative date expressions first
        RelativeCheck relativeCheck = checkRelativeDate(normalized);
        if (relativeCheck.isRelative()) {
            if (referenceDate == null) {
                return Known.unknown("UNRESOLVED: Relative date '" + text.trim() + "' requires a known reference date");
            }
            return relativeCheck.resolve(referenceDate);
        }

        // Clean ordinals (12th -> 12, 1st -> 1)
        String cleaned = stripOrdinals(normalized);

        // Try direct parse of entire string
        Known<LocalDate> direct = tryParseExplicit(cleaned);
        if (direct.isValue()) {
            return direct;
        }

        // Scan for explicit date patterns in text (e.g. in a quote)
        List<LocalDate> found = extractExplicitDates(cleaned);
        if (found.isEmpty()) {
            return direct.isUnknown() ? direct : Known.unknown("Cannot parse date: \"" + text + "\"");
        }

        Set<LocalDate> uniqueDates = new LinkedHashSet<>(found);
        if (uniqueDates.size() == 1) {
            return Known.value(uniqueDates.iterator().next());
        }

        return Known.unknown("Ambiguous date: multiple conflicting dates found in text: " + uniqueDates);
    }

    private static Known<LocalDate> tryParseExplicit(String text) {
        String s = text.trim();
        for (DateTimeFormatter formatter : EXPLICIT_FORMATTERS) {
            try {
                LocalDate date = LocalDate.parse(s, formatter);
                return Known.value(date);
            } catch (DateTimeParseException ignored) {
                // Try next formatter
            }
        }
        return Known.unknown("Unrecognized date format: \"" + text + "\"");
    }

    private static List<LocalDate> extractExplicitDates(String text) {
        List<LocalDate> results = new ArrayList<>();
        Matcher matcher = EXPLICIT_DATE_PATTERN.matcher(text);
        while (matcher.find()) {
            String token = matcher.group(1);
            String cleanedToken = stripOrdinals(token);
            Known<LocalDate> parsed = tryParseExplicit(cleanedToken);
            if (parsed.isValue()) {
                results.add(parsed.getOrThrow());
            }
        }
        return results;
    }

    private static String stripOrdinals(String text) {
        // Replace 1st, 2nd, 3rd, 4th with 1, 2, 3, 4
        return text.replaceAll("(?<=\\b\\d{1,2})(st|nd|rd|th)\\b", "");
    }

    private static RelativeCheck checkRelativeDate(String raw) {
        String s = raw.toLowerCase(Locale.ROOT).trim();

        if (s.equals("today")) {
            return new RelativeCheck(true, ref -> Known.value(ref));
        }
        if (s.equals("yesterday")) {
            return new RelativeCheck(true, ref -> Known.value(ref.minusDays(1)));
        }
        if (s.equals("tomorrow")) {
            return new RelativeCheck(true, ref -> Known.value(ref.plusDays(1)));
        }
        if (s.equals("day after tomorrow")) {
            return new RelativeCheck(true, ref -> Known.value(ref.plusDays(2)));
        }
        if (s.equals("end of month") || s.equals("month end") || s.equals("end of the month")) {
            return new RelativeCheck(true, ref -> Known.value(ref.with(TemporalAdjusters.lastDayOfMonth())));
        }
        if (s.equals("end of next month")) {
            return new RelativeCheck(true, ref -> Known.value(ref.plusMonths(1).with(TemporalAdjusters.lastDayOfMonth())));
        }

        // Day of week: "next friday", "this friday", "coming friday"
        Matcher nextDowMatcher = Pattern.compile("^(?:next|coming|this)\\s+(monday|tuesday|wednesday|thursday|friday|saturday|sunday)$").matcher(s);
        if (nextDowMatcher.matches()) {
            DayOfWeek targetDow = parseDayOfWeek(nextDowMatcher.group(1));
            boolean isNext = s.startsWith("next");
            return new RelativeCheck(true, ref -> {
                LocalDate result = isNext
                    ? ref.with(TemporalAdjusters.next(targetDow))
                    : ref.with(TemporalAdjusters.nextOrSame(targetDow));
                return Known.value(result);
            });
        }

        // Offsets: "in X days", "after X days", "within X days"
        Matcher inDaysMatcher = Pattern.compile("^(?:in|after|within)\\s+(\\d+)\\s+days?$").matcher(s);
        if (inDaysMatcher.matches()) {
            int days = Integer.parseInt(inDaysMatcher.group(1));
            return new RelativeCheck(true, ref -> Known.value(ref.plusDays(days)));
        }

        // Offsets ago: "X days ago", "X days back"
        Matcher daysAgoMatcher = Pattern.compile("^(\\d+)\\s+days?\\s+(?:ago|back|earlier)$").matcher(s);
        if (daysAgoMatcher.matches()) {
            int days = Integer.parseInt(daysAgoMatcher.group(1));
            return new RelativeCheck(true, ref -> Known.value(ref.minusDays(days)));
        }

        // Weeks offset: "in X weeks", "after X weeks"
        Matcher inWeeksMatcher = Pattern.compile("^(?:in|after)\\s+(\\d+)\\s+weeks?$").matcher(s);
        if (inWeeksMatcher.matches()) {
            int weeks = Integer.parseInt(inWeeksMatcher.group(1));
            return new RelativeCheck(true, ref -> Known.value(ref.plusWeeks(weeks)));
        }

        // Months offset: "in X months", "after X months"
        Matcher inMonthsMatcher = Pattern.compile("^(?:in|after)\\s+(\\d+)\\s+months?$").matcher(s);
        if (inMonthsMatcher.matches()) {
            int months = Integer.parseInt(inMonthsMatcher.group(1));
            return new RelativeCheck(true, ref -> Known.value(ref.plusMonths(months)));
        }

        return new RelativeCheck(false, null);
    }

    private static DayOfWeek parseDayOfWeek(String s) {
        return switch (s.toLowerCase(Locale.ROOT)) {
            case "monday" -> DayOfWeek.MONDAY;
            case "tuesday" -> DayOfWeek.TUESDAY;
            case "wednesday" -> DayOfWeek.WEDNESDAY;
            case "thursday" -> DayOfWeek.THURSDAY;
            case "friday" -> DayOfWeek.FRIDAY;
            case "saturday" -> DayOfWeek.SATURDAY;
            case "sunday" -> DayOfWeek.SUNDAY;
            default -> throw new IllegalArgumentException("Unknown day of week: " + s);
        };
    }

    private record RelativeCheck(boolean isRelative, java.util.function.Function<LocalDate, Known<LocalDate>> resolver) {
        public Known<LocalDate> resolve(LocalDate ref) {
            return resolver.apply(ref);
        }
    }
}
