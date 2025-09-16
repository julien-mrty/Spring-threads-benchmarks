# Virtual Threads at Scale (Java 21 / Spring Boot)

*One repo. Three concurrency models. Real failure modes. Side-by-side benchmarks.*

**Goal:** Help Spring developers decide when to use **Virtual Threads (Loom)** vs a **classic thread pool** vs **reactive WebFlux**, by measuring real workloads (mostly-blocking I/O) under stress with proper backpressure, timeouts, and observability.

See [Concepts & Definitions](./docs/concepts.md) for more information on concepts used in this repo.

---

## Problem statement & value

Modern Spring teams face a practical choice:

- Stick with **classic MVC + thread pool**,
- embrace **Java 21 virtual threads (Loom)** for a simpler but scalable blocking style, or
- go **reactive (WebFlux + R2DBC)** for end-to-end non-blocking I/O.

**Which one should you pick for a mostly-blocking app (e.g., database-heavy)?**  
This project answers that with reproducible benchmarks and failure playbooks, so you can choose based on data—not vibes.

---

## What you get

- A runnable Spring Boot app with **three profiles** (classic pool, virtual threads, reactive).
- Workloads that mimic real systems (DB calls, fan-out HTTP, slowdowns, spikes).
- **Metrics + dashboards** to see backpressure, tail latency, and collapse/recovery.
- A **decision guide**: when VT is perfect, when reactive still wins, and when classic is “good enough”.

---

## Why JDBC as the blocking example (and the broader principle)

- **JDBC is blocking by design**: the calling thread waits for the DB to respond.
- In ~90% of backend apps, the **database is the bottleneck**, so JDBC is a realistic, high-signal workload to test.
- The trade-offs you’ll see are general to any blocking I/O:
    - Filesystem access (read/write large files)
    - Blocking HTTP clients to external services
    - Message brokers/drivers that block on I/O
    - Legacy SDKs that expose synchronous APIs

So while we use JDBC as the main exemplar, you can mentally swap in “blocking call X” and the conclusions still hold.

---

## Concurrency models compared

### MVC + Classic Thread Pool
- **Bounded pool** (e.g., 200–400 threads), easy to reason about.
- Can **stall under heavy blocking** or slow downstreams.

### MVC + Virtual Threads (Java 21)
- Switch on with `spring.threads.virtual.enabled=true`.
- Keeps the **simple, blocking coding style**, but supports **huge concurrency** cheaply.
- Still needs **backpressure** (e.g., DB connection pool stays small!).

### Reactive (WebFlux + R2DBC)
- End-to-end **non-blocking**, great for **high fan-out** & **streaming**.
- **Steeper learning curve**; different debugging/observability patterns.

### Classic Threads vs Virtual Threads vs Reactive (R2DBC)

| Aspect | Classic Threads (MVC + JDBC) | Virtual Threads (MVC + JDBC, Loom) | Reactive (WebFlux + R2DBC) |
|---|---|---|---|
| **Concurrency model** | Fixed-size thread pool; each request uses an OS thread | Millions of cheap virtual threads; blocking style remains | Non-blocking (event loops), back-pressure via reactive streams |
| **Code style** | Simple, imperative | Same as classic (simple, blocking) | Functional/async pipelines (Mono/Flux) |
| **Best for** | Low–mid concurrency, mature legacy apps | Mostly-blocking I/O with high concurrency; easy migration path | End-to-end non-blocking, high fan-out, streaming |
| **Resource limits** | Pool size caps concurrency; easy to reason about | DB connections become the real limit; must enforce backpressure | Few threads; concurrency limited by event loop & back-pressure |
| **Pitfalls** | Thread pool exhaustion under blocking | Pinning, unbounded fan-outs if you ignore limits | Steeper learning curve; context/logging/stack traces trickier |
| **DB access** | JDBC (blocking) | JDBC (blocking, but OK with VTs) | R2DBC (non-blocking DB driver) |
| **Backpressure knobs** | Pool sizes/queues | DB pool size + semaphores + timeouts | Reactive operators (`buffer`, `limitRate`, `timeout`), connection pools |

---


---

## What we benchmark

### Workloads
- **DB-bound:** `GET /orders/{id}`, `POST /transfer` (transaction), `GET /report/slow` (simulated slow query).
- **Downstream HTTP fan-out:** `GET /aggregate` calls N mock services and merges results.
- **Mixed CPU/I/O:** small CPU blips to ensure no runaway.

### Metrics
- **Throughput** (RPS), **latency** (p50/p95/p99), **error/timeouts**.
- **DB pool stats** (active/idle/pending), **thread/VT counts**, **HTTP server stats**.
- **Custom timers** (connection acquisition, retries, cancellations).

### Failure modes (on purpose)
- **DB pool exhaustion** (watch pending waits and timeouts).
- **Slow queries / slow downstreams** (timeouts, cancellations).
- **Pinned virtual threads** (e.g., `synchronized` around I/O) → detect and fix.
- **ThreadLocal misuse** → memory/GC pressure and safer alternatives.

---

## Safety nets you’ll see in code

- **Backpressure** via small HikariCP pool and explicit **semaphores** for fan-outs.
- **Timeouts everywhere:** JDBC statement/acquire timeouts, HTTP connect/read/response timeouts.
- **Structured concurrency** (`StructuredTaskScope`) to fan-out with deadlines and auto-cancel losers.
- **Context propagation:** MDC in MVC; Reactor Context (and Micrometer context) in reactive.

---

## When to choose what (rule-of-thumb)

- **Use Virtual Threads (MVC+VT)** when your app is **mostly blocking** (DB/HTTP), you want **simple code**, **high concurrency**, and you **respect resource limits** (DB pool remains bounded).
- **Use Reactive (WebFlux+R2DBC)** when you need **end-to-end non-blocking**, **high fan-out aggregations**, **streaming**, or **strict resource caps**.
- **Classic MVC pool** is still fine for **low-to-moderate loads** or legacy stacks where changing concurrency model isn’t worth the complexity.

---

## Quickstart

### Prereqs
- Ubuntu 24, Java 21 (Temurin), Maven 3.9+, Docker.

### Run the app (current baseline)
```bash
mvn spring-boot:run
# then:
curl http://localhost:8080/
```

### Profiles (as they land)

**mvc-vt → Spring MVC + JDBC + virtual threads**
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=mvc-vt
```

**mvc-classic → Spring MVC + JDBC + ThreadPoolTaskExecutor**
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=mvc-classic
```

**reactive → WebFlux + R2DBC**
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=reactive
```

> **Tip:** Expose `/actuator/prometheus` and spin up **Prometheus + Grafana** via `docker-compose` (provided) to visualize runs.

---

## Benchmark methodology

- **Load tools:** k6 and/or wrk2 (constant RPS).
- **Stages:** light → medium → heavy → spike.
- **Runs:** warm-up ≥2m, measure ≥5m, repeat ×3; keep CSV/JSON.
- **Hardware:** fixed settings; note OS/file-descriptor limits.
- **Outputs:** latency histograms, error rates, PromQL snapshots, Grafana dashboards.

---

## Key config snippets

### Enable Virtual Threads (MVC profile)
```yaml
# application-mvc-vt.yml
spring:
  threads:
    virtual:
      enabled: true
  datasource:
    hikari:
      maximum-pool-size: 16
      connection-timeout: "200ms"

server:
  tomcat:
    max-connections: 1000

management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
```

### Classic pool (MVC profile)
```yaml
# application-mvc-classic.yml
spring:
  threads:
    virtual:
      enabled: false
  task:
    execution:
      pool:
        core-size: 200
        max-size: 400
  datasource:
    hikari:
      maximum-pool-size: 16
```

### Reactive (WebFlux + R2DBC)
```yaml
# application-reactive.yml
spring:
  r2dbc:
    url: r2dbc:postgresql://localhost:5432/app
    username: app
    password: app

management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
```

---

## Java 21 now, migrate to Java 25 later

This repo starts on **Java 21 (LTS)** for maximum compatibility.  
After **Java 25** (next LTS) stabilizes in the ecosystem, we’ll migrate, re-run benchmarks, and document: what broke (if anything), what got faster, and any GC/profiling differences.
