package nl.innolics.holidayadapter.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class HolidayTest {

    private static final LocalDate DAY = LocalDate.of(2026, 12, 26);

    @Test
    void idIsCountryDateAndSlug() {
        assertThat(Holiday.idOf("NL", DAY, "St. Stephen's Day")).isEqualTo("NL:2026-12-26:st-stephens-day");
    }

    @Test
    void idDropsAccents() {
        assertThat(Holiday.idOf("DE", DAY, "Zweiter Weihnachtsfeiertag – Stéphane"))
                .isEqualTo("DE:2026-12-26:zweiter-weihnachtsfeiertag-stephane");
    }

    @Test
    void twoHolidaysOnTheSameDayGetDifferentIds() {
        assertThat(Holiday.idOf("NL", DAY, "Boxing Day")).isNotEqualTo(Holiday.idOf("NL", DAY, "Second Christmas Day"));
    }
}
