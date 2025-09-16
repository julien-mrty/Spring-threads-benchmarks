# Summary

- [Project Loom (aka “Virtual Threads”)](#project-loom-aka-virtual-threads)
- [Latency](#latency)
- [Throughput](#throughput)
- [Classic Threads vs Virtual Threads vs Reactive (R2DBC)](#classic-threads-vs-virtual-threads-vs-reactive-r2dbc)
- [Rules of thumb](#rules-of-thumb)
- [Idempotency](#idempotency)
- [Bonus: how these concepts interact in your project](#bonus-how-these-concepts-interact-in-your-project)
- [Cardinality in Metrics](#cardinality-in-metrics)
- [Who Does What (Metrics Stack)](#who-does-what-metrics-stack)
- [HikariCP and Flyway: How They Work Together](#hikaricp-and-flyway-how-they-work-together)

---

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

## How these concepts interact in the project
- **Latency vs throughput:** As you push higher throughput, **p99 latency** often rises first. That’s your early warning of saturation (DB pool wait times, downstream slowness).
- **Virtual Threads + JDBC:** Great combo—just **bound concurrency** with a small HikariCP pool and **timeouts**.
- **Reactive + R2DBC:** Avoids blocking entirely; excels when you must orchestrate many concurrent I/O operations with **tight resource budgets**.
- **Idempotency:** Mandatory when you add **timeouts + client-side retries** in load tests. It prevents false duplicates from skewing results (and from causing real-world damage).

---

# Cardinality in Metrics

## What “Cardinality” Means
- **Metric series** = metric name **+** full set of label key/value pairs.  
  Example:  
  `http_server_requests_seconds_count{method="GET", uri="/orders/{id}", status="200", instance="app-1"}` is **one** unique time series.
- **Series cardinality** = how many unique series exist for a metric.  
  It grows as the **product** of the number of distinct values of each label.

### Quick Math
- If `method` has 5 values, `status` 5, `uri` 50, `instance` 3 → **5 × 5 × 50 × 3 = 3,750** series for that one metric.
- If it’s a **histogram**, each label-combo produces multiple series (**buckets + sum + count**), so multiply again (e.g., ×12) → **~45,000** series.

## Why Cardinality Matters
- **Memory & cost**: Every active series consumes RAM on Prometheus; more series = higher memory, CPU, storage, and potential OOM.
- **Query latency**: Dashboards slow down or time out when they fan out over millions of series.
- **Reliability**: High series churn (constantly appearing/disappearing label values) hurts ingestion and makes alerting noisy.

## What “Low Cardinality” Really Means
Design labels so that each has a **small, bounded** set of values.

**Good labels**:
- `method` (GET/POST/…)
- `outcome` (SUCCESS/REDIRECTION/CLIENT_ERROR/SERVER_ERROR)
- `status` (a few codes)
- `uri` **pattern** (e.g., `/orders/{id}`, not `/orders/123`)
- `op` (findById/create/update)
- `env` (dev/stage/prod)
- `instance` (a few replicas)

**Dangerous labels**:
- `userId`, `orderId`, raw path, `requestId`, `sessionId`, `stacktrace`, anything unbounded or highly variable.

## Common Sources of Cardinality Explosions (Spring)
### HTTP Server Metrics (`http.server.requests`)
- **OK**: `method`, `status` or `outcome`, **templated** `uri` (e.g., `/orders/{id}`), `exception`.
- **Bad**: raw paths or query strings (e.g., `/orders/12345?search=foo`).

### DB / Repository Timers
- **OK**: small `op` set (findById, insert, update) and maybe `table`.
- **Bad**: tagging with `customerName`, `sqlText`, or dynamic SQL IDs.

### k6 Metrics
- **OK**: static scenario tags like `scenario="mix"` or `route="/orders/{id}"`.
- **Bad**: tagging every request with `vu` (virtual user id), `iteration`, or raw `url`.

## Practical Budgets & Rules of Thumb
- Keep **per-metric** series in the **low thousands** per service/instance.
- For **histograms**, remember: each label-combo × (**buckets + sum + count**). With 10 buckets → **×12** multiplier.
- Multiply again by number of **instances** and **environments**.

---

# Who Does What (Metrics Stack)

- **Micrometer (in this app)** → collects metrics (HTTP, JVM, DB, custom) and exposes them at `/actuator/prometheus`.
- **Prometheus** → pulls/scrapes those metrics on an interval and stores them as time series.
- **Grafana** → queries & visualizes Prometheus data (dashboards, alerts).
- **k6 / wrk2** → generate load. k6 can export its own metrics to Prometheus; wrk2 mostly prints to stdout.

## 1) Spring Boot + Micrometer → Prometheus
Micrometer is the metrics API used by Spring Boot. Add the Prometheus registry (dependency) and expose the endpoint.

## 2) k6 → Prometheus (optional but handy)
k6 can publish its load metrics (RPS, latency, errors) to Prometheus so you can plot generated load vs. app behavior on one Grafana dashboard.

## 3) wrk2
- **Purpose**: very fast, open-loop load generator with constant throughput.
- **Output**: primarily stdout (latency distribution, RPS); no native Prometheus export.
- **Use**: great for quick local max-throughput tests. If you want wrk2 metrics in Grafana, you’d need a sidecar/exporter or to import results manually—most teams use **wrk2 for spot checks** and **k6 for scripted tests + dashboards**.

## 4) Grafana
Point Grafana at Prometheus as a data source. Then:
- Panels for **app metrics** (Micrometer → Prometheus).
- Panels for **load metrics** (k6 → Prometheus).
- **Correlate** spikes from k6 with app latency, DB time, pool saturation, GC, etc.

---

# HikariCP and Flyway: How They Work Together

HikariCP is your app’s **JDBC connection pool**; Flyway is your **DB migration runner**. In Spring Boot they’re wired so that Flyway uses a `DataSource` (often the same one Hikari manages) to run migrations at startup, then Hikari serves your app’s normal queries once the schema is ready.

## Startup Sequence
1. **Spring creates the DataSource**
    - By default this is `HikariDataSource` (HikariCP).
    - Pool is configured from `spring.datasource.*` (url, user, max pool size, etc.).
2. **Flyway runs migrations before the app is “ready”**
    - Spring Boot’s `FlywayMigrationInitializer` triggers `flyway.migrate()` during context refresh.
    - Flyway borrows a connection from the DataSource (HikariCP) and executes your `V1__init.sql`, `V2__seed.sql`, … from `classpath:db/migration`.
    - If the DB supports transactional DDL (e.g., PostgreSQL), Flyway wraps each migration (or a group) in a transaction.
3. **App starts serving traffic**
    - After `migrate()` completes successfully, the context finishes starting.
    - The same Hikari pool now serves your repositories/controllers under normal load.

**Net effect**: Hikari is the faucet; Flyway is an early user of that faucet to prepare the schema. Once done, your code uses the same faucet for queries.

## Connection Behavior & Performance
- **How many connections does Flyway use?** Typically **one** at a time. It borrows from Hikari; with `maximum-pool-size=16` you still have spare connections for anything else starting up.
- **Will this block the app?** Yes, by design. Migrations run **before** endpoints are available, avoiding schema drift at runtime.

### Transactions
- Default is **one transaction per migration** (`group=false`).
- Set `spring.flyway.group=true` to run all pending migrations in a **single transaction** (only if your DB supports it).

## Schema History & Concurrency
- Flyway maintains a table (default **`flyway_schema_history`**) to record which migrations ran, checksums, timestamps.
- If multiple app instances start, Flyway uses **DB-level locking** around this history table so only one instance applies migrations; others wait or skip when they see it’s up to date.

## Flyway vs. `schema.sql` / `data.sql`
- With Flyway on the classpath, Spring Boot does **not** auto-run `schema.sql`/`data.sql` by default—use Flyway’s `V__` and `R__` scripts instead to avoid double-initialization.
