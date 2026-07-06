package nl.stokpop.scramjet.controller;

import io.swagger.v3.oas.annotations.Operation;
import nl.stokpop.scramjet.ScramjetProperties;
import nl.stokpop.scramjet.domain.BurnerMessage;
import nl.stokpop.scramjet.util.Sleeper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MindMyBusiness {

    private static final Logger log = LoggerFactory.getLogger(MindMyBusiness.class);

    private final ScramjetProperties props;

    public MindMyBusiness(final ScramjetProperties props) {
        this.props = props;
    }

    /**
     * Mind my business for 'duration' milliseconds.
     */
    @Operation(summary = "Mind my business for 'duration' milliseconds.")
    @GetMapping(value = "/mind-my-business", produces = "application/json")
    public BurnerMessage mindMyBusiness(@RequestParam(value = "duration", defaultValue = "5") String duration) {
        long startTime = System.currentTimeMillis();
        try {
            log.info("mindMyBusiness start");
            Sleeper.sleep(duration);
            long durationMillis = System.currentTimeMillis() - startTime;
            return new BurnerMessage("Mind my business should take %s milliseconds.".formatted(duration), props.name(), durationMillis);
        } finally {
            log.info("mindMyBusiness end: {} ms", System.currentTimeMillis() - startTime);
        }
    }
}
