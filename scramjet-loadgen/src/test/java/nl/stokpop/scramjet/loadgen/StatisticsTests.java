package nl.stokpop.scramjet.loadgen;

import nl.stokpop.scramjet.loadgen.Statistics.Interval;
import nl.stokpop.scramjet.loadgen.Statistics.StepStats;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatisticsTests {

    static final long MS = 1_000_000L;
    static final long S = 1_000_000_000L;

    static Sample ok(String step, long offset, long responseTime) {
        return new Sample(step, offset, responseTime, null);
    }

    static Sample failed(String step, long offset, long responseTime, String reason) {
        return new Sample(step, offset, responseTime, reason);
    }

    static Results results(long elapsedNanos, List<String> steps, Sample... samples) {
        Results results = new Results(steps);
        for (Sample sample : samples) {
            results.add(sample);
        }
        results.finish(elapsedNanos);
        return results;
    }

    @Test
    void percentileIsNearestRank() {
        long[] sorted = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        assertEquals(1, Statistics.percentile(sorted, 0));
        assertEquals(5, Statistics.percentile(sorted, 50));
        assertEquals(9, Statistics.percentile(sorted, 90));
        assertEquals(10, Statistics.percentile(sorted, 95));
        assertEquals(10, Statistics.percentile(sorted, 100));
        assertEquals(0, Statistics.percentile(new long[0], 50));
    }

    @Test
    void stepStatsBuilderDerivesPercentilesAndFailures() {
        long[] sorted = {1, 2, 3, 4, 5, 6, 7, 8, 9, 100};

        StepStats stats = StepStats.builder("x").counts(10, 7).throughput(5).sortedResponseTimes(sorted).build();

        assertEquals(new StepStats("x", 10, 7, 3, 5, 1, 5, 9, 100, 100, 100), stats);
        assertEquals(30.0, stats.errorPercentage());
    }

    @Test
    void summaryPerStepAndAll() {
        Results results = results(2 * S, List.of("a", "b"),
                ok("a", 0, 10 * MS), ok("a", 0, 30 * MS), ok("a", 0, 20 * MS), failed("a", 0, 40 * MS, "HTTP 500"),
                ok("b", 0, 5 * MS));

        List<StepStats> summary = Statistics.summary(results);

        assertEquals(List.of("a", "b", "all"), summary.stream().map(StepStats::name).toList());
        StepStats a = summary.get(0);
        assertEquals(4, a.total());
        assertEquals(3, a.ok());
        assertEquals(1, a.failed());
        assertEquals(25.0, a.errorPercentage());
        assertEquals(2.0, a.throughput());
        assertEquals(10 * MS, a.min());
        assertEquals(20 * MS, a.p50());
        assertEquals(40 * MS, a.max());
        StepStats all = summary.get(2);
        assertEquals(5, all.total());
        assertEquals(5 * MS, all.min());
        assertEquals(2.5, all.throughput());
    }

    @Test
    void summaryOfStepWithoutSamplesIsZero() {
        StepStats a = Statistics.summary(results(S, List.of("a"))).getFirst();
        assertEquals(0, a.total());
        assertEquals(0, a.errorPercentage());
        assertEquals(0, a.max());
    }

    @Test
    void failureReasonsCountedAndSorted() {
        Results results = results(S, List.of("a"),
                failed("a", 0, 1, "Timeout"), failed("a", 0, 1, "HTTP 500"), failed("a", 0, 1, "HTTP 500"), ok("a", 0, 1));

        assertEquals(Map.of("HTTP 500", 2L, "Timeout", 1L), Statistics.failureReasons(results));
        assertEquals(List.of("HTTP 500", "Timeout"), List.copyOf(Statistics.failureReasons(results).keySet()));
    }

    @Test
    void timelineBucketsBySchedule() {
        Results results = results(2 * S, List.of("a"),
                ok("a", 0, 10 * MS), ok("a", 500 * MS, 20 * MS),
                ok("a", S, 100 * MS), failed("a", S + 900 * MS, 300 * MS, "HTTP 500"));

        List<Interval> timeline = Statistics.timeline(results, 30);

        assertEquals(List.of(
                new Interval(0, 2, 0, 10 * MS, 20 * MS, 20 * MS),
                new Interval(S, 2, 1, 100 * MS, 300 * MS, 300 * MS)), timeline);
    }

    @Test
    void timelineIntervalsAreWholeSecondsWithinMaxRows() {
        Results results = results(100 * S, List.of("a"), ok("a", 0, MS), ok("a", 99 * S, MS));

        List<Interval> timeline = Statistics.timeline(results, 30);

        // 100 s over at most 30 rows: 3.33 s rounds up to 4 s intervals, 25 rows
        assertEquals(25, timeline.size());
        assertEquals(4 * S, timeline.get(1).startNanos());
        assertEquals(1, timeline.getLast().count());
        assertTrue(timeline.subList(1, 24).stream().allMatch(interval -> interval.count() == 0));
    }

    @Test
    void timelineOfNoSamplesIsEmpty() {
        assertEquals(List.of(), Statistics.timeline(results(S, List.of("a")), 30));
    }
}
