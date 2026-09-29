package nl.stokpop.scramjet.loadgen;

import nl.stokpop.scramjet.loadgen.Statistics.Interval;
import nl.stokpop.scramjet.loadgen.Statistics.StepStats;

import java.io.PrintStream;
import java.util.List;
import java.util.Map;

/**
 * Plain text report printed when the run is finished: summary table,
 * failure reasons, response times over time as bars, and the used settings.
 */
final class AsciiReport implements Report {

    private static final int TIMELINE_ROWS = 30;
    private static final int BAR_WIDTH = 40;
    private static final long MS = 1_000_000L;

    private final PrintStream out;

    AsciiReport(PrintStream out) {
        this.out = out;
    }

    @Override
    public void started(Options options, List<LoadGen.Step> scenario, long totalRequests) {
        out.printf("Running %s scenario: %d requests at %.1f req/s for %s against %s%n",
                options.scenario(), totalRequests, options.rate(), options.duration(), options.baseUrl());
        if (options.insecure()) {
            out.println("WARNING: --insecure: TLS certificates and host names are NOT verified");
        }
    }

    @Override
    public void finished(Options options, List<LoadGen.Step> scenario, Results results) {
        out.print(render(options, scenario, results));
    }

    String render(Options options, List<LoadGen.Step> scenario, Results results) {
        return summary(results) + failures(results) + timeline(Statistics.timeline(results, TIMELINE_ROWS)) + settings(options, scenario);
    }

    static String summary(Results results) {
        StringBuilder text = new StringBuilder();
        text.append("%nFinished in %.1f s%n%n".formatted(results.elapsedNanos() / (double) Statistics.SECOND));
        text.append("%-8s %7s %7s %7s %7s %8s %8s %8s %8s %8s %8s %8s%n".formatted(
                "step", "total", "ok", "failed", "err", "req/s", "min", "p50", "p90", "p95", "p99", "max"));
        for (StepStats stats : Statistics.summary(results)) {
            text.append("%-8s %7d %7d %7d %6.1f%% %8.1f %8.1f %8.1f %8.1f %8.1f %8.1f %8.1f%n".formatted(
                    stats.name(), stats.total(), stats.ok(), stats.failed(), stats.errorPercentage(), stats.throughput(),
                    millis(stats.min()), millis(stats.p50()), millis(stats.p90()), millis(stats.p95()),
                    millis(stats.p99()), millis(stats.max())));
        }
        text.append("%nResponse times in ms, measured from scheduled request start.%n".formatted());
        return text.toString();
    }

    static String failures(Results results) {
        Map<String, Long> reasons = Statistics.failureReasons(results);
        if (reasons.isEmpty()) {
            return "";
        }
        StringBuilder text = new StringBuilder("%nFailures:%n".formatted());
        reasons.forEach((reason, count) -> text.append("  %-30s %d%n".formatted(reason, count)));
        return text.toString();
    }

    /**
     * Bar per interval: '#' up to p50, '=' up to p95, '-' up to max, '>' when max is beyond the scale.
     */
    static String timeline(List<Interval> intervals) {
        if (intervals.isEmpty()) {
            return "";
        }
        long intervalSeconds = intervals.size() > 1 ? intervals.get(1).startNanos() / Statistics.SECOND : 1;
        long scale = Math.max(10 * MS, intervals.stream().mapToLong(Interval::p95).max().orElse(0));

        StringBuilder text = new StringBuilder();
        text.append("%nResponse times over time (all steps, per %d s, in ms):%n".formatted(intervalSeconds));
        text.append("%7s %5s %4s %8s %8s %8s  %s%n".formatted("time", "reqs", "err", "p50", "p95", "max", axis(scale)));
        for (Interval interval : intervals) {
            text.append("%6ds %5d %4s %8.1f %8.1f %8.1f  %s%n".formatted(
                    interval.startNanos() / Statistics.SECOND, interval.count(),
                    interval.errors() == 0 ? "" : interval.errors() + "!",
                    millis(interval.p50()), millis(interval.p95()), millis(interval.max()),
                    bar(interval.p50(), interval.p95(), interval.max(), scale)));
        }
        text.append("Bars: # p50, = p95, - max, > max beyond scale. Log scale from 1 ms to %.1f ms, the highest p95 of any interval.%n"
                .formatted(millis(scale)));
        return text.toString();
    }

    static String settings(Options options, List<LoadGen.Step> scenario) {
        StringBuilder text = new StringBuilder("%nSettings:%n".formatted());
        text.append("  %-10s %s%n".formatted("url", options.baseUrl()));
        text.append("  %-10s %s%n".formatted("scenario", options.scenario()));
        text.append("  %-10s %s%n".formatted("duration", options.duration()));
        text.append("  %-10s %s req/s%n".formatted("rate", options.rate()));
        text.append("  %-10s %s%n".formatted("timeout", options.timeout()));
        if (options.insecure()) {
            text.append("  %-10s %s%n".formatted("tls", "insecure, no certificate or host name verification"));
        }
        scenario.forEach(step -> text.append("  %-10s %s%n".formatted(step.name(), step.path())));
        return text.toString();
    }

    static String bar(long p50, long p95, long max, long scale) {
        int n50 = p50 == 0 ? 0 : Math.max(1, width(p50, scale));
        int n95 = Math.max(n50, width(p95, scale));
        int nMax = Math.max(n95, Math.min(BAR_WIDTH, width(max, scale)));
        return "#".repeat(n50) + "=".repeat(n95 - n50) + "-".repeat(nMax - n95) + (max > scale ? ">" : "");
    }

    /**
     * Log scale, so a shift from 10 to 100 ms stays visible next to multi-second timeouts.
     */
    private static int width(long value, long scale) {
        if (value <= MS) {
            return 0;
        }
        double position = Math.log10((double) value / MS) / Math.log10((double) scale / MS);
        return (int) Math.round(Math.min(1.0, position) * BAR_WIDTH);
    }

    /**
     * Tick labels for the bar column at 1 ms, 10 ms, 100 ms, 1 s, ... up to the scale.
     */
    static String axis(long scale) {
        char[] axis = " ".repeat(BAR_WIDTH + 8).toCharArray();
        int nextFree = 0;
        for (long tick = MS; tick <= scale; tick *= 10) {
            String label = tick < 1000 * MS ? (tick / MS) + "ms" : (tick / (1000 * MS)) + "s";
            int position = Math.max(width(tick, scale), nextFree);
            if (position + label.length() > axis.length) {
                break;
            }
            label.getChars(0, label.length(), axis, position);
            nextFree = position + label.length() + 1;
        }
        return new String(axis).stripTrailing();
    }

    static double millis(long nanos) {
        return nanos / 1e6;
    }
}
