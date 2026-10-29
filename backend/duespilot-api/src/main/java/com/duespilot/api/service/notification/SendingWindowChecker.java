package com.duespilot.api.service.notification;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Enforces Rule R-75: Outbound message sending window 09:00–19:00 IST.
 * Messages prepared outside this window are not dispatched immediately;
 * they are set to status QUEUED with scheduled_for pointing to the next opening.
 */
@Component
public class SendingWindowChecker {

    public static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");
    public static final LocalTime WINDOW_START = LocalTime.of(9, 0, 0);
    public static final LocalTime WINDOW_END = LocalTime.of(19, 0, 0);

    public record WindowResult(boolean insideWindow, @Nullable Instant nextWindowStart) {}

    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public SendingWindowChecker() {
        this(Clock.system(IST_ZONE));
    }

    public SendingWindowChecker(Clock clock) {
        this.clock = clock;
    }

    public WindowResult checkCurrentWindow() {
        return checkWindow(Instant.now(clock));
    }

    public WindowResult checkWindow(Instant instant) {
        ZonedDateTime istDateTime = instant.atZone(IST_ZONE);
        LocalTime time = istDateTime.toLocalTime();
        LocalDate date = istDateTime.toLocalDate();

        if (time.isBefore(WINDOW_START)) {
            // Before 09:00 IST today -> opens today at 09:00 IST
            ZonedDateTime nextOpening = ZonedDateTime.of(date, WINDOW_START, IST_ZONE);
            return new WindowResult(false, nextOpening.toInstant());
        } else if (!time.isBefore(WINDOW_END)) {
            // At or after 19:00 IST -> opens tomorrow at 09:00 IST
            ZonedDateTime nextOpening = ZonedDateTime.of(date.plusDays(1), WINDOW_START, IST_ZONE);
            return new WindowResult(false, nextOpening.toInstant());
        }

        // Inside 09:00 to 19:00 IST
        return new WindowResult(true, null);
    }
}
