package nl.innolics.holidayadapter.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where to read holidays from, and which slice of them.
 */
@ConfigurationProperties(prefix = "nager")
public record NagerProperties(
        String baseUrl,
        List<String> countries,
        int yearsAhead,
        Duration timeout) {
}
