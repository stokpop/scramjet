package nl.stokpop.scramjet.loadgen;

import nl.stokpop.scramjet.loadgen.Statistics.Interval;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static nl.stokpop.scramjet.loadgen.StatisticsTests.MS;
import static nl.stokpop.scramjet.loadgen.StatisticsTests.S;
import static nl.stokpop.scramjet.loadgen.StatisticsTests.failed;
import static nl.stokpop.scramjet.loadgen.StatisticsTests.ok;
import static nl.stokpop.scramjet.loadgen.StatisticsTests.results;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsciiReportTests {

    @Test
    void timelineBarsOnLogScale() {
        // scale is the highest p95, 1000 ms: log10 spans 3 decades over 40 chars
        List<Interval> intervals = List.of(
                new Interval(0, 10, 0, 10 * MS, 100 * MS, 100 * MS),
                new Interval(S, 10, 2, 100 * MS, 1000 * MS, 1000 * MS));

        String[] lines = AsciiReport.timeline(intervals).split("\n");

        assertEquals("     0s    10          10.0    100.0    100.0  " + "#".repeat(13) + "=".repeat(14), lines[3]);
        assertEquals("     1s    10   2!    100.0   1000.0   1000.0  " + "#".repeat(27) + "=".repeat(13), lines[4]);
    }

    @Test
    void timelineAxisHasDecadeTicks() {
        List<Interval> intervals = List.of(new Interval(0, 1, 0, 1000 * MS, 1000 * MS, 1000 * MS));

        String header = AsciiReport.timeline(intervals).split("\n")[2];

        // ticks at 0, 13, 27 and 40 chars: one third of the bar per decade
        assertTrue(header.endsWith("1ms          10ms          100ms        1s"), header);
    }

    @Test
    void timelineMarksMaxBeyondScale() {
        List<Interval> intervals = List.of(new Interval(0, 20, 0, 10 * MS, 100 * MS, 5000 * MS));

        String row = AsciiReport.timeline(intervals).split("\n")[3];

        // p95 is the scale, so the bar is full; max beyond it only adds '>'
        assertTrue(row.endsWith("  " + "#".repeat(20) + "=".repeat(20) + ">"), row);
    }

    @Test
    void timelineIntervalLengthFromSecondRow() {
        List<Interval> intervals = List.of(new Interval(0, 1, 0, MS, MS, MS), new Interval(4 * S, 1, 0, MS, MS, MS));

        assertTrue(AsciiReport.timeline(intervals).contains("per 4 s"));
        assertEquals("", AsciiReport.timeline(List.of()));
    }

    @Test
    void summaryRowFormatsMillis() {
        String summary = AsciiReport.summary(results(S, List.of("a"), ok("a", 0, 12_500_000), failed("a", 0, 37_500_000, "HTTP 500")));

        assertTrue(summary.contains("a              2       1       1   50.0%      2.0     12.5     12.5     37.5     37.5     37.5     37.5"), summary);
        assertTrue(summary.contains("Finished in 1.0 s"), summary);
    }

    @Test
    void failuresOnlyWhenThereAreAny() {
        assertEquals("", AsciiReport.failures(results(S, List.of("a"), ok("a", 0, MS))));
        assertTrue(AsciiReport.failures(results(S, List.of("a"), failed("a", 0, MS, "HTTP 503"))).contains("HTTP 503                       1"));
    }

    @Test
    void settingsShowOptionsAndStepPaths() {
        Options options = Options.builder().scenario("leak").rate(5).leakItems(7).duration(Duration.ofMinutes(1)).build();

        String settings = AsciiReport.settings(options, LoadGen.scenario(options));

        assertTrue(settings.contains("scenario   leak"), settings);
        assertTrue(settings.contains("rate       5.0 req/s"), settings);
        assertTrue(settings.contains("duration   PT1M"), settings);
        assertTrue(settings.contains("leak       /memory/grow?objects=1&length=100&items=7"), settings);
        assertTrue(settings.contains("delay      /delay?duration=100"), settings);
    }
}
