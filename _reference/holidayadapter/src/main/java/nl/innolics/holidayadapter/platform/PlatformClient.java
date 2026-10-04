package nl.innolics.holidayadapter.platform;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import nl.innolics.holidayadapter.config.AdapterProperties;
import nl.innolics.holidayadapter.domain.Holiday;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Talks to the platform: registration with sazmanagement, and pushes to the dataApi it hands back.
 */
@Component
public class PlatformClient {

    private static final Logger log = LoggerFactory.getLogger(PlatformClient.class);

    private final RestClient client;
    private final AdapterProperties properties;
    private final Envelope.Meta meta;

    public PlatformClient(RestClient.Builder builder, AdapterProperties properties) {
        this.client = builder.build();
        this.properties = properties;
        this.meta = new Envelope.Meta(properties.providerId(), properties.typeId(),
                MediaType.APPLICATION_JSON_VALUE, properties.source(), properties.processor());
    }

    /**
     * Registers this adapter and returns the push address. Empty when sazmanagement is down or
     * hands back no dataApi (it does after its ZooKeeper session expires; see ADR-001).
     */
    public Optional<URI> register() {
        var body = Map.of(
                "name", properties.name(),
                "id", properties.providerId(),
                "settings", Map.of(),
                "messageTypes", List.of(Map.of("id", properties.typeId(), "name", properties.typeName())),
                "provider", Map.of("name", "Nager.Date",
                        "description", "Public holidays per country, from date.nager.at"));
        try {
            RegistrationResponse response = client.post()
                    .uri(properties.managementUrl() + "/adapter/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(RegistrationResponse.class);
            if (response == null || response.dataApi() == null) {
                log.warn("Registered, but sazmanagement returned no dataApi");
                return Optional.empty();
            }
            log.info("Registered as {}; dataApi {}", properties.name(), response.dataApi());
            String override = properties.dataApiOverride();
            if (override != null && !override.isBlank()) {
                log.info("Using DATA_API_OVERRIDE {} instead", override);
                return Optional.of(URI.create(override));
            }
            return Optional.of(response.dataApi());
        } catch (RestClientException e) {
            log.warn("Registration failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** Pushes one record. Throws on any non-2xx, so the caller does not mark it as sent. */
    public void push(URI dataApi, Holiday holiday) {
        client.post()
                .uri(dataApi)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new Envelope(meta, holiday))
                .retrieve()
                .toBodilessEntity();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RegistrationResponse(URI dataApi, List<String> queues) {
    }
}
