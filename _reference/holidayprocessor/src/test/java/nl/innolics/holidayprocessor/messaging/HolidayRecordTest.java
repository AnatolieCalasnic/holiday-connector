package nl.innolics.holidayprocessor.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

class HolidayRecordTest {

    private static HolidayRecord record(Instant observedAt, String localName) {
        return new HolidayRecord("NL:2026-04-27:kings-day", false, observedAt, "NL", 2026,
                LocalDate.of(2026, 4, 27), localName, "King's Day", true, List.of(), List.of("Public"), "{}");
    }

    @Test
    void theSameHolidayObservedLaterHashesTheSame() {
        assertThat(record(Instant.parse("2026-09-27T06:00:00Z"), "Koningsdag").contentHash())
                .isEqualTo(record(Instant.parse("2026-09-28T06:00:00Z"), "Koningsdag").contentHash());
    }

    @Test
    void aChangedFieldChangesTheHash() {
        Instant t = Instant.parse("2026-09-27T06:00:00Z");
        assertThat(record(t, "Koningsdag").contentHash()).isNotEqualTo(record(t, "Koninginnedag").contentHash());
    }
}
