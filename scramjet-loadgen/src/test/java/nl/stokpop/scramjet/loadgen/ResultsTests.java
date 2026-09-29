package nl.stokpop.scramjet.loadgen;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static nl.stokpop.scramjet.loadgen.StatisticsTests.ok;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResultsTests {

    @Test
    void listenersSeeEverySample() {
        Results results = new Results(List.of("a"));
        List<Sample> seen = new ArrayList<>();
        results.addListener(seen::add);

        Sample sample = ok("a", 0, 1);
        results.add(sample);

        assertEquals(List.of(sample), seen);
        assertEquals(List.of(sample), results.samples());
    }

    @Test
    void samplesIsAnImmutableSnapshot() {
        Results results = new Results(List.of("a"));
        results.add(ok("a", 0, 1));

        List<Sample> snapshot = results.samples();
        results.add(ok("a", 0, 2));

        assertEquals(1, snapshot.size());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(ok("a", 0, 3)));
    }
}
