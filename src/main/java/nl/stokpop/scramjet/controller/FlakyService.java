package nl.stokpop.scramjet.controller;

import io.swagger.v3.oas.annotations.Operation;
import nl.stokpop.scramjet.ScramjetException;
import nl.stokpop.scramjet.ScramjetProperties;
import nl.stokpop.scramjet.domain.BurnerMessage;
import nl.stokpop.scramjet.util.Sleeper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Random;

@RestController
public class FlakyService {

    private static final Logger log = LoggerFactory.getLogger(FlakyService.class);

    private final ScramjetProperties props;
    private final Random random = new Random();

    public FlakyService(final ScramjetProperties props) {
        this.props = props;
    }

    /**
     * The flaky call fails 'flakiness' times out of 100 calls. If maxRandomDelay is -1
     * a 20 millisecond sleep is done, otherwise a random sleep up to maxRandomDelay millis.
     */
    @Operation(summary = "The flaky call fails most of the time.")
    @GetMapping(value = "/flaky", produces = "application/json")
    public BurnerMessage flaky(
            @RequestParam(value = "flakiness", defaultValue = "50") int flakiness,
            @RequestParam(value = "maxRandomDelay", defaultValue = "-1") int maxRandomDelay) {

        log.info("Flaky service called with flakiness percentage {} and max random delay in ms {}", flakiness, maxRandomDelay);
        long startTime = System.currentTimeMillis();

        long sleepTime = randomSleep(maxRandomDelay);

        if ((random.nextInt(100) + 1) < flakiness) {
            throw new ScramjetException("Sorry, flaky call failed after " + sleepTime + " milliseconds.");
        }
        long durationMillis = System.currentTimeMillis() - startTime;
        return new BurnerMessage("Yes, flaky call succeeds", props.name(), durationMillis);
    }

    private long randomSleep(int maxRandomDelay) {
        long sleepTime = maxRandomDelay == -1 ? 20 : random.nextInt(maxRandomDelay) + 1;
        Sleeper.sleep(Duration.ofMillis(sleepTime));
        return sleepTime;
    }
}
