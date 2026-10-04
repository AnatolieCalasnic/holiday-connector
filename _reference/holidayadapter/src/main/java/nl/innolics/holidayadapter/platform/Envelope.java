package nl.innolics.holidayadapter.platform;

import com.fasterxml.jackson.annotation.JsonProperty;
import nl.innolics.holidayadapter.domain.Holiday;

/**
 * What sazdatastream accepts on POST /data/push. It validates only meta; the payload is our contract
 * with the processor. The platform routes on raw.&lt;meta.source&gt;.&lt;meta.type&gt;.
 */
public record Envelope(Meta meta, Holiday payload) {

    public record Meta(
            int provider,
            int type,
            @JsonProperty("mime-type") String mimeType,
            String source,
            String processor) {
    }
}
