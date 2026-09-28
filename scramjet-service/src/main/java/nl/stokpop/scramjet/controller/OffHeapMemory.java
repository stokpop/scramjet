package nl.stokpop.scramjet.controller;

import io.swagger.v3.oas.annotations.Operation;
import nl.stokpop.scramjet.ScramjetProperties;
import nl.stokpop.scramjet.domain.BurnerMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Off-heap memory growth, invisible to the java heap: use it to slowly run
 * into an off-heap OutOfMemoryError or a container OOM kill while heap
 * metrics look perfectly healthy.
 *
 * Two mechanisms:
 * - direct ByteBuffers: counted against -XX:MaxDirectMemorySize (defaults to
 *   max heap size), OOMs with "Direct buffer memory" when exceeded
 * - foreign memory segments (java.lang.foreign Arena): raw native
 *   allocations, not limited by MaxDirectMemorySize, grow until malloc
 *   fails or the OS/container kills the process
 *
 * Every 4K page is touched after allocation so the memory is actually
 * committed and resident (RSS), not just reserved.
 */
@RestController
public class OffHeapMemory {

    private static final Logger log = LoggerFactory.getLogger(OffHeapMemory.class);
    private static final int PAGE_SIZE = 4096;

    private static final List<ByteBuffer> directBuffers = Collections.synchronizedList(new ArrayList<>());
    private static final AtomicLong directBytes = new AtomicLong();

    private static final List<Arena> arenas = Collections.synchronizedList(new ArrayList<>());
    private static final AtomicLong segmentBytes = new AtomicLong();

    private final ScramjetProperties props;

    public OffHeapMemory(final ScramjetProperties props) {
        this.props = props;
    }

    @Operation(summary = "Grow off-heap memory with direct ByteBuffers. Limited by -XX:MaxDirectMemorySize (default: max heap size).")
    @GetMapping("/memory/direct/grow")
    public BurnerMessage directGrow(
            @RequestParam(value = "buffers", defaultValue = "10") int buffers,
            @RequestParam(value = "size", defaultValue = "1048576") int size) {

        long startTime = System.currentTimeMillis();

        log.info("Allocate [{}] direct ByteBuffers of [{}] bytes each, current total [{}] bytes.",
                buffers, size, directBytes.get());

        for (int i = 0; i < buffers; i++) {
            ByteBuffer buffer = ByteBuffer.allocateDirect(size);
            touchPages(buffer);
            directBuffers.add(buffer);
            directBytes.addAndGet(size);
        }

        long durationMillis = System.currentTimeMillis() - startTime;
        return new BurnerMessage(createDirectMessage(), props.name(), durationMillis);
    }

    @Operation(summary = "Clear the direct ByteBuffer hoard. Memory is freed when the buffers are garbage collected.")
    @GetMapping("/memory/direct/clear")
    public BurnerMessage directClear() {
        log.info("Clear [{}] direct ByteBuffers of [{}] total bytes.", directBuffers.size(), directBytes.get());
        long startTime = System.currentTimeMillis();
        directBuffers.clear();
        directBytes.set(0);
        long durationMillis = System.currentTimeMillis() - startTime;
        return new BurnerMessage(createDirectMessage(), props.name(), durationMillis);
    }

    @Operation(summary = "Grow native memory with foreign memory segments (Arena). Not limited by MaxDirectMemorySize: grows until native allocation fails.")
    @GetMapping("/memory/segment/grow")
    public BurnerMessage segmentGrow(
            @RequestParam(value = "segments", defaultValue = "10") int segments,
            @RequestParam(value = "size", defaultValue = "1048576") long size) {

        long startTime = System.currentTimeMillis();

        log.info("Allocate [{}] memory segments of [{}] bytes each, current total [{}] bytes.",
                segments, size, segmentBytes.get());

        for (int i = 0; i < segments; i++) {
            Arena arena = Arena.ofShared();
            MemorySegment segment = arena.allocate(size);
            touchPages(segment, size);
            arenas.add(arena);
            segmentBytes.addAndGet(size);
        }

        long durationMillis = System.currentTimeMillis() - startTime;
        return new BurnerMessage(createSegmentMessage(), props.name(), durationMillis);
    }

    @Operation(summary = "Close all arenas: native segment memory is freed immediately.")
    @GetMapping("/memory/segment/clear")
    public BurnerMessage segmentClear() {
        log.info("Close [{}] arenas of [{}] total bytes.", arenas.size(), segmentBytes.get());
        long startTime = System.currentTimeMillis();
        synchronized (arenas) {
            arenas.forEach(Arena::close);
            arenas.clear();
        }
        segmentBytes.set(0);
        long durationMillis = System.currentTimeMillis() - startTime;
        return new BurnerMessage(createSegmentMessage(), props.name(), durationMillis);
    }

    private static void touchPages(ByteBuffer buffer) {
        for (int pos = 0; pos < buffer.capacity(); pos += PAGE_SIZE) {
            buffer.put(pos, (byte) 0x53);
        }
    }

    private static void touchPages(MemorySegment segment, long size) {
        for (long pos = 0; pos < size; pos += PAGE_SIZE) {
            segment.set(java.lang.foreign.ValueLayout.JAVA_BYTE, pos, (byte) 0x53);
        }
    }

    private String createDirectMessage() {
        return "There are now %d direct ByteBuffers, holding a total of %d MB off-heap."
                .formatted(directBuffers.size(), directBytes.get() / (1024 * 1024));
    }

    private String createSegmentMessage() {
        return "There are now %d memory segments, holding a total of %d MB native memory."
                .formatted(arenas.size(), segmentBytes.get() / (1024 * 1024));
    }
}
