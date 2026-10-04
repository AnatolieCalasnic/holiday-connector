package nl.innolics.holidayadapter.domain;

import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The payload of one message: payload contract v1 (see docs/PLAN.md).
 * A present holiday carries every field; a removed one carries only id, scope, status and observedAt.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Holiday(
        int schemaVersion,
        String id,
        Status status,
        Instant observedAt,
        String countryCode,
        int year,
        LocalDate date,
        String localName,
        String name,
        Boolean global,
        List<String> counties,
        List<String> types) {

    public static final int SCHEMA_VERSION = 1;

    public enum Status { present, removed }

    public static Holiday present(String countryCode, LocalDate date, String localName, String name,
                                  boolean global, List<String> counties, List<String> types, Instant observedAt) {
        return new Holiday(SCHEMA_VERSION, idOf(countryCode, date, name), Status.present, observedAt,
                countryCode, date.getYear(), date, localName, name, global,
                counties == null ? List.of() : List.copyOf(counties),
                types == null ? List.of() : List.copyOf(types));
    }

    public static Holiday removed(String id, Scope scope, Instant observedAt) {
        return new Holiday(SCHEMA_VERSION, id, Status.removed, observedAt,
                scope.countryCode(), scope.year(), null, null, null, null, null, null);
    }

    /**
     * The source has no IDs, so we build a stable one: country, date and the English name as a slug.
     * Two holidays on the same day stay distinct; a renamed holiday reads as one removed and one added.
     */
    public static String idOf(String countryCode, LocalDate date, String name) {
        String slug = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replace("'", "")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return countryCode + ":" + date + ":" + slug;
    }

    /**
     * What counts as "changed". Excludes observedAt and status, which differ on every pull.
     */
    @JsonIgnore
    public String fingerprint() {
        return String.join("|", String.valueOf(date), localName, name, String.valueOf(global),
                String.valueOf(counties), String.valueOf(types));
    }
}
