package nl.innolics.holidayadapter.source;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import nl.innolics.holidayadapter.domain.Holiday;
import nl.innolics.holidayadapter.domain.Scope;
import org.junit.jupiter.api.Test;

/**
 * Contract test against a real Nager.Date response (fixtures/nager-NL-2026.json, saved 27 Sep 2026).
 * If the source changes shape, this fails before production does.
 */
class NagerDateSourceTest {

    private static final Instant NOW = Instant.parse("2026-09-27T06:00:00Z");
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private List<Holiday> fixture() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/fixtures/nager-NL-2026.json")) {
            NagerDateSource.NagerHoliday[] raw = mapper.readValue(in, NagerDateSource.NagerHoliday[].class);
            return NagerDateSource.map(new Scope("NL", 2026), raw, NOW);
        }
    }

    @Test
    void parsesEveryHolidayInTheRealResponse() throws Exception {
        assertThat(fixture()).hasSize(11);
    }

    @Test
    void mapsTheFieldsWeDependOn() throws Exception {
        Holiday kingsDay = fixture().stream()
                .filter(h -> h.name().equals("King's Day")).findFirst().orElseThrow();

        assertThat(kingsDay.id()).isEqualTo("NL:2026-04-27:kings-day");
        assertThat(kingsDay.date()).isEqualTo(LocalDate.of(2026, 4, 27));
        assertThat(kingsDay.localName()).isEqualTo("Koningsdag");
        assertThat(kingsDay.global()).isTrue();
        assertThat(kingsDay.types()).containsExactly("Public");
        assertThat(kingsDay.counties()).isEmpty();
        assertThat(kingsDay.status()).isEqualTo(Holiday.Status.present);
        assertThat(kingsDay.observedAt()).isEqualTo(NOW);
    }

    @Test
    void idsAreUniqueWithinAYear() throws Exception {
        assertThat(fixture()).extracting(Holiday::id).doesNotHaveDuplicates();
    }

    @Test
    void skipsARecordWithoutADateInsteadOfFailing() {
        var raw = new NagerDateSource.NagerHoliday[] {
                new NagerDateSource.NagerHoliday(null, "Kapot", "Broken", "NL", true, null, null),
                new NagerDateSource.NagerHoliday(LocalDate.of(2026, 1, 1), "Nieuwjaarsdag", "New Year's Day",
                        "NL", true, null, List.of("Public"))
        };
        assertThat(NagerDateSource.map(new Scope("NL", 2026), raw, NOW))
                .extracting(Holiday::id).containsExactly("NL:2026-01-01:new-years-day");
    }
}
