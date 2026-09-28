# Scramjet

A lean, mean performance-simulation service. Scramjet is a fresh, minimal reimplementation of
[perfana/afterburner](https://github.com/perfana/afterburner) on Spring Boot 4.1 and Java 25,
with metrics exported over OTLP.

Use it as a target application for load tests: it can burn CPU, hog or churn memory, hold locks,
sleep, log abundantly, fail on demand and fan out remote calls — all tunable per request.

## Modules

* `scramjet-service` — the Spring Boot service
* `scramjet-loadgen` — a minimal load generator, plain Java with virtual threads, no dependencies

## Requirements

* Java 25

## Run

```shell
./mvnw -pl scramjet-service spring-boot:run
```

The service listens on port 8080. API documentation (OpenAPI via springdoc) is served at
`http://localhost:8080/swagger-ui.html`.

## API

The HTTP API is compatible with the afterburner core endpoints: same paths, request parameters
and JSON response shape (`message`, `name`, `durationInMillis`).

| Endpoint | What it does |
|---|---|
| `GET /delay?duration=100` | Sleep in the request thread (millis or ISO-8601, e.g. `PT0.5S`) |
| `GET /delay-limited?duration=100` | Same, but concurrency-limited by a semaphore (`scramjet.delay-call-limit`); rejects with 503 when exhausted |
| `GET /cpu/magic-identity-check?matrixSize=10` | Burn CPU with matrix multiplication |
| `GET /memory/grow?objects=10&items=9&length=100` | Simulate a memory leak (objects are retained forever) |
| `GET /memory/clear` | Clear the leak |
| `GET /memory/churn?objects=181&duration=100` | High object churn to stress young-gen GC |
| `GET /memory/direct/grow?buffers=10&size=1048576` | Off-heap leak via direct ByteBuffers (hits `-XX:MaxDirectMemorySize`) |
| `GET /memory/direct/clear` | Drop the direct buffers (freed on GC) |
| `GET /memory/segment/grow?segments=10&size=1048576` | Native leak via foreign memory segments (Arena), grows until malloc fails or the container is OOM-killed |
| `GET /memory/segment/clear` | Close all arenas, native memory freed immediately |
| `GET /flaky?flakiness=50&maxRandomDelay=-1` | Fails `flakiness` out of 100 calls |
| `GET /parallel?primeDelayMillis=2&maxPrime=10000` | Prime sums on the common fork join pool |
| `GET /serial-stream?primeDelayMillis=5&maxPrime=10000` | Same, single threaded |
| `GET /parallel-info` | Common fork join pool stats |
| `GET /one-lock?duration=100` | All requests contend on one lock |
| `GET /log-some?logLines=10&logSize=1000` | Log a lot (also `POST` with a body) |
| `GET /mind-my-business?duration=5` | Sleep with start/end log lines |
| `GET /system-info` | JVM memory, processors and threads |
| `GET /remote/call?path=/delay` | Call a downstream service (itself by default) |
| `GET /remote/call-many?path=/delay?duration=33&count=3` | Parallel downstream calls on virtual threads |

Not ported from afterburner (by design, to stay lean): database/mybatis endpoints, basket shop,
file upload/download, tcp connect, resilience4j retry/circuit-breaker endpoints and spring-security.

To see an off-heap OOM quickly, cap direct memory and grow:

```shell
java -Xmx256m -XX:MaxDirectMemorySize=128m -jar scramjet-service/target/scramjet-service-*.jar
# then repeat: curl "localhost:8080/memory/direct/grow?buffers=16&size=1048576"
# heap stays healthy; after ~128 MB: OutOfMemoryError: Direct buffer memory
```

`/memory/segment/grow` ignores `MaxDirectMemorySize` and keeps allocating native
memory until malloc fails or the OS/container kills the process — the classic
"RSS grows but heap looks fine" incident.

## Metrics over OTLP

Metrics are exported with Micrometer's OTLP registry to an OpenTelemetry collector,
every 10 seconds, including `http.server.requests` latency histograms and JVM metrics,
tagged with `service.name=scramjet`.

Export is **off by default**, so running without a collector gives no log noise.
Switch it on with `OTLP_ENABLED=true`; the default target is `http://localhost:4318/v1/metrics`:

```shell
OTLP_ENABLED=true ./mvnw -pl scramjet-service spring-boot:run
OTLP_ENABLED=true MANAGEMENT_OTLP_METRICS_EXPORT_URL=http://collector:4318/v1/metrics java -jar scramjet-service/target/scramjet-service-*.jar
```

At startup a log line states whether export is on and where it sends to. Once enabled, an
unreachable collector is logged as a one-line warning (no stacktrace) on every export, on
purpose: metrics should not go missing silently during a load test.

A quick local collector to see the metrics flow:

```shell
docker run --rm -p 4318:4318 otel/opentelemetry-collector
```

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `scramjet.name` | `scramjet` | Name reported in responses |
| `scramjet.delay-call-limit` | `10` | Max concurrent `/delay-limited` calls |
| `scramjet.remote-call-base-url` | `http://localhost:8080` | Base url for `/remote/call*` |
| `spring.threads.virtual.enabled` | `false` | Serve requests on virtual threads |

## Load generator

Open-loop load: requests start at a fixed rate on virtual threads, whatever the response
times, so a slow service builds up concurrency instead of quietly getting less load.
The scenario alternates `/delay` and `/cpu/magic-identity-check` calls.

```shell
./mvnw -pl scramjet-loadgen package
java -jar scramjet-loadgen/target/scramjet-loadgen.jar --url http://localhost:8080 --duration 30s --rate 20
```

| Option | Default | Meaning |
|---|---|---|
| `--url` | `http://localhost:8080` | Base url of the service |
| `--duration` | `30s` | How long to run: `30s`, `2m`, `PT1M` or plain seconds |
| `--rate` | `10` | Requests started per second |
| `--timeout` | `10s` | Per request timeout |
| `--delay-ms` | `100` | `duration` param for `/delay` |
| `--matrix-size` | `100` | `matrixSize` param for `/cpu/magic-identity-check` |
| `--insecure` | off | Skip TLS certificate and host name verification, for test environments with self-signed certificates only |

At the end it reports per step the total, successes, failures, error percentage, throughput
and response times (min, p50, p90, p95, p99, max) in milliseconds, plus a breakdown of
failure reasons (HTTP status or exception). Response times are measured from the
*scheduled* start of each request, so they include any time a request had to wait to be
sent (no coordinated omission).

## Build

```shell
./mvnw verify
```
