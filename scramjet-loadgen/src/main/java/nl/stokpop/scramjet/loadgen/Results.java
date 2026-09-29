package nl.stokpop.scramjet.loadgen;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Model of a run: thread safe collection of samples. Listeners are called for
 * every new sample, on the thread that recorded it, so views can update live.
 */
final class Results {

    private final List<String> stepNames;
    private final ConcurrentLinkedQueue<Sample> samples = new ConcurrentLinkedQueue<>();
    private final List<Consumer<Sample>> listeners = new CopyOnWriteArrayList<>();
    private volatile long elapsedNanos;

    Results(List<String> stepNames) {
        this.stepNames = List.copyOf(stepNames);
    }

    void addListener(Consumer<Sample> listener) {
        listeners.add(listener);
    }

    void add(Sample sample) {
        samples.add(sample);
        listeners.forEach(listener -> listener.accept(sample));
    }

    void finish(long elapsedNanos) {
        this.elapsedNanos = elapsedNanos;
    }

    List<String> stepNames() {
        return stepNames;
    }

    List<Sample> samples() {
        return List.copyOf(samples);
    }

    long elapsedNanos() {
        return elapsedNanos;
    }
}
