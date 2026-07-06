package nl.stokpop.scramjet.controller;

import io.swagger.v3.oas.annotations.Operation;
import nl.stokpop.scramjet.ScramjetProperties;
import nl.stokpop.scramjet.domain.BurnerMessage;
import nl.stokpop.scramjet.error.OutOfResourcesException;
import nl.stokpop.scramjet.util.Sleeper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@RestController
public class Delay {

    public static final int SEMAPHORE_WAIT_TIMEOUT_MS = 5;

    private final Semaphore semaphore;
    private final ScramjetProperties props;

    public Delay(final ScramjetProperties props) {
        this.props = props;
        this.semaphore = new Semaphore(props.delayCallLimit());
    }

    /**
     * Simple sleep in request thread for 'duration' milliseconds (or ISO-8601 duration).
     */
    @Operation(summary = "The delay call does a simple java sleep in request thread for 'duration' milliseconds.")
    @GetMapping(value = "/delay", produces = "application/json")
    public BurnerMessage delay(@RequestParam(value = "duration", defaultValue = "100") String duration) {
        return sleep(duration);
    }

    /**
     * Same sleep, but only a limited number of concurrent calls allowed via Semaphore.
     */
    @Operation(summary = "The delay call does a simple java sleep in request thread for 'duration' milliseconds. Use limited number of threads via Semaphore.")
    @GetMapping(value = "/delay-limited", produces = "application/json")
    public BurnerMessage delayLimited(@RequestParam(value = "duration", defaultValue = "100") String duration) {
        try {
            final boolean allowedToSleep = semaphore.tryAcquire(SEMAPHORE_WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            try {
                if (allowedToSleep) {
                    return sleep(duration);
                } else {
                    throw new OutOfResourcesException("Sorry, all reserved resources - threads - are in use.");
                }
            } finally {
                if (allowedToSleep) {
                    semaphore.release();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OutOfResourcesException("Sorry, got interrupted waiting for thread to sleep on.");
        }
    }

    private BurnerMessage sleep(String duration) {
        long startTime = System.currentTimeMillis();
        Sleeper.sleep(duration);
        long durationMillis = System.currentTimeMillis() - startTime;
        return new BurnerMessage("This was a delay of %s".formatted(duration), props.name(), durationMillis);
    }
}
