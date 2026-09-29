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

        run(options, scenario(options), Report.forName(options.report()));
    }

    static List<Step> scenario(Options options) {
        Step delay = new Step("delay", "/delay?duration=" + options.delayMillis());
        return switch (options.scenario()) {
            case "churn" -> List.of(
                    new Step("churn", "/memory/churn?duration=0&objects=" + options.churnObjects()),
                    delay);
            case "leak" -> List.of(
                    new Step("leak", "/memory/grow?objects=1&length=100&items=" + options.leakItems()),
                    delay);
            case "native" -> List.of(
                    new Step("native", "/memory/native/churn?segments=" + options.nativeSegments()
                            + "&size=" + options.nativeKb() * 1024 + "&duration=" + options.delayMillis()),
                    delay);
            default -> List.of(
                    delay,
                    new Step("matrix", "/cpu/magic-identity-check?matrixSize=" + options.matrixSize()));
        };
    }

    static Results run(Options options, List<Step> scenario, Report report) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5));
        if (options.insecure()) {
            // HttpClient has no builder option for this; the property must be set before the first client is built
            System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");
            builder.sslContext(trustAllSslContext());
        }
        HttpClient client = builder.build();

        long totalRequests = Math.round(options.rate() * options.duration().toNanos() / 1e9);
        long intervalNanos = Math.round(1e9 / options.rate());

        Results results = new Results(scenario.stream().map(Step::name).toList());
        results.addListener(report::sample);
        report.started(options, scenario, totalRequests);

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
                long offset = scheduledStart - start;
                executor.submit(() -> results.add(call(client, request, step, scheduledStart, offset)));
            }
        }
        results.finish(System.nanoTime() - start);
        report.finished(options, scenario, results);
        return results;
    }

    private static Sample call(HttpClient client, HttpRequest request, Step step, long scheduledStart, long offset) {
        String failure;
        try {
            int status = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            failure = status >= 200 && status < 300 ? null : "HTTP " + status;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            failure = e.getClass().getSimpleName();
        } catch (Exception e) {
            failure = e.getClass().getSimpleName();
        }
        return new Sample(step.name(), offset, System.nanoTime() - scheduledStart, failure);
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
