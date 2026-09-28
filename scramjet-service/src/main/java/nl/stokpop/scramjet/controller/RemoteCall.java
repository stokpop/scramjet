package nl.stokpop.scramjet.controller;

import io.swagger.v3.oas.annotations.Operation;
import nl.stokpop.scramjet.ScramjetException;
import nl.stokpop.scramjet.ScramjetProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Call this (or another) scramjet instance over http. Useful to simulate
 * fan-out traffic and downstream latency. The 'type' request param is accepted
 * for Afterburner API compatibility, but all calls use the JDK http client.
 */
@RestController
public class RemoteCall {

    private static final Logger log = LoggerFactory.getLogger(RemoteCall.class);

    private final RestClient restClient;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public RemoteCall(ScramjetProperties props) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder()
                .baseUrl(props.remoteCallBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * Call one remote service.
     */
    @Operation(summary = "Call one remote service.")
    @GetMapping("remote/call")
    public String remoteCall(
            @RequestParam(value = "path", defaultValue = "/delay") String path,
            @RequestParam(value = "type", defaultValue = "httpclient") String type) {
        return executeCall(path);
    }

    /**
     * Call many remote services in parallel using CompletableFutures on virtual threads.
     */
    @Operation(summary = "Call many remote services in parallel using CompletableFutures on virtual threads.")
    @GetMapping("remote/call-many")
    public String remoteCallMany(
            @RequestParam(value = "path", defaultValue = "/delay?duration=33") String path,
            @RequestParam(value = "type", defaultValue = "httpclient") String type,
            @RequestParam(value = "count", defaultValue = "3") int count) {

        List<CompletableFuture<String>> results = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            results.add(CompletableFuture.supplyAsync(() -> executeCall(path), executor));
        }
        return results.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.joining(","));
    }

    private String executeCall(String path) {
        log.info("Execute remote call to [{}]", path);
        try {
            return restClient.get().uri(path).retrieve().body(String.class);
        } catch (Exception e) {
            throw new ScramjetException("Execute call failed for " + path, e);
        }
    }
}
