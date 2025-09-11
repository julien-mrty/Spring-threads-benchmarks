# Project Loom (aka “Virtual Threads”)

**What it is:** A long-running OpenJDK effort (shipped GA in Java 21) that adds **virtual threads**—extremely cheap threads scheduled by the JVM instead of the OS.

**Why it matters:**
- You can keep writing simple **blocking code** (JDBC, HTTP clients), but handle **massive concurrency** (tens/hundreds of thousands of requests).
- Great fit for **I/O-heavy** services.
- **Watch out for pinning:** if a virtual thread enters a native/`synchronized` section during blocking I/O, it can temporarily “pin” its carrier OS thread, hurting scalability.

---

## Latency
- **Definition:** The time to handle one request (start → finish).
- Usually reported as **p50/p95/p99** (medians and tail latencies).
- Lower is better; high **p99** often indicates contention, timeouts, GC pauses, or saturated downstreams.
- **Example:** `p50=12 ms, p95=45 ms, p99=120 ms` means most requests are fast, but a few are slow—those tails often hurt users the most.

---

## Throughput
- **Definition:** How many requests per second your service completes (**RPS/QPS**).
- Higher is better, but **only** if latency and error rate remain acceptable.
- Always discuss throughput **with** latency (a system can do high RPS by queueing requests and making them slow—bad).

---

## Classic Threads vs Virtual Threads vs Reactive (R2DBC)

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

## Rules of thumb
- If your app is **DB/HTTP-heavy and blocking** → **Virtual Threads** give scalability with simple code.
- If your app does **huge fan-out/streaming** or needs **strict resource caps** → **Reactive/R2DBC**.
- **Classic pool** is fine for **modest loads** or when change risk is high.

---

## Idempotency
- **Definition:** An operation is idempotent if running it multiple times has the same effect as running it once.
- **Why you care:** Networks fail, clients retry. Without idempotency, retries can double-charge, duplicate orders, etc.

**HTTP examples (intended semantics):**
- `GET`, `HEAD`, `OPTIONS` → idempotent and **safe** (no state change).
- `PUT` → **idempotent** (replace/ensure a resource’s state).
- `DELETE` → **idempotent** (deleting an already-deleted resource is still “deleted”).
- `POST` → **not idempotent** by default (creates/changes state).

### Making POST idempotent in practice
- **Idempotency key:** Client sends a unique key (e.g., `Idempotency-Key` header).
- **Server stores** result keyed by *(key, operation)*.
- **Retries** with the same key return the original result without re-executing side effects.
- **Natural keys:** Use a business unique key (e.g., `transferId`) so repeated calls overwrite/return the same transaction.
- **Exactly-once vs at-least-once:** In distributed systems you usually guarantee **at-least-once** processing + **idempotent handlers**.
- **Persistence tip:** Keep an idempotency table with `(key, status, responseHash, createdAt, ttl)` to dedupe retries safely.

---

## Bonus: how these concepts interact in your project
- **Latency vs throughput:** As you push higher throughput, **p99 latency** often rises first. That’s your early warning of saturation (DB pool wait times, downstream slowness).
- **Virtual Threads + JDBC:** Great combo—just **bound concurrency** with a small HikariCP pool and **timeouts**.
- **Reactive + R2DBC:** Avoids blocking entirely; excels when you must orchestrate many concurrent I/O operations with **tight resource budgets**.
- **Idempotency:** Mandatory when you add **timeouts + client-side retries** in load tests. It prevents false duplicates from skewing results (and from causing real-world damage).
