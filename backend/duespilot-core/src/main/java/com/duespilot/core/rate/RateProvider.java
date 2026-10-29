package com.duespilot.core.rate;

import java.time.LocalDate;
import java.util.Optional;
import org.jspecify.annotations.NonNull;

/**
 * Contract for fetching the current Reserve Bank of India (RBI) Bank Rate.
 *
 * Per architecture.md section 10.7:
 * - Fetches rate candidate from authoritative source (e.g. via Tavily limited to rbi.org.in).
 * - Must return source URL, effective date, and confirmation state.
 * - Human must confirm candidate before it is marked confirmed (Rule R-19).
 */
public interface RateProvider {

    /**
     * Retrieves the latest Bank Rate candidate.
     *
     * @return a {@link RateCandidate} with rate in basis points, effective date, source URL, and confirmation flag.
     */
    @NonNull RateCandidate getLatestBankRate();

    /**
     * Retrieves the Bank Rate effective on a specific historical date if available.
     *
     * @param date target historical date
     * @return an Optional containing the candidate rate if recorded.
     */
    @NonNull Optional<RateCandidate> getBankRateOn(@NonNull LocalDate date);
}
