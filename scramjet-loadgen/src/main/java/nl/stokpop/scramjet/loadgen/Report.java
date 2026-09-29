package nl.stokpop.scramjet.loadgen;

import java.util.List;

/**
 * View on a run. Only finished is required; a live view can also act on
 * started and every sample (called concurrently from request threads).
 */
interface Report {

    List<String> NAMES = List.of("ascii");

    static Report forName(String name) {
        return switch (name) {
            case "ascii" -> new AsciiReport(System.out);
            default -> throw new IllegalArgumentException("Unknown report " + name + ", choose one of " + NAMES);
        };
    }

    default void started(Options options, List<LoadGen.Step> scenario, long totalRequests) {
    }

    default void sample(Sample sample) {
    }

    void finished(Options options, List<LoadGen.Step> scenario, Results results);
}
