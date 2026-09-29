package nl.stokpop.scramjet.loadgen;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Pure calculations on samples, shared by all report views.
 */
final class Statistics {

    static final long SECOND = 1_000_000_000L;

    private Statistics() {
    }

    record StepStats(String name, long total, long ok, long failed, double throughput,
                     long min, long p50, long p90, long p95, long p99, long max) {

        static Builder builder(String name) {
            return new Builder(name);
        }

        double errorPercentage() {
            return total == 0 ? 0 : 100.0 * failed / total;
        }

        static final class Builder {
            private final String name;
            private long total;
            private long ok;
            private double throughput;
            private long[] sortedResponseTimes = new long[0];

            private Builder(String name) {
                this.name = name;
            }

            Builder counts(long total, long ok) {
                this.total = total;
                this.ok = ok;
                return this;
            }

            Builder throughput(double throughput) {
                this.throughput = throughput;
                return this;
            }

            Builder sortedResponseTimes(long[] sortedResponseTimes) {
                this.sortedResponseTimes = sortedResponseTimes;
                return this;
            }

            StepStats build() {
                long[] sorted = sortedResponseTimes;
                return new StepStats(name, total, ok, total - ok, throughput,
                        sorted.length == 0 ? 0 : sorted[0],
                        percentile(sorted, 50), percentile(sorted, 90), percentile(sorted, 95), percentile(sorted, 99),
                        Statistics.max(sorted));
            }
        }
    }

    record Interval(long startNanos, long count, long errors, long p50, long p95, long max) {
    }

    /**
     * One entry per step plus a last "all" entry.
     */
    static List<StepStats> summary(Results results) {
        List<Sample> samples = results.samples();
        double elapsedSeconds = results.elapsedNanos() / (double) SECOND;
        List<StepStats> stats = new ArrayList<>();
        for (String step : results.stepNames()) {
            stats.add(stepStats(step, samples, s -> s.step().equals(step), elapsedSeconds));
        }
        stats.add(stepStats("all", samples, _ -> true, elapsedSeconds));
        return stats;
    }

    static Map<String, Long> failureReasons(Results results) {
        return results.samples().stream()
                .filter(sample -> !sample.ok())
                .collect(Collectors.groupingBy(Sample::failure, TreeMap::new, Collectors.counting()));
    }

    /**
     * Samples bucketed by scheduled start into at most maxRows intervals of whole seconds.
     */
    static List<Interval> timeline(Results results, int maxRows) {
        List<Sample> samples = results.samples();
        if (samples.isEmpty()) {
            return List.of();
        }
        long span = samples.stream().mapToLong(Sample::offsetNanos).max().orElse(0) + 1;
        long intervalNanos = Math.max(1, Math.ceilDiv(Math.ceilDiv(span, maxRows), SECOND)) * SECOND;
        int count = (int) Math.ceilDiv(span, intervalNanos);

        List<List<Sample>> buckets = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            buckets.add(new ArrayList<>());
        }
        samples.forEach(sample -> buckets.get((int) (sample.offsetNanos() / intervalNanos)).add(sample));

        List<Interval> intervals = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            intervals.add(interval(i * intervalNanos, buckets.get(i)));
        }
        return intervals;
    }

    static Interval interval(long startNanos, List<Sample> samples) {
        long[] sorted = sortedResponseTimes(samples);
        long errors = samples.stream().filter(sample -> !sample.ok()).count();
        return new Interval(startNanos, sorted.length, errors, percentile(sorted, 50), percentile(sorted, 95), max(sorted));
    }

    /**
     * Nearest-rank percentile on an ascending sorted array.
     */
    static long percentile(long[] sorted, double percentile) {
        if (sorted.length == 0) {
            return 0;
        }
        int rank = (int) Math.ceil(percentile / 100.0 * sorted.length);
        return sorted[Math.clamp(rank - 1, 0, sorted.length - 1)];
    }

    private static StepStats stepStats(String name, List<Sample> samples, Predicate<Sample> filter, double elapsedSeconds) {
        List<Sample> selected = samples.stream().filter(filter).toList();
        long[] sorted = sortedResponseTimes(selected);
        return StepStats.builder(name)
                .counts(sorted.length, selected.stream().filter(Sample::ok).count())
                .throughput(elapsedSeconds == 0 ? 0 : sorted.length / elapsedSeconds)
                .sortedResponseTimes(sorted)
                .build();
    }

    private static long[] sortedResponseTimes(List<Sample> samples) {
        return samples.stream().mapToLong(Sample::responseTimeNanos).sorted().toArray();
    }

    private static long max(long[] sorted) {
        return sorted.length == 0 ? 0 : sorted[sorted.length - 1];
    }
}
