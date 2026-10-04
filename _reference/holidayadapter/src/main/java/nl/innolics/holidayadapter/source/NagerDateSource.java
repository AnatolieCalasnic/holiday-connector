package nl.innolics.holidayadapter.source;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import nl.innolics.holidayadapter.config.NagerProperties;
import nl.innolics.holidayadapter.domain.Holiday;
import nl.innolics.holidayadapter.domain.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Reads public holidays from Nager.Date: GET PublicHolidays/{year}/{countryCode}.
 */
@Component
public class NagerDateSource implements HolidaySource {

    private static final Logger log = LoggerFactory.getLogger(NagerDateSource.class);

    private final RestClient client;

    public NagerDateSource(RestClient.Builder builder, NagerProperties properties) {
        var settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(properties.timeout())
                .withReadTimeout(properties.timeout());
        this.client = builder
                .baseUrl(properties.baseUrl())
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .build();
    }

    @Override
    public List<Holiday> fetch(Scope scope, Instant observedAt) throws SourceUnavailableException {
        NagerHoliday[] response;
        try {
            response = client.get()
                    .uri("PublicHolidays/{year}/{country}", scope.year(), scope.countryCode())
                    .retrieve()
                    .body(NagerHoliday[].class);
        } catch (RestClientException e) {
            throw new SourceUnavailableException("Nager.Date unavailable for " + scope.key(), e);
        }
        if (response == null) {
            throw new SourceUnavailableException("Nager.Date returned no body for " + scope.key(), null);
        }
        return map(scope, response, observedAt);
    }

    static List<Holiday> map(Scope scope, NagerHoliday[] response, Instant observedAt) {
        List<Holiday> holidays = new ArrayList<>(response.length);
        for (NagerHoliday h : response) {
            if (h.date() == null || h.name() == null || h.name().isBlank()) {
                log.warn("Skipping holiday without date or name in {}: {}", scope.key(), h);
                continue;
            }
            holidays.add(Holiday.present(scope.countryCode(), h.date(), h.localName(), h.name(),
                    Boolean.TRUE.equals(h.global()), h.counties(), h.types(), observedAt));
        }
        return holidays;
    }

    /**
     * The fields of the Nager.Date response we depend on. Anything else is ignored.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record NagerHoliday(LocalDate date, String localName, String name, String countryCode,
                        Boolean global, List<String> counties, List<String> types) {
    }
}
