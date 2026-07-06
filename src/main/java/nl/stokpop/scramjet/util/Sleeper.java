package nl.stokpop.scramjet.util;

import nl.stokpop.scramjet.ScramjetException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.format.DateTimeParseException;

public final class Sleeper {

    private static final Logger log = LoggerFactory.getLogger(Sleeper.class);

    private Sleeper() {
    }

    public static void sleep(String durationAsString) {
        Duration sleepDuration = parseDuration(durationAsString);
        long durationMillis = sleepDuration.toMillis();
        log.info("About to sleep for [{}] millis (durationAsString = [{}]).", durationMillis, durationAsString);
        long startTime = System.currentTimeMillis();
        sleep(sleepDuration);
        long actualDuration = System.currentTimeMillis() - startTime;
        log.info("Actual sleep was [{}] of expected [{}] millis. Delta: [{}]", actualDuration, durationMillis, actualDuration - durationMillis);
    }

    public static void sleep(Duration sleepDuration) {
        try {
            Thread.sleep(sleepDuration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Sleep received interrupt: {}", e.getMessage());
        }
    }

    /**
     * Parses plain millis (e.g. "100") or ISO-8601 durations (e.g. "PT0.5S").
     */
    private static Duration parseDuration(String duration) {
        if (duration.startsWith("P")) {
            try {
                return Duration.parse(duration);
            } catch (DateTimeParseException e) {
                throw new ScramjetException("Not a valid duration [%s]".formatted(duration), e);
            }
        }
        try {
            return Duration.ofMillis(Long.parseLong(duration));
        } catch (NumberFormatException e) {
            throw new ScramjetException("Not a valid duration [%s]".formatted(duration), e);
        }
    }
}
