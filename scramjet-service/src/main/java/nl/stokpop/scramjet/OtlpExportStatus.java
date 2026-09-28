package nl.stokpop.scramjet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "management.otlp.metrics.export.enabled", havingValue = "false", matchIfMissing = true)
class OtlpExportStatus {

    private static final Logger log = LoggerFactory.getLogger(OtlpExportStatus.class);

    @EventListener(ApplicationReadyEvent.class)
    void logDisabled() {
        log.info("OTLP metrics export disabled, set OTLP_ENABLED=true to send metrics to an OpenTelemetry collector");
    }
}
