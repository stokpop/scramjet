package nl.stokpop.scramjet.loadgen;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.LockSupport;

/**
 * Minimal open-loop load generator for scramjet.
 *
 * Requests are started at a fixed rate on virtual threads, independent of how
 * fast responses come back, so a slow server builds up concurrency instead of
 * silently lowering the load. Response times are measured from the moment a
 * request was scheduled to start, which avoids coordinated omission.
 */
public final class LoadGen {

    private LoadGen() {
    }

    public static void main(String[] args) {
        Options options;
        try {
            options = Options.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.err.println();
            System.err.println(Options.USAGE);
            System.exit(2);
            return;
        }
        if (options.help()) {
            System.out.println(Options.USAGE);
            return;
        }

        List<Step> scenario = List.of(
                new Step("delay", "/delay?duration=" + options.delayMillis()),
                new Step("matrix", "/cpu/magic-identity-check?matrixSize=" + options.matrixSize()));

        Results results = run(options, scenario);
        System.out.println(results.report());
    }

    static Results run(Options options, List<Step> scenario) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5));
        if (options.insecure()) {
            // HttpClient has no builder option for this; the property must be set before the first client is built
            System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");
            builder.sslContext(trustAllSslContext());
            System.out.println("WARNING: --insecure: TLS certificates and host names are NOT verified");
        }
        HttpClient client = builder.build();

        long totalRequests = Math.round(options.rate() * options.duration().toNanos() / 1e9);
        long intervalNanos = Math.round(1e9 / options.rate());

        System.out.printf("Running %d requests at %.1f req/s for %s against %s%n",
                totalRequests, options.rate(), options.duration(), options.baseUrl());

        Results results = new Results(scenario.stream().map(Step::name).toList());

        long start = System.nanoTime();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (long i = 0; i < totalRequests; i++) {
                long scheduledStart = start + i * intervalNanos;
                waitUntil(scheduledStart);
                Step step = scenario.get((int) (i % scenario.size()));
                HttpRequest request = HttpRequest.newBuilder(URI.create(options.baseUrl() + step.path()))
                        .timeout(options.timeout())
                        .GET()
                        .build();
                executor.submit(() -> call(client, request, step, scheduledStart, results));
            }
        }
        results.finish(System.nanoTime() - start);
        return results;
    }

    private static void call(HttpClient client, HttpRequest request, Step step, long scheduledStart, Results results) {
        try {
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            long responseTime = System.nanoTime() - scheduledStart;
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                results.success(step.name(), responseTime);
            } else {
                results.failure(step.name(), responseTime, "HTTP " + status);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            results.failure(step.name(), System.nanoTime() - scheduledStart, e.getClass().getSimpleName());
        } catch (Exception e) {
            results.failure(step.name(), System.nanoTime() - scheduledStart, e.getClass().getSimpleName());
        }
    }

    private static SSLContext trustAllSslContext() {
        TrustManager trustAll = new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{trustAll}, new SecureRandom());
            return sslContext;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cannot create trust-all SSLContext", e);
        }
    }

    private static void waitUntil(long nanoTime) {
        long remaining;
        while ((remaining = nanoTime - System.nanoTime()) > 0) {
            LockSupport.parkNanos(remaining);
        }
    }

    record Step(String name, String path) {
    }
}
