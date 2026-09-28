package nl.stokpop.scramjet;

import io.micrometer.registry.otlp.OtlpMetricsSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

/**
 * An unreachable collector fails every export step, and Micrometer logs each failure
 * with a full stacktrace. Log it here as a single line instead.
 */
@Component
class OtlpExportFailureLogger implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(OtlpExportFailureLogger.class);

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof OtlpMetricsSender sender) {
            return (OtlpMetricsSender) request -> {
                try {
                    sender.send(request);
                } catch (Exception e) {
                    log.warn("OTLP metrics export failed: {}", e.getMessage() != null ? e.getMessage() : e.toString());
                }
            };
        }
        return bean;
    }
}
