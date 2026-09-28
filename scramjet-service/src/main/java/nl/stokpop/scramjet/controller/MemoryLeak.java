package nl.stokpop.scramjet.controller;

import io.swagger.v3.oas.annotations.Operation;
import nl.stokpop.scramjet.ScramjetProperties;
import nl.stokpop.scramjet.domain.BurnerMessage;
import nl.stokpop.scramjet.domain.MusicMachine;
import nl.stokpop.scramjet.domain.MusicMachineMemory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;

@RestController
public class MemoryLeak {

    private static final Logger log = LoggerFactory.getLogger(MemoryLeak.class);

    private final ScramjetProperties props;

    public MemoryLeak(final ScramjetProperties props) {
        this.props = props;
    }

    /**
     * Simulate a memory leak: objects added here are never garbage collected.
     */
    @Operation(summary = "Simulate a memory leak.")
    @GetMapping("/memory/grow")
    public BurnerMessage memoryGrow(
            @RequestParam(value = "objects", defaultValue = "10") int objects,
            @RequestParam(value = "items", defaultValue = "9") int items,
            @RequestParam(value = "length", defaultValue = "100") int length) {

        long startTime = System.currentTimeMillis();

        log.info("Add [{}] objects with [{}] items of length [{}] to current set of [{}] objects.",
                objects, items, length, MusicMachine.getMusicMachineMemories().size());

        for (int i = 0; i < objects; i++) {
            MusicMachine.getMusicMachineMemories().add(new MusicMachineMemory(items, length));
        }
        long durationMillis = System.currentTimeMillis() - startTime;
        return new BurnerMessage(createMemoryMessage(), props.name(), durationMillis);
    }

    @Operation(summary = "Clear the memory leak.")
    @GetMapping("/memory/clear")
    public BurnerMessage memoryClear() {
        log.info("Clear object list with size [{}].", MusicMachine.getMusicMachineMemories().size());
        long startTime = System.currentTimeMillis();
        MusicMachine.setMusicMachineMemories(new ArrayList<>());
        long durationMillis = System.currentTimeMillis() - startTime;
        return new BurnerMessage(createMemoryMessage(), props.name(), durationMillis);
    }

    private String createMemoryMessage() {
        int totalItems = MusicMachine.getMusicMachineMemories().stream().mapToInt(MusicMachineMemory::size).sum();
        return "There are now %d objects, containing a total of %d items."
                .formatted(MusicMachine.getMusicMachineMemories().size(), totalItems);
    }
}
