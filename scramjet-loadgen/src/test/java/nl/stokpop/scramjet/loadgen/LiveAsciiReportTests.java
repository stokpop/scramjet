package nl.stokpop.scramjet.loadgen;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static nl.stokpop.scramjet.loadgen.StatisticsTests.MS;
import static nl.stokpop.scramjet.loadgen.StatisticsTests.S;
import static nl.stokpop.scramjet.loadgen.StatisticsTests.failed;
import static nl.stokpop.scramjet.loadgen.StatisticsTests.ok;
import static nl.stokpop.scramjet.loadgen.StatisticsTests.results;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveAsciiReportTests {

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private final AtomicLong clock = new AtomicLong();
    private final LiveAsciiReport report = new LiveAsciiReport(new PrintStream(buffer, true, StandardCharsets.UTF_8), clock::get, false);
    private final Options options = Options.builder().rate(10).duration(Duration.ofSeconds(30)).build();

    private List<String> lines() {
        return buffer.toString(StandardCharsets.UTF_8).lines().toList();
    }

    @Test
    void tickPrintsCompletedSinceLastTickAndInFlight() {
        report.started(options, List.of(), 300);
        report.sample(ok("a", 0, 10 * MS));
        report.sample(ok("a", 0, 20 * MS));
        report.sample(failed("a", 0, 30 * MS, "HTTP 500"));

        clock.set(2 * S);
        report.tick();

        // 21 started at 10 req/s after 2 s, 3 completed; log scale 1 ms .. 10 s timeout: 20 ms = 13 chars
        assertEquals("     2s     3   1!     18     20.0     30.0     30.0  " + "#".repeat(13) + "=".repeat(2), lines().getLast());
    }

    @Test
    void emptyTickShowsNothingDoneAndGrowingInFlight() {
        report.started(options, List.of(), 300);
        report.sample(ok("a", 0, 10 * MS));
        clock.set(S);
        report.tick();

        clock.set(2 * S);
        report.tick();

        assertEquals("     2s     0          20      0.0      0.0      0.0  ", lines().getLast());
    }

    @Test
    void inFlightNeverCountsMoreThanTotalRequests() {
        report.started(options, List.of(), 5);
        clock.set(60 * S);
        report.tick();

        assertTrue(lines().getLast().startsWith("    60s     0           5"), lines().getLast());
    }

    @Test
    void finishedFlushesWindowThenPrintsFullReport() {
        report.started(options, List.of(), 1);
        Sample sample = ok("a", 0, 10 * MS);
        report.sample(sample);
        clock.set(S);

        report.finished(options, List.of(), results(S, List.of("a"), sample));

        String output = buffer.toString(StandardCharsets.UTF_8);
        assertTrue(output.indexOf("     1s     1") < output.indexOf("Finished in 1.0 s"), output);
    }

    @Test
    void tickStretchesForLongRuns() {
        assertEquals(1, LiveAsciiReport.tickSeconds(Options.builder().duration(Duration.ofSeconds(30)).build()));
        assertEquals(10, LiveAsciiReport.tickSeconds(Options.builder().duration(Duration.ofMinutes(10)).build()));
    }

    @Test
    void selectedByName() {
        assertInstanceOf(LiveAsciiReport.class, Report.forName(Options.parse("--report", "live").report()));
    }
}
