package nl.stokpop.scramjet.loadgen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread safe collector of response times, success and failure counts per step.
 */
final class Results {

    private final Map<String, StepResults> steps = new LinkedHashMap<>();
    private long elapsedNanos;

    Results(List<String> stepNames) {
        stepNames.forEach(name -> steps.put(name, new StepResults()));
    }

    void success(String step, long responseTimeNanos) {
        StepResults results = steps.get(step);
        results.responseTimes.add(responseTimeNanos);
        results.successes.incrementAndGet();
    }

    void failure(String step, long responseTimeNanos, String reason) {
        StepResults results = steps.get(step);
        results.responseTimes.add(responseTimeNanos);
        results.failureReasons.computeIfAbsent(reason, _ -> new AtomicLong()).incrementAndGet();
    }

    void finish(long elapsedNanos) {
        this.elapsedNanos = elapsedNanos;
    }

    long successes(String step) {
        return steps.get(step).successes.get();
    }

    long failures(String step) {
        return steps.get(step).failures();
    }

    String report() {
        double elapsedSeconds = elapsedNanos / 1e9;
        StringBuilder report = new StringBuilder();
        report.append("%nFinished in %.1f s%n%n".formatted(elapsedSeconds));

        String header = "%-8s %7s %7s %7s %7s %8s %8s %8s %8s %8s %8s %8s%n";
        String row = "%-8s %7d %7d %7d %6.1f%% %8.1f %8.1f %8.1f %8.1f %8.1f %8.1f %8.1f%n";
        report.append(header.formatted("step", "total", "ok", "failed", "err", "req/s", "min", "p50", "p90", "p95", "p99", "max"));

        List<Long> all = new ArrayList<>();
        long allSuccesses = 0;
        long allFailures = 0;
        Map<String, Long> allReasons = new TreeMap<>();
        for (Map.Entry<String, StepResults> entry : steps.entrySet()) {
            StepResults results = entry.getValue();
            List<Long> times = new ArrayList<>(results.responseTimes);
            all.addAll(times);
            allSuccesses += results.successes.get();
            allFailures += results.failures();
            results.failureReasons.forEach((reason, count) -> allReasons.merge(reason, count.get(), Long::sum));
            report.append(formatRow(row, entry.getKey(), times, results.successes.get(), results.failures(), elapsedSeconds));
        }
        report.append(formatRow(row, "all", all, allSuccesses, allFailures, elapsedSeconds));
        report.append("%nResponse times in ms, measured from scheduled request start.%n".formatted());

        if (!allReasons.isEmpty()) {
            report.append("%nFailures:%n".formatted());
            allReasons.forEach((reason, count) -> report.append("  %-30s %d%n".formatted(reason, count)));
        }
        return report.toString();
    }

    private static String formatRow(String format, String name, List<Long> timesNanos, long ok, long failed, double elapsedSeconds) {
        long[] sorted = timesNanos.stream().mapToLong(Long::longValue).sorted().toArray();
        long total = sorted.length;
        return format.formatted(name, total, ok, failed,
                total == 0 ? 0.0 : 100.0 * failed / total,
                elapsedSeconds == 0 ? 0.0 : total / elapsedSeconds,
                millis(sorted.length == 0 ? 0 : sorted[0]),
                millis(percentile(sorted, 50)),
                millis(percentile(sorted, 90)),
                millis(percentile(sorted, 95)),
                millis(percentile(sorted, 99)),
                millis(sorted.length == 0 ? 0 : sorted[sorted.length - 1]));
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

    private static double millis(long nanos) {
        return nanos / 1e6;
    }

    private static final class StepResults {
        final ConcurrentLinkedQueue<Long> responseTimes = new ConcurrentLinkedQueue<>();
        final AtomicLong successes = new AtomicLong();
        final Map<String, AtomicLong> failureReasons = new ConcurrentHashMap<>();

        long failures() {
            return failureReasons.values().stream().mapToLong(AtomicLong::get).sum();
        }
    }
}
