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

Not ported from afterburner (by design, to stay lean): database/mybatis endpoints, basket shop,
file upload/download, tcp connect, resilience4j retry/circuit-breaker endpoints and spring-security.

### Latency and contention

| Endpoint | What it does |
|---|---|
| `GET /delay?duration=100` | Sleep in the request thread (millis or ISO-8601, e.g. `PT0.5S`) |
| `GET /delay-limited?duration=100` | Same, but concurrency-limited by a semaphore (`scramjet.delay-call-limit`); rejects with 503 when exhausted |
| `GET /mind-my-business?duration=5` | Sleep with start/end log lines |
| `GET /one-lock?duration=100` | All requests contend on one lock |

### CPU and thread pools

| Endpoint | What it does |
|---|---|
| `GET /cpu/magic-identity-check?matrixSize=10` | Burn CPU with matrix multiplication |
| `GET /parallel?primeDelayMillis=2&maxPrime=10000` | Prime sums on the common fork join pool |
| `GET /serial-stream?primeDelayMillis=5&maxPrime=10000` | Same, single threaded |
| `GET /parallel-info` | Common fork join pool stats |

### Heap memory

| Endpoint | What it does |
|---|---|
| `GET /memory/churn?objects=181&duration=100` | High object churn: short-lived objects that stress young-gen GC |
| `GET /memory/grow?objects=10&items=9&length=100` | Memory leak: objects are retained forever (~1.8 KB per item) |
| `GET /memory/clear` | Clear the leak |

### Off-heap memory

| Endpoint | What it does |
|---|---|
| `GET /memory/direct/grow?buffers=10&size=1048576` | Off-heap leak via direct ByteBuffers (hits `-XX:MaxDirectMemorySize`) |
| `GET /memory/direct/clear` | Drop the direct buffers (freed on GC) |
| `GET /memory/segment/grow?segments=10&size=1048576` | Native leak via foreign memory segments (Arena), grows until malloc fails or the container is OOM-killed |
| `GET /memory/segment/clear` | Close all arenas, native memory freed immediately |
| `GET /memory/native/churn?segments=20&size=65536&duration=100` | Native churn: malloc segments, hold them for `duration`, free them, all within the request |

To see an off-heap OOM quickly, cap direct memory and grow:

```shell
java -Xmx256m -XX:MaxDirectMemorySize=128m -jar scramjet-service/target/scramjet-service-*.jar
# then repeat: curl "localhost:8080/memory/direct/grow?buffers=16&size=1048576"
# heap stays healthy; after ~128 MB: OutOfMemoryError: Direct buffer memory
```

`/memory/segment/grow` ignores `MaxDirectMemorySize` and keeps allocating native
memory until malloc fails or the OS/container kills the process — the classic
"RSS grows but heap looks fine" incident.

### Errors and logging

| Endpoint | What it does |
|---|---|
| `GET /flaky?flakiness=50&maxRandomDelay=-1` | Fails `flakiness` out of 100 calls |
| `GET /log-some?logLines=10&logSize=1000` | Log a lot (also `POST` with a body) |

### Remote calls

| Endpoint | What it does |
|---|---|
| `GET /remote/call?path=/delay` | Call a downstream service (itself by default) |
| `GET /remote/call-many?path=/delay?duration=33&count=3` | Parallel downstream calls on virtual threads |

### Info

| Endpoint | What it does |
|---|---|
| `GET /system-info` | JVM memory, processors and threads |

## Experiments

* [MALLOC_ARENA_MAX and TrimNativeHeapInterval](experiments/malloc-arena-max/README.md): effect of
  `MALLOC_ARENA_MAX=2` and `-XX:TrimNativeHeapInterval` on native memory retention, fragmentation and
  malloc lock contention, measured with NMT, JFR, `malloc_info`, `malloc_trim` and `/proc`.

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

```shell
./mvnw -pl scramjet-loadgen package
java -jar scramjet-loadgen/target/scramjet-loadgen.jar --url http://localhost:8080 --duration 30s --rate 20
```

Each scenario alternates two calls. The churn and leak scenarios pair their memory call
with `/delay`, so growing GC pressure shows up in the response times of otherwise cheap
requests.

| Scenario | Calls | What to expect |
|---|---|---|
| `basic` | `/delay`, `/cpu/magic-identity-check` | Steady latency and CPU load |
| `churn` | `/memory/churn`, `/delay` | High allocation rate, frequent young-gen GCs, heap stays flat |
| `leak` | `/memory/grow`, `/delay` | Heap fills up, GC works harder and harder, then timeouts and `OutOfMemoryError` |
| `native` | `/memory/native/churn`, `/delay` | Native malloc/free on many request threads: glibc malloc arenas, RSS above what the JVM tracks |

With defaults the leak retains ~176 KB per leak call. Against a service started with
`-Xmx256m`, `--scenario leak --rate 20` runs into `OutOfMemoryError` after about two
minutes; lower `--rate` or `--leak-items` for a slower leak. `/memory/clear` releases it.

| Option | Default | Meaning |
|---|---|---|
| `--url` | `http://localhost:8080` | Base url of the service |
| `--duration` | `30s` | How long to run: `30s`, `2m`, `PT1M` or plain seconds |
| `--rate` | `10` | Requests started per second |
| `--timeout` | `10s` | Per request timeout |
| `--scenario` | `basic` | `basic`, `churn`, `leak` or `native` |
| `--delay-ms` | `100` | `duration` param for `/delay` |
| `--matrix-size` | `100` | `matrixSize` param for `/cpu/magic-identity-check` |
| `--churn-objects` | `100000` | Short-lived BigDecimals created per `/memory/churn` call |
| `--leak-items` | `100` | Music scores (~1.8 KB each) retained per `/memory/grow` call |
| `--native-segments` | `20` | Native segments malloc'ed per `/memory/native/churn` call |
| `--native-kb` | `64` | Size per native segment; keep it below glibc's 128 KB mmap threshold to hit the malloc arenas |
| `--report` | `ascii` | `ascii`: report at the end; `live`: also a row per tick during the run |
| `--insecure` | off | Skip TLS certificate and host name verification, for test environments with self-signed certificates only |

At the end it reports per step the total, successes, failures, error percentage, throughput
and response times (min, p50, p90, p95, p99, max) in milliseconds, plus a breakdown of
failure reasons (HTTP status or exception). Response times are measured from the
*scheduled* start of each request, so they include any time a request had to wait to be
sent (no coordinated omission).

The report also charts response times over time, so hiccups and slow degradation stand
out. Each row is an interval (whole seconds, at most 30 rows) with its p50, p95 and max as
a bar on a log scale, and `!` marks intervals with errors. Here a leak run against a
service with `-Xmx128m`: GC trouble shows at 54 s, the service stops answering at 63 s:

```
   time  reqs  err      p50      p95      max  1ms         10ms       100ms       1s
    48s    60           7.5    103.7    107.5  ##########=============
    51s    60           5.4    105.0    107.0  ########===============
    54s    60          88.0    103.8    141.0  ######################=--
    57s    60          81.7    104.2    133.7  ######################=-
    60s    60          82.9    134.1    244.1  ######################==---
    63s    60  55!   3000.6   3001.3   3004.1  ########################################>
```

With `--report live` a row is printed every second while the run is going (stretched on long
runs to stay under about 60 rows), for the responses that completed in that tick. `flight`
is the number of requests sent but not answered yet, so a service that stops keeping up
shows as `done` dropping while `flight` grows, before any timeout arrives. Bars use a
fixed log scale from 1 ms to `--timeout`. Rows are appended, not redrawn, so the output
also works in CI logs and pipes. The full report follows at the end.

```
   time  done  err flight      p50      p95      max  1ms         10ms       100ms       1s
    62s    40           2     76.9    124.7    126.9  ######################==
    64s    28          14    103.1    293.1    390.5  #######################=====--
    66s     0          54      0.0      0.0      0.0
    68s    33  33!     61   3000.7   3001.9   3003.6  ########################################>
```

The code follows a small MVC split: `Results` is the model (samples, with listeners for
live updates), `Statistics` does the calculations, and a `Report` implementation is the
view: `AsciiReport` for the end report, `LiveAsciiReport` for live rows.

## Build

```shell
./mvnw verify
```
