package nl.stokpop.scramjet.loadgen;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoadGenTests {

    @Test
    void percentileNearestRank() {
        long[] sorted = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        assertEquals(5, Results.percentile(sorted, 50));
        assertEquals(9, Results.percentile(sorted, 90));
        assertEquals(10, Results.percentile(sorted, 99));
        assertEquals(1, Results.percentile(sorted, 0));
        assertEquals(0, Results.percentile(new long[0], 50));
    }

    @Test
    void parseOptions() {
        Options options = Options.parse("--url", "http://host:9090/", "--duration", "2m", "--rate", "25.5");
        assertEquals("http://host:9090", options.baseUrl());
        assertEquals(Duration.ofMinutes(2), options.duration());
        assertEquals(25.5, options.rate());
    }

    @Test
    void defaultsToLocalhost() {
        assertEquals("http://localhost:8080", Options.parse().baseUrl());
    }

    @Test
    void parseDurations() {
        assertEquals(Duration.ofSeconds(30), Options.parseDuration("30s"));
        assertEquals(Duration.ofMillis(500), Options.parseDuration("500ms"));
        assertEquals(Duration.ofSeconds(90), Options.parseDuration("PT1M30S"));
        assertEquals(Duration.ofSeconds(12), Options.parseDuration("12"));
        assertThrows(IllegalArgumentException.class, () -> Options.parseDuration("soon"));
    }

    @Test
    void rejectsBadRate() {
        assertThrows(IllegalArgumentException.class, () -> Options.parse("--rate", "0"));
        assertThrows(IllegalArgumentException.class, () -> Options.parse("--rate"));
    }

    @Test
    void runsScenarioAgainstServer() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/ok", exchange -> {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.createContext("/fail", exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.start();
        try {
            Options options = Options.parse(
                    "--url", "http://localhost:" + server.getAddress().getPort(),
                    "--duration", "1s", "--rate", "20");
            Results results = LoadGen.run(options, List.of(
                    new LoadGen.Step("ok", "/ok"),
                    new LoadGen.Step("fail", "/fail")));

            assertEquals(10, results.successes("ok"));
            assertEquals(0, results.failures("ok"));
            assertEquals(0, results.successes("fail"));
            assertEquals(10, results.failures("fail"));
            String report = results.report();
            assertTrue(report.contains("HTTP 500"), report);
        } finally {
            server.stop(0);
        }
    }
}
