package nl.innolics.holidayprocessor.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;

import com.fasterxml.jackson.databind.ObjectMapper;
import nl.innolics.holidayprocessor.config.ProcessorProperties;
import org.junit.jupiter.api.Test;

class MessageDecoderTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final MessageDecoder decoder = new MessageDecoder(mapper,
            new ProcessorProperties("nagerdate", 904, "saz-data-exchange", "nagerdate.processor", "nagerdate.processor.dlq"));

    private static final String PRESENT = """
            {"meta":{"provider":104,"type":904,"mime-type":"application/json","source":"nagerdate","processor":"nagerdate.processor"},
             "payload":{"schemaVersion":1,"id":"NL:2026-04-27:kings-day","status":"present",
                        "observedAt":"2026-09-27T06:00:00Z","countryCode":"NL","year":2026,"date":"2026-04-27",
                        "localName":"Koningsdag","name":"King's Day","global":true,"counties":[],"types":["Public"]}}""";

    private static final String REMOVED = """
            {"meta":{"source":"nagerdate"},
             "payload":{"schemaVersion":1,"id":"NL:2026-05-05:liberation-day","status":"removed",
                        "observedAt":"2026-09-28T06:00:00Z","countryCode":"NL","year":2026}}""";

    @Test
    void decodesPlainJson() throws Exception {
        HolidayRecord r = decoder.decode(PRESENT);

        assertThat(r.id()).isEqualTo("NL:2026-04-27:kings-day");
        assertThat(r.removed()).isFalse();
        assertThat(r.date()).isEqualTo(LocalDate.of(2026, 4, 27));
        assertThat(r.localName()).isEqualTo("Koningsdag");
        assertThat(r.types()).containsExactly("Public");
        assertThat(r.observedAt()).isEqualTo(Instant.parse("2026-09-27T06:00:00Z"));
    }

    @Test
    void decodesTheDoubleEncodedBodySazdatastreamActuallySends() throws Exception {
        String doubleEncoded = mapper.writeValueAsString(PRESENT); // a JSON string containing JSON

        assertThat(decoder.decode(doubleEncoded).id()).isEqualTo("NL:2026-04-27:kings-day");
    }

    @Test
    void decodesARemovalWithOnlyItsIdAndScope() throws Exception {
        HolidayRecord r = decoder.decode(REMOVED);

        assertThat(r.removed()).isTrue();
        assertThat(r.id()).isEqualTo("NL:2026-05-05:liberation-day");
        assertThat(r.date()).isNull();
    }

    @Test
    void rejectsAnotherSourcesMessage() {
        assertThatThrownBy(() -> decoder.decode(PRESENT.replace("\"source\":\"nagerdate\"", "\"source\":\"challengetool\"")))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessageContaining("meta.source is 'challengetool'");
    }

    @Test
    void rejectsAMissingId() {
        assertThatThrownBy(() -> decoder.decode(PRESENT.replace("\"id\":\"NL:2026-04-27:kings-day\",", "")))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessage("payload.id is missing");
    }

    @Test
    void rejectsAnUnknownStatus() {
        assertThatThrownBy(() -> decoder.decode(PRESENT.replace("\"present\"", "\"deleted\"")))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessageContaining("is not present|removed");
    }

    @Test
    void rejectsAFutureSchemaVersion() {
        assertThatThrownBy(() -> decoder.decode(PRESENT.replace("\"schemaVersion\":1", "\"schemaVersion\":2")))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessage("unsupported payload.schemaVersion 2");
    }

    @Test
    void rejectsABadDate() {
        assertThatThrownBy(() -> decoder.decode(PRESENT.replace("\"date\":\"2026-04-27\"", "\"date\":\"27-04-2026\"")))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessage("payload.date is not an ISO-8601 date");
    }

    @Test
    void rejectsGarbage() {
        assertThatThrownBy(() -> decoder.decode("not json at all"))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessage("body is not valid JSON");
    }
}
