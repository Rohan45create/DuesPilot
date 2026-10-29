package com.duespilot.core.interest;

import com.duespilot.core.model.Paise;
import com.duespilot.core.rate.RateCandidate;
import com.duespilot.core.statutory.StatutoryParameters;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Interest Engine Tests: Convention C-1 (Section 10.6 & Rules R-1, R-19, R-20, R-60)")
class InterestEngineTest {

    private final StatutoryParameters statutory = StatutoryParameters.loadFromClasspath();

    /*
     * =========================================================================
     * HAND-COMPUTED CASE 1: 1 Full Month
     * =========================================================================
     * Principal: ₹1,00,000.00 = 10,000,000 paise
     * Dates: 2024-03-01 to 2024-04-01 (1 full month)
     * Bank Rate: 650 bps (6.50%), Multiplier: 3 -> Effective Annual Rate = 19.50%
     * Monthly Rate: 0.1950 / 12 = 0.01625
     * Month 1 Interest: 10,000,000 * 0.01625 = 162,500 paise (₹1,625.00)
     * Rounding HALF_UP: 162,500.0 -> 162,500 paise
     * Total Interest: 162,500 paise
     * Closing Balance: 10,000,000 + 162,500 = 10,162,500 paise (₹1,01,625.00)
     */
    @Test
    @DisplayName("Hand-computed Case 1: 1 full month compounding")
    void case1OneFullMonth() {
        LocalDate from = LocalDate.of(2024, 3, 1);
        LocalDate to = LocalDate.of(2024, 4, 1);
        Paise principal = Paise.ofRupees(100000); // 10,000,000 paise

        InterestInput input = InterestInput.of(principal, from, to, 650, statutory.interestMultiplier(), true);
        InterestResult result = InterestEngine.compute(input);

        // Assertions matching hand calculation
        assertThat(result.interest()).isEqualTo(Paise.ofPaise(162500L));
        assertThat(result.closingBalance()).isEqualTo(Paise.ofPaise(10162500L));
        assertThat(result.rests()).hasSize(1);
        assertThat(result.rests().get(0).interestAccrued()).isEqualTo(Paise.ofPaise(162500L));
        assertThat(result.rests().get(0).isPartialMonth()).isFalse();

        // Label assertions (R-7, R-20)
        assertThat(result.statusLabel()).isEqualTo("PROVISIONAL");
        assertThat(result.convention()).isEqualTo("C-1");
        assertThat(result.effectiveAnnualRateBps()).isEqualTo(1950);
    }

    /*
     * =========================================================================
     * HAND-COMPUTED CASE 2: 2 Full Months with Compounding & HALF_UP Rounding
     * =========================================================================
     * Principal: ₹50,000.00 = 5,000,000 paise
     * Dates: 2024-01-01 to 2024-03-01 (2 full months)
     * Bank Rate: 675 bps (6.75%), Multiplier: 3 -> Effective Annual Rate = 20.25%
     * Monthly Rate: 0.2025 / 12 = 0.016875
     *
     * Month 1 (Jan 1 to Feb 1):
     *   Opening Balance: 5,000,000 paise
     *   Interest: 5,000,000 * 0.016875 = 84,375.0 paise -> 84,375 paise (₹843.75)
     *   Closing Balance: 5,000,000 + 84,375 = 5,084,375 paise
     *
     * Month 2 (Feb 1 to Mar 1):
     *   Opening Balance: 5,084,375 paise
     *   Interest: 5,084,375 * 0.016875 = 85,798.828125 paise
     *   HALF_UP Rounding: 85,798.828125 -> 85,799 paise (₹857.99)
     *   Closing Balance: 5,084,375 + 85,799 = 5,170,174 paise (₹51,701.74)
     *
     * Total Interest: 84,375 + 85,799 = 170,174 paise (₹1,701.74)
     */
    @Test
    @DisplayName("Hand-computed Case 2: 2 full months with compounding and HALF_UP rounding")
    void case2TwoFullMonthsWithCompounding() {
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 3, 1);
        Paise principal = Paise.ofRupees(50000); // 5,000,000 paise

        InterestInput input = InterestInput.of(principal, from, to, 675, statutory.interestMultiplier(), true);
        InterestResult result = InterestEngine.compute(input);

        assertThat(result.interest()).isEqualTo(Paise.ofPaise(170174L));
        assertThat(result.closingBalance()).isEqualTo(Paise.ofPaise(5170174L));
        assertThat(result.rests()).hasSize(2);
        assertThat(result.rests().get(0).interestAccrued()).isEqualTo(Paise.ofPaise(84375L));
        assertThat(result.rests().get(1).interestAccrued()).isEqualTo(Paise.ofPaise(85799L));
        assertThat(result.statusLabel()).isEqualTo("PROVISIONAL");
    }

    /*
     * =========================================================================
     * HAND-COMPUTED CASE 3: 3 Full Months
     * =========================================================================
     * Principal: ₹2,00,000.00 = 20,000,000 paise
     * Dates: 2024-01-01 to 2024-04-01 (3 full months)
     * Bank Rate: 650 bps (6.50%), Multiplier: 3 -> Monthly Rate = 0.01625
     *
     * Month 1: 20,000,000 * 0.01625 = 325,000 paise -> Balance = 20,325,000 paise
     * Month 2: 20,325,000 * 0.01625 = 330,281.25 -> HALF_UP = 330,281 paise -> Balance = 20,655,281 paise
     * Month 3: 20,655,281 * 0.01625 = 335,648.31625 -> HALF_UP = 335,648 paise -> Balance = 20,990,929 paise
     *
     * Total Interest: 325,000 + 330,281 + 335,648 = 990,929 paise (₹9,909.29)
     * Closing Balance: 20,990,929 paise (₹2,09,909.29)
     */
    @Test
    @DisplayName("Hand-computed Case 3: 3 full months compounding")
    void case3ThreeFullMonths() {
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 4, 1);
        Paise principal = Paise.ofRupees(200000); // 20,000,000 paise

        InterestInput input = InterestInput.of(principal, from, to, 650, statutory.interestMultiplier(), true);
        InterestResult result = InterestEngine.compute(input);

        assertThat(result.interest()).isEqualTo(Paise.ofPaise(990929L));
        assertThat(result.closingBalance()).isEqualTo(Paise.ofPaise(20990929L));
        assertThat(result.rests()).hasSize(3);
        assertThat(result.rests().get(0).interestAccrued()).isEqualTo(Paise.ofPaise(325000L));
        assertThat(result.rests().get(1).interestAccrued()).isEqualTo(Paise.ofPaise(330281L));
        assertThat(result.rests().get(2).interestAccrued()).isEqualTo(Paise.ofPaise(335648L));
        assertThat(result.statusLabel()).isEqualTo("PROVISIONAL");
    }

    /*
     * =========================================================================
     * HAND-COMPUTED CASE 4: Pure Partial Month (10 days, 0 full months)
     * =========================================================================
     * Principal: ₹1,50,000.00 = 15,000,000 paise
     * Dates: 2024-03-01 to 2024-03-11 (10 days)
     * Bank Rate: 650 bps (6.50%), Multiplier: 3 -> Annual Rate = 0.1950
     * Days = 10
     * Interest: 15,000,000 * 0.1950 * 10 / 365 = 29,250,000 / 365 = 80,136.9863... paise
     * HALF_UP Rounding: 80,136.9863 -> 80,137 paise (₹801.37)
     * Closing Balance: 15,000,000 + 80,137 = 15,080,137 paise (₹1,50,801.37)
     */
    @Test
    @DisplayName("Hand-computed Case 4: pure partial month of 10 days")
    void case4PurePartialMonth() {
        LocalDate from = LocalDate.of(2024, 3, 1);
        LocalDate to = LocalDate.of(2024, 3, 11); // 10 days
        Paise principal = Paise.ofRupees(150000);

        InterestInput input = InterestInput.of(principal, from, to, 650, statutory.interestMultiplier(), true);
        InterestResult result = InterestEngine.compute(input);

        assertThat(result.interest()).isEqualTo(Paise.ofPaise(80137L));
        assertThat(result.closingBalance()).isEqualTo(Paise.ofPaise(15080137L));
        assertThat(result.rests()).hasSize(1);
        assertThat(result.rests().get(0).isPartialMonth()).isTrue();
        assertThat(result.rests().get(0).daysInPeriod()).isEqualTo(10);
    }

    /*
     * =========================================================================
     * HAND-COMPUTED CASE 5: 1 Full Month + 15 Days Partial Month
     * =========================================================================
     * Principal: ₹1,00,000.00 = 10,000,000 paise
     * Dates: 2024-01-01 to 2024-02-16 (1 full month [Jan 1 to Feb 1] + 15 days [Feb 1 to Feb 16])
     * Bank Rate: 650 bps (6.50%), Multiplier: 3 -> Effective Annual Rate = 19.50%
     *
     * Month 1 (Jan 1 to Feb 1):
     *   Opening Balance: 10,000,000 paise
     *   Interest: 10,000,000 * (0.195 / 12) = 162,500 paise
     *   Closing Balance: 10,162,500 paise
     *
     * Partial Month (Feb 1 to Feb 16, 15 days):
     *   Opening Balance: 10,162,500 paise
     *   Interest: 10,162,500 * 0.1950 * 15 / 365 = 29,725,312.5 / 365 = 81,439.2123... paise
     *   HALF_UP Rounding: 81,439.2123 -> 81,439 paise (₹814.39)
     *   Closing Balance: 10,162,500 + 81,439 = 10,243,939 paise (₹1,02,439.39)
     *
     * Total Interest: 162,500 + 81,439 = 243,939 paise (₹2,439.39)
     */
    @Test
    @DisplayName("Hand-computed Case 5: 1 full month plus 15 days partial month")
    void case5OneFullMonthPlusPartialMonth() {
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 2, 16);
        Paise principal = Paise.ofRupees(100000);

        InterestInput input = InterestInput.of(principal, from, to, 650, statutory.interestMultiplier(), true);
        InterestResult result = InterestEngine.compute(input);

        assertThat(result.interest()).isEqualTo(Paise.ofPaise(243939L));
        assertThat(result.closingBalance()).isEqualTo(Paise.ofPaise(10243939L));
        assertThat(result.rests()).hasSize(2);
        assertThat(result.rests().get(0).isPartialMonth()).isFalse();
        assertThat(result.rests().get(0).interestAccrued()).isEqualTo(Paise.ofPaise(162500L));
        assertThat(result.rests().get(1).isPartialMonth()).isTrue();
        assertThat(result.rests().get(1).daysInPeriod()).isEqualTo(15);
        assertThat(result.rests().get(1).interestAccrued()).isEqualTo(Paise.ofPaise(81439L));
    }

    /*
     * =========================================================================
     * HAND-COMPUTED CASE 6: Fractional Principal, Rate 5.25%, 2 Full Months + 7 Days
     * =========================================================================
     * Principal: ₹73,456.78 = 7,345,678 paise
     * Dates: 2024-05-10 to 2024-07-17 (2 full months [May 10 to Jul 10] + 7 days [Jul 10 to Jul 17])
     * Bank Rate: 525 bps (5.25%), Multiplier: 3 -> Annual Rate = 15.75% = 0.1575
     * Monthly Rate: 0.1575 / 12 = 0.013125
     *
     * Month 1 (May 10 to Jun 10):
     *   Opening Balance: 7,345,678 paise
     *   Interest: 7,345,678 * 0.013125 = 96,412.01925 -> HALF_UP = 96,412 paise
     *   Closing Balance: 7,345,678 + 96,412 = 7,442,090 paise
     *
     * Month 2 (Jun 10 to Jul 10):
     *   Opening Balance: 7,442,090 paise
     *   Interest: 7,442,090 * 0.013125 = 97,677.43125 -> HALF_UP = 97,677 paise
     *   Closing Balance: 7,442,090 + 97,677 = 7,539,767 paise
     *
     * Partial Month (Jul 10 to Jul 17, 7 days):
     *   Opening Balance: 7,539,767 paise
     *   Interest: 7,539,767 * 0.1575 * 7 / 365 = 8,312,593.1175 / 365 = 22,774.2277... paise
     *   HALF_UP Rounding: 22,774.2277 -> 22,774 paise
     *   Closing Balance: 7,539,767 + 22,774 = 7,562,541 paise (₹75,625.41)
     *
     * Total Interest: 96,412 + 97,677 + 22,774 = 216,863 paise (₹2,168.63)
     */
    @Test
    @DisplayName("Hand-computed Case 6: odd principal with 5.25% bank rate, 2 full months + 7 days")
    void case6OddPrincipalAndRateWithCompoundAndPartial() {
        LocalDate from = LocalDate.of(2024, 5, 10);
        LocalDate to = LocalDate.of(2024, 7, 17);
        Paise principal = Paise.ofPaise(7345678L); // ₹73,456.78

        InterestInput input = InterestInput.of(principal, from, to, 525, statutory.interestMultiplier(), true);
        InterestResult result = InterestEngine.compute(input);

        assertThat(result.interest()).isEqualTo(Paise.ofPaise(216863L));
        assertThat(result.closingBalance()).isEqualTo(Paise.ofPaise(7562541L));
        assertThat(result.rests()).hasSize(3);
        assertThat(result.rests().get(0).interestAccrued()).isEqualTo(Paise.ofPaise(96412L));
        assertThat(result.rests().get(1).interestAccrued()).isEqualTo(Paise.ofPaise(97677L));
        assertThat(result.rests().get(2).interestAccrued()).isEqualTo(Paise.ofPaise(22774L));
        assertThat(result.statusLabel()).isEqualTo("PROVISIONAL");
    }

    // ==========================================
    // Rule R-19 Refusal: Unconfirmed Bank Rate
    // ==========================================
    @Test
    @DisplayName("Refuses computation when bank rate is unconfirmed (Rule R-19)")
    void refusesUnconfirmedBankRate() {
        LocalDate from = LocalDate.of(2024, 3, 1);
        LocalDate to = LocalDate.of(2024, 4, 1);
        Paise principal = Paise.ofRupees(100000);

        // isRateConfirmed = false
        InterestInput unconfirmedInput = InterestInput.of(principal, from, to, 650, statutory.interestMultiplier(), false);

        assertThatThrownBy(() -> InterestEngine.compute(unconfirmedInput))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Refusing interest computation: Bank rate is unconfirmed")
            .hasMessageContaining("Rule R-19 requires human confirmation");
    }

    @Test
    @DisplayName("Refuses computation when RateCandidate has isConfirmed = false")
    void refusesUnconfirmedRateCandidate() {
        LocalDate from = LocalDate.of(2024, 3, 1);
        LocalDate to = LocalDate.of(2024, 4, 1);
        Paise principal = Paise.ofRupees(100000);

        RateCandidate unconfirmedCandidate = RateCandidate.unconfirmed(
            650,
            LocalDate.of(2024, 2, 1),
            "https://rbi.org.in/rates",
            "Extracted bank rate 6.50%"
        );

        assertThatThrownBy(() -> InterestEngine.compute(principal, from, to, unconfirmedCandidate))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Refusing interest computation: Bank rate is unconfirmed");
    }

    @Test
    @DisplayName("Zero duration results in zero interest and unchanged balance")
    void zeroDurationReturnsZeroInterest() {
        LocalDate date = LocalDate.of(2024, 3, 1);
        Paise principal = Paise.ofRupees(100000);

        InterestInput input = InterestInput.of(principal, date, date, 650, statutory.interestMultiplier(), true);
        InterestResult result = InterestEngine.compute(input);

        assertThat(result.interest()).isEqualTo(Paise.ZERO);
        assertThat(result.closingBalance()).isEqualTo(principal);
        assertThat(result.rests()).isEmpty();
        assertThat(result.statusLabel()).isEqualTo("PROVISIONAL");
    }
}
