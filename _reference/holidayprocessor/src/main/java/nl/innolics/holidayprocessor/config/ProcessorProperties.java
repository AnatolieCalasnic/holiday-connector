package nl.innolics.holidayprocessor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "processor")
public record ProcessorProperties(
        String source,
        int typeId,
        String exchange,
        String queue,
        String deadLetterQueue) {

    /** The key sazdatastream publishes with: raw.&lt;meta.source&gt;.&lt;meta.type&gt;. */
    public String routingKey() {
        return "raw." + source + "." + typeId;
    }
}
