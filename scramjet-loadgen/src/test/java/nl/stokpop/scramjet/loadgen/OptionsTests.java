package nl.stokpop.scramjet.loadgen;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OptionsTests {

    @Test
    void defaults() {
        Options options = Options.parse();
        assertEquals("http://localhost:8080", options.baseUrl());
        assertEquals(Duration.ofSeconds(30), options.duration());
        assertEquals(10, options.rate());
        assertEquals("basic", options.scenario());
        assertEquals("ascii", options.report());
    }

    @Test
    void parsesValuesAndStripsTrailingSlash() {
        Options options = Options.parse("--url", "http://host:9090/", "--duration", "2m", "--rate", "25.5", "--scenario", "LEAK");
        assertEquals("http://host:9090", options.baseUrl());
        assertEquals(Duration.ofMinutes(2), options.duration());
        assertEquals(25.5, options.rate());
        assertEquals("leak", options.scenario());
    }

    @Test
    void builderSetsValuesAndValidates() {
        Options options = Options.builder().baseUrl("https://host/").rate(2.5).scenario("churn").insecure(true).build();
        assertEquals("https://host", options.baseUrl());
        assertEquals(2.5, options.rate());
        assertEquals("churn", options.scenario());
        assertEquals(true, options.insecure());
        assertThrows(IllegalArgumentException.class, () -> Options.builder().rate(0).build());
    }

    @Test
    void parsesDurations() {
        assertEquals(Duration.ofMillis(500), Options.parseDuration("500ms"));
        assertEquals(Duration.ofSeconds(30), Options.parseDuration("30s"));
        assertEquals(Duration.ofMinutes(2), Options.parseDuration("2m"));
        assertEquals(Duration.ofHours(1), Options.parseDuration("1h"));
        assertEquals(Duration.ofSeconds(90), Options.parseDuration("PT1M30S"));
        assertEquals(Duration.ofSeconds(12), Options.parseDuration("12"));
        assertThrows(IllegalArgumentException.class, () -> Options.parseDuration("soon"));
    }

    @Test
    void rejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> Options.parse("--rate", "0"));
        assertThrows(IllegalArgumentException.class, () -> Options.parse("--rate", "fast"));
        assertThrows(IllegalArgumentException.class, () -> Options.parse("--rate"));
        assertThrows(IllegalArgumentException.class, () -> Options.parse("--scenario", "boom"));
        assertThrows(IllegalArgumentException.class, () -> Options.parse("--report", "html"));
        assertThrows(IllegalArgumentException.class, () -> Options.parse("--bogus", "1"));
        assertThrows(IllegalArgumentException.class, () -> Options.parse("--delay-ms", "-1"));
    }

    @Test
    void zeroDelayMeansNoHold() {
        assertEquals(0, Options.parse("--delay-ms", "0").delayMillis());
    }
}
