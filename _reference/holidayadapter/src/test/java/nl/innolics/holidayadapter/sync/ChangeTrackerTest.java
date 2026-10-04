package nl.innolics.holidayadapter.sync;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import nl.innolics.holidayadapter.domain.Holiday;
import nl.innolics.holidayadapter.domain.Scope;
import org.junit.jupiter.api.Test;

class ChangeTrackerTest {

    private static final Scope NL_2026 = new Scope("NL", 2026);
    private static final Instant T1 = Instant.parse("2026-09-27T06:00:00Z");
    private static final Instant T2 = Instant.parse("2026-09-28T06:00:00Z");

    private final ChangeTracker tracker = new ChangeTracker();

    private static Holiday holiday(String date, String name, String localName) {
        return Holiday.present("NL", LocalDate.parse(date), localName, name, true, null, List.of("Public"), T1);
    }

    private void sendAll(ChangeTracker.Changes changes) {
        changes.upserts().forEach(h -> tracker.markSent(NL_2026, h));
        changes.removals().forEach(h -> tracker.markSent(NL_2026, h));
    }

    @Test
    void firstRunSendsEverything() {
        var changes = tracker.diff(NL_2026, List.of(
                holiday("2026-01-01", "New Year's Day", "Nieuwjaarsdag"),
                holiday("2026-04-27", "King's Day", "Koningsdag")), T1);

        assertThat(changes.upserts()).hasSize(2);
        assertThat(changes.removals()).isEmpty();
    }

    @Test
    void secondRunWithTheSameDataSendsNothingEvenThoughObservedAtMoved() {
        var pull = List.of(holiday("2026-04-27", "King's Day", "Koningsdag"));
        sendAll(tracker.diff(NL_2026, pull, T1));

        var later = List.of(Holiday.present("NL", LocalDate.parse("2026-04-27"), "Koningsdag", "King's Day",
                true, null, List.of("Public"), T2));
        assertThat(tracker.diff(NL_2026, later, T2).isEmpty()).isTrue();
    }

    @Test
    void aChangedFieldIsSentAgain() {
        sendAll(tracker.diff(NL_2026, List.of(holiday("2026-04-27", "King's Day", "Koningsdag")), T1));

        var changes = tracker.diff(NL_2026, List.of(holiday("2026-04-27", "King's Day", "Koninginnedag")), T2);

        assertThat(changes.upserts()).extracting(Holiday::localName).containsExactly("Koninginnedag");
    }

    @Test
    void aHolidayThatDisappearsFromACompletePullIsRemoved() {
        sendAll(tracker.diff(NL_2026, List.of(
                holiday("2026-01-01", "New Year's Day", "Nieuwjaarsdag"),
                holiday("2026-05-05", "Liberation Day", "Bevrijdingsdag")), T1));

        var changes = tracker.diff(NL_2026, List.of(holiday("2026-01-01", "New Year's Day", "Nieuwjaarsdag")), T2);

        assertThat(changes.upserts()).isEmpty();
        assertThat(changes.removals()).singleElement().satisfies(r -> {
            assertThat(r.id()).isEqualTo("NL:2026-05-05:liberation-day");
            assertThat(r.status()).isEqualTo(Holiday.Status.removed);
            assertThat(r.observedAt()).isEqualTo(T2);
        });
    }

    @Test
    void removalIsScopedToTheYearThatWasPulled() {
        var nl2027 = new Scope("NL", 2027);
        var newYear2027 = Holiday.present("NL", LocalDate.parse("2027-01-01"), "Nieuwjaarsdag", "New Year's Day",
                true, null, List.of("Public"), T1);
        tracker.markSent(nl2027, newYear2027);

        var changes = tracker.diff(NL_2026, List.of(), T2);

        assertThat(changes.removals()).isEmpty();
    }

    @Test
    void anUnsentRecordIsOfferedAgainNextRun() {
        var pull = List.of(holiday("2026-04-27", "King's Day", "Koningsdag"));
        tracker.diff(NL_2026, pull, T1); // push failed: nothing marked

        assertThat(tracker.diff(NL_2026, pull, T2).upserts()).hasSize(1);
    }
}
