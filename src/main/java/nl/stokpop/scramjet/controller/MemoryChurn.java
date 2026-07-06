package nl.stokpop.scramjet.controller;

import io.swagger.v3.oas.annotations.Operation;
import nl.stokpop.scramjet.ScramjetProperties;
import nl.stokpop.scramjet.domain.BurnerMessage;
import nl.stokpop.scramjet.util.Sleeper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Creates lots of local objects per request to fill young heap space.
 */
@RestController
public class MemoryChurn {

    private final ScramjetProperties props;

    public MemoryChurn(final ScramjetProperties props) {
        this.props = props;
    }

    @Operation(summary = "Simulate high object churn: lots of objects created per request.")
    @GetMapping("/memory/churn")
    public BurnerMessage memoryChurn(@RequestParam(value = "objects", defaultValue = "181") int objects,
                                     @RequestParam(value = "duration", defaultValue = "100") String duration) {
        long startTime = System.currentTimeMillis();

        List<BigDecimal> numbers = IntStream.range(0, objects)
                .mapToObj(BigDecimal::new)
                .toList();

        Sleeper.sleep(duration);

        long durationMillis = System.currentTimeMillis() - startTime;
        return new BurnerMessage(
                "This churner object creation took [%d] ms for [%d] BigDecimals and [%s] delay."
                        .formatted(durationMillis, numbers.size(), duration),
                props.name(), durationMillis);
    }
}
