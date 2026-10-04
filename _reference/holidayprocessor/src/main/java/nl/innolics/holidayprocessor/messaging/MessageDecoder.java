package nl.innolics.holidayprocessor.messaging;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import nl.innolics.holidayprocessor.config.ProcessorProperties;
import org.springframework.stereotype.Component;

/**
 * Turns a raw message body into a {@link HolidayRecord}, or says exactly why it cannot.
 * <p>
 * sazdatastream JSON-encodes the envelope twice, so the body is usually a JSON string containing
 * JSON (ADR-001, finding 2). Plain JSON is accepted too, so this keeps working if that is fixed.
 */
@Component
public class MessageDecoder {

    static final int SCHEMA_VERSION = 1;

    private final ObjectMapper mapper;
    private final String expectedSource;

    public MessageDecoder(ObjectMapper mapper, ProcessorProperties properties) {
        this.mapper = mapper;
        this.expectedSource = properties.source();
    }

    public HolidayRecord decode(String body) throws InvalidMessageException {
        JsonNode root = parse(body);
        if (root.isTextual()) {
            root = parse(root.asText());
        }
        if (!root.isObject()) {
            throw new InvalidMessageException("body is not a JSON object");
        }

        String source = root.path("meta").path("source").asText("");
        if (!expectedSource.equals(source)) {
            throw new InvalidMessageException("meta.source is '" + source + "', expected '" + expectedSource + "'");
        }

        JsonNode p = root.path("payload");
        if (!p.isObject()) {
            throw new InvalidMessageException("payload is missing or not an object");
        }
        int schemaVersion = p.path("schemaVersion").asInt(-1);
        if (schemaVersion != SCHEMA_VERSION) {
            throw new InvalidMessageException("unsupported payload.schemaVersion " + schemaVersion);
        }

        String id = required(p, "id");
        String status = required(p, "status");
        if (!status.equals("present") && !status.equals("removed")) {
            throw new InvalidMessageException("payload.status '" + status + "' is not present|removed");
        }
        boolean removed = status.equals("removed");
        Instant observedAt = instant(p, "observedAt");
        String countryCode = required(p, "countryCode");
        int year = p.path("year").asInt(0);
        if (year == 0) {
            throw new InvalidMessageException("payload.year is missing");
        }
        String payloadJson = p.toString();

        if (removed) {
            return new HolidayRecord(id, true, observedAt, countryCode, year,
                    null, null, null, null, null, null, payloadJson);
        }
        return new HolidayRecord(id, false, observedAt, countryCode, year,
                date(p, "date"), p.path("localName").asText(null), required(p, "name"),
                p.path("global").asBoolean(false), strings(p, "counties"), strings(p, "types"), payloadJson);
    }

    private JsonNode parse(String text) throws InvalidMessageException {
        try {
            return mapper.readTree(text);
        } catch (JsonProcessingException e) {
            throw new InvalidMessageException("body is not valid JSON");
        }
    }

    private static String required(JsonNode p, String field) throws InvalidMessageException {
        String value = p.path(field).asText("");
        if (value.isBlank()) {
            throw new InvalidMessageException("payload." + field + " is missing");
        }
        return value;
    }

    private static Instant instant(JsonNode p, String field) throws InvalidMessageException {
        try {
            return Instant.parse(required(p, field));
        } catch (DateTimeParseException e) {
            throw new InvalidMessageException("payload." + field + " is not an ISO-8601 instant");
        }
    }

    private static LocalDate date(JsonNode p, String field) throws InvalidMessageException {
        try {
            return LocalDate.parse(required(p, field));
        } catch (DateTimeParseException e) {
            throw new InvalidMessageException("payload." + field + " is not an ISO-8601 date");
        }
    }

    private static List<String> strings(JsonNode p, String field) {
        List<String> values = new ArrayList<>();
        p.path(field).forEach(n -> values.add(n.asText()));
        return values;
    }
}
