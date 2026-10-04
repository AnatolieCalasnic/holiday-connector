package nl.innolics.holidayprocessor.messaging;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;

/**
 * One validated payload, ready to store. For a removal only id, scope and observedAt are set.
 */
public record HolidayRecord(
        String id,
        boolean removed,
        Instant observedAt,
        String countryCode,
        int year,
        LocalDate date,
        String localName,
        String name,
        Boolean global,
        List<String> counties,
        List<String> types,
        String payloadJson) {

    /** What counts as "changed": the holiday itself, not when it was observed. */
    public String contentHash() {
        String content = String.join("|", String.valueOf(date), localName, name, String.valueOf(global),
                String.valueOf(counties), String.valueOf(types));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
