# Scramjet

A lean, mean performance-simulation service. Scramjet is a fresh, minimal reimplementation of
[perfana/afterburner](https://github.com/perfana/afterburner) on Spring Boot 4.1 and Java 25,
with metrics exported over OTLP.

Use it as a target application for load tests: it can burn CPU, hog or churn memory, hold locks,
sleep, log abundantly, fail on demand and fan out remote calls — all tunable per request.

## Requirements

* Java 25

## Run

```shell
./mvnw spring-boot:run
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

## Metrics over OTLP

Metrics are exported with Micrometer's OTLP registry to an OpenTelemetry collector,
by default `http://localhost:4318/v1/metrics` every 10 seconds, including
`http.server.requests` latency histograms and JVM metrics, tagged with
`service.name=scramjet`.

Configure via `application.properties` or environment:

```properties
management.otlp.metrics.export.url=http://collector:4318/v1/metrics
management.otlp.metrics.export.step=10s
```

Disable export (e.g. locally without a collector) with
`management.otlp.metrics.export.enabled=false`.

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

## Build

```shell
./mvnw verify
```
