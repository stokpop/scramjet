package nl.stokpop.scramjet;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "scramjet")
public record ScramjetProperties(
        @DefaultValue("scramjet") String name,
        @DefaultValue("10") int delayCallLimit,
        @DefaultValue("http://localhost:8080") String remoteCallBaseUrl) {
}
