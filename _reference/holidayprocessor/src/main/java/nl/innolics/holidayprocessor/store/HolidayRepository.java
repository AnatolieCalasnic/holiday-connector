package nl.innolics.holidayprocessor.store;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import nl.innolics.holidayprocessor.messaging.HolidayRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saves by ID. Safe to call twice with the same record, and safe with two processor replicas.
 */
@Repository
public class HolidayRepository {

    public enum Outcome { INSERTED, UPDATED, RESTORED, REMOVED, UNCHANGED, IGNORED_STALE, IGNORED_UNKNOWN }

    private record Stored(String contentHash, Instant observedAt, Instant removedAt) {
    }

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public HolidayRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Transactional
    public Outcome apply(HolidayRecord r) {
        Stored stored = find(r.id());

        if (stored == null) {
            if (r.removed()) {
                return Outcome.IGNORED_UNKNOWN;
            }
            if (insert(r)) {
                return Outcome.INSERTED;
            }
            stored = find(r.id()); // another replica inserted it first
        }

        if (r.observedAt().isBefore(stored.observedAt())) {
            return Outcome.IGNORED_STALE; // late data never overwrites newer data
        }

        if (r.removed()) {
            if (stored.removedAt() != null) {
                return Outcome.UNCHANGED;
            }
            jdbc.update("""
                    UPDATE holiday SET removed_at = ?, observed_at = ?, payload = ?::jsonb,
                           version = version + 1, updated_at = now()
                    WHERE id = ?""",
                    ts(r.observedAt()), ts(r.observedAt()), r.payloadJson(), r.id());
            return Outcome.REMOVED;
        }

        if (stored.removedAt() == null && r.contentHash().equals(stored.contentHash())) {
            return Outcome.UNCHANGED;
        }
        jdbc.update("""
                UPDATE holiday SET date = ?, local_name = ?, name = ?, global = ?, counties = ?::jsonb,
                       types = ?::jsonb, payload = ?::jsonb, content_hash = ?, observed_at = ?,
                       removed_at = NULL, version = version + 1, updated_at = now()
                WHERE id = ?""",
                r.date(), r.localName(), r.name(), r.global(), json(r.counties()), json(r.types()),
                r.payloadJson(), r.contentHash(), ts(r.observedAt()), r.id());
        return stored.removedAt() != null ? Outcome.RESTORED : Outcome.UPDATED;
    }

    private Stored find(String id) {
        List<Stored> rows = jdbc.query(
                "SELECT content_hash, observed_at, removed_at FROM holiday WHERE id = ? FOR UPDATE",
                (rs, n) -> new Stored(rs.getString(1), instant(rs.getTimestamp(2)), instant(rs.getTimestamp(3))),
                id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private boolean insert(HolidayRecord r) {
        return jdbc.update("""
                INSERT INTO holiday (id, country_code, year, date, local_name, name, global, counties, types,
                                     payload, content_hash, observed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?)
                ON CONFLICT (id) DO NOTHING""",
                r.id(), r.countryCode(), r.year(), r.date(), r.localName(), r.name(), r.global(),
                json(r.counties()), json(r.types()), r.payloadJson(), r.contentHash(), ts(r.observedAt())) == 1;
    }

    private String json(List<String> values) {
        try {
            return mapper.writeValueAsString(values == null ? List.of() : values);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Timestamp ts(Instant instant) {
        return Timestamp.from(instant);
    }

    private static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
