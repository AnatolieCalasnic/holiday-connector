package nl.innolics.holidayadapter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How this adapter presents itself to the platform. Every value comes from configuration.
 */
@ConfigurationProperties(prefix = "adapter")
public record AdapterProperties(
        String managementUrl,
        String name,
        String source,
        int providerId,
        int typeId,
        String typeName,
        String processor,
        String cron,
        boolean runOnStartup,
        String dataApiOverride) {
}
