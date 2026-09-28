package nl.stokpop.scramjet.controller;

import io.swagger.v3.oas.annotations.Operation;
import nl.stokpop.scramjet.ScramjetProperties;
import nl.stokpop.scramjet.domain.BurnerMessage;
import nl.stokpop.scramjet.domain.ParallelInfo;
import nl.stokpop.scramjet.util.Calculator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

/**
 * Execute some tasks using the common fork join pool.
 * See what happens when it gets busy.
 */
@RestController
public class GoParallel {

    private final ScramjetProperties props;

    public GoParallel(ScramjetProperties props) {
        this.props = props;
    }

    /**
     * Show current information of the common fork join pool.
     */
    @Operation(summary = "Show current information of the common fork join pool.")
    @GetMapping("/parallel-info")
    public ParallelInfo parallelInfo() {
        ForkJoinPool pool = ForkJoinPool.commonPool();
        return new ParallelInfo(
                "ForkJoinPool.commonPool()",
                pool.getActiveThreadCount(),
                pool.getParallelism(),
                pool.getAsyncMode(),
                pool.getPoolSize(),
                pool.getQueuedSubmissionCount(),
                pool.getQueuedTaskCount(),
                pool.getRunningThreadCount(),
                pool.getStealCount());
    }

    /**
     * Calculate the sum of prime numbers, with some additional delay,
     * using a parallel stream (common fork join pool).
     */
    @Operation(summary = "Calculate the sum of prime numbers, with some additional delay, using parallel stream (common fork join pool).")
    @GetMapping("/parallel")
    public BurnerMessage goParallel(
            @RequestParam(value = "primeDelayMillis", defaultValue = "2") int primeDelayMillis,
            @RequestParam(value = "maxPrime", defaultValue = "10000") int maxPrime) {
        long startTime = System.currentTimeMillis();

        List<Long> numbers = Collections.synchronizedList(
                LongStream.range(1, maxPrime)
                        .boxed()
                        .collect(Collectors.toCollection(() -> new ArrayList<>(maxPrime))));

        long sum = numbers.parallelStream()
                .filter(n -> Calculator.isPrime(n, primeDelayMillis))
                .reduce(0L, Long::sum);

        long durationMillis = System.currentTimeMillis() - startTime;
        return new BurnerMessage("The sum of prime numbers up to %d: %d".formatted(maxPrime, sum), props.name(), durationMillis);
    }

    /**
     * Calculate the sum of prime numbers, with some additional delay, using a regular (serial) stream.
     */
    @Operation(summary = "Calculate the sum of prime numbers, with some additional delay, using a regular (serial) stream.")
    @GetMapping("/serial-stream")
    public BurnerMessage goSerialStream(
            @RequestParam(value = "primeDelayMillis", defaultValue = "5") int primeDelayMillis,
            @RequestParam(value = "maxPrime", defaultValue = "10000") long maxPrime) {
        long startTime = System.currentTimeMillis();

        long sum = LongStream.range(1, maxPrime)
                .filter(n -> Calculator.isPrime(n, primeDelayMillis))
                .reduce(0L, Long::sum);

        long durationMillis = System.currentTimeMillis() - startTime;
        return new BurnerMessage("The sum of prime numbers up to %d: %d".formatted(maxPrime, sum), props.name(), durationMillis);
    }
}
