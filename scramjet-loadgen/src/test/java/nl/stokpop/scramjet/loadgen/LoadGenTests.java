package nl.stokpop.scramjet.loadgen;

import com.sun.net.httpserver.HttpServer;
import nl.stokpop.scramjet.loadgen.Statistics.StepStats;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LoadGenTests {

    @Test
    void scenarioSteps() {
        assertEquals(List.of("delay", "matrix"), stepNames(Options.parse()));
        assertEquals(List.of("churn", "delay"), stepNames(Options.parse("--scenario", "churn")));
        assertEquals(List.of("leak", "delay"), stepNames(Options.parse("--scenario", "leak")));
    }

    @Test
    void scenarioPathsUseOptions() {
        assertEquals("/memory/grow?objects=1&length=100&items=3",
                LoadGen.scenario(Options.parse("--scenario", "leak", "--leak-items", "3")).getFirst().path());
        assertEquals("/memory/churn?duration=0&objects=500",
                LoadGen.scenario(Options.parse("--scenario", "churn", "--churn-objects", "500")).getFirst().path());
        assertEquals("/delay?duration=7",
                LoadGen.scenario(Options.parse("--delay-ms", "7")).getFirst().path());
    }

    @Test
    void runsAtRateAndNotifiesReport() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/ok", exchange -> {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.createContext("/fail", exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.start();
        AtomicLong started = new AtomicLong();
        AtomicInteger samples = new AtomicInteger();
        AtomicInteger finished = new AtomicInteger();
        Report report = new Report() {
            @Override
            public void started(Options options, List<LoadGen.Step> scenario, long totalRequests) {
                started.set(totalRequests);
            }

            @Override
            public void sample(Sample sample) {
                samples.incrementAndGet();
            }

            @Override
            public void finished(Options options, List<LoadGen.Step> scenario, Results results) {
                finished.incrementAndGet();
            }
        };
        try {
            Options options = Options.parse("--url", "http://localhost:" + server.getAddress().getPort(), "--duration", "1s", "--rate", "20");
            Results results = LoadGen.run(options, List.of(new LoadGen.Step("ok", "/ok"), new LoadGen.Step("fail", "/fail")), report);

            List<StepStats> summary = Statistics.summary(results);
            assertEquals(10, summary.get(0).ok());
            assertEquals(10, summary.get(1).failed());
            assertEquals(20, started.get());
            assertEquals(20, samples.get());
            assertEquals(1, finished.get());
        } finally {
            server.stop(0);
        }
    }

    private static List<String> stepNames(Options options) {
        return LoadGen.scenario(options).stream().map(LoadGen.Step::name).toList();
    }
}
