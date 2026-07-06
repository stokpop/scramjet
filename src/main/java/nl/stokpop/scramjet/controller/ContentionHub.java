package nl.stokpop.scramjet.controller;

import io.swagger.v3.oas.annotations.Operation;
import nl.stokpop.scramjet.ScramjetProperties;
import nl.stokpop.scramjet.domain.BurnerMessage;
import nl.stokpop.scramjet.util.Sleeper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ContentionHub {

    public static final Object lock = new Object();

    private final ScramjetProperties props;

    public ContentionHub(final ScramjetProperties props) {
        this.props = props;
    }

    /**
     * One lock to hold all threads.
     */
    @Operation(summary = "One lock to hold all threads.")
    @GetMapping(value = "/one-lock", produces = "application/json")
    public BurnerMessage oneLock(@RequestParam(value = "duration", defaultValue = "100") String duration) {
        long startTime = System.currentTimeMillis();
        synchronized (lock) {
            Sleeper.sleep(duration);
        }
        long lockDuration = System.currentTimeMillis() - startTime;
        return new BurnerMessage("Survived lock wait", props.name(), lockDuration);
    }
}
