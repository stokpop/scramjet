package nl.stokpop.scramjet.loadgen;

import nl.stokpop.scramjet.loadgen.Statistics.Interval;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Prints a row per tick while the run is going, for the responses that completed
 * since the previous tick, then the full {@link AsciiReport} at the end.
 *
 * Rows are appended rather than redrawn, so the output also works in logs and pipes.
 * Bars use a fixed log scale from 1 ms to the request timeout, so rows stay comparable.
 */
final class LiveAsciiReport implements Report {

    private static final int MAX_ROWS = 60;

    private final PrintStream out;
    private final LongSupplier clock;
    private final boolean autoTick;
    private final ConcurrentLinkedQueue<Sample> window = new ConcurrentLinkedQueue<>();

    private ScheduledExecutorService ticker;
    private Options options;
    private long totalRequests;
    private long startNanos;
    private long completed;

    LiveAsciiReport(PrintStream out) {
        this(out, System::nanoTime, true);
    }

    LiveAsciiReport(PrintStream out, LongSupplier clock, boolean autoTick) {
        this.out = out;
        this.clock = clock;
        this.autoTick = autoTick;
    }

    @Override
    public void started(Options options, List<LoadGen.Step> scenario, long totalRequests) {
        this.options = options;
        this.totalRequests = totalRequests;
        this.startNanos = clock.getAsLong();
        new AsciiReport(out).started(options, scenario, totalRequests);

        long tickSeconds = tickSeconds(options);
        out.printf("%nLive, per %d s, responses completed in that interval, in ms:%n", tickSeconds);
        out.printf("%7s %5s %4s %6s %8s %8s %8s  %s%n", "time", "done", "err", "flight", "p50", "p95", "max",
                AsciiReport.axis(options.timeout().toNanos()));
        if (autoTick) {
            ticker = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("live-report").factory());
            ticker.scheduleAtFixedRate(this::tick, tickSeconds, tickSeconds, TimeUnit.SECONDS);
        }
    }

    @Override
    public void sample(Sample sample) {
        window.add(sample);
    }

    @Override
    public void finished(Options options, List<LoadGen.Step> scenario, Results results) {
        if (ticker != null) {
            ticker.shutdownNow();
            try {
                ticker.awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (!window.isEmpty()) {
            tick();
        }
        new AsciiReport(out).finished(options, scenario, results);
    }

    /**
     * Prints one row for the samples completed since the previous tick.
     */
    synchronized void tick() {
        List<Sample> samples = new ArrayList<>();
        Sample sample;
        while ((sample = window.poll()) != null) {
            samples.add(sample);
        }
        completed += samples.size();

        long elapsedNanos = clock.getAsLong() - startNanos;
        Interval interval = Statistics.interval(elapsedNanos, samples);
        out.printf("%6ds %5d %4s %6d %8.1f %8.1f %8.1f  %s%n",
                Math.round(elapsedNanos / (double) Statistics.SECOND), interval.count(),
                interval.errors() == 0 ? "" : interval.errors() + "!",
                inFlight(elapsedNanos),
                AsciiReport.millis(interval.p50()), AsciiReport.millis(interval.p95()), AsciiReport.millis(interval.max()),
                AsciiReport.bar(interval.p50(), interval.p95(), interval.max(), options.timeout().toNanos()));
    }

    /**
     * Requests started so far, derived from the fixed rate, minus the ones that completed.
     */
    private long inFlight(long elapsedNanos) {
        long started = Math.min(totalRequests, (long) (elapsedNanos / 1e9 * options.rate()) + 1);
        return Math.max(0, started - completed);
    }

    /**
     * One second, stretched for long runs to stay within about 60 rows.
     */
    static long tickSeconds(Options options) {
        return Math.max(1, Math.ceilDiv(options.duration().toSeconds(), MAX_ROWS));
    }
}
