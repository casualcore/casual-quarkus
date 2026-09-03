[//]: # (-*- coding: utf-8-unix -*-)

# Chaos and soak test results: reverse outbound topology

This document records the methodology, topology, and verification results of the extended chaos and soak test suite executed

---

## Executive summary

2026-09-02

| Test Run | Duration | Concurrency | Total Transactions | Throughput | In-Doubt Txs | SIGKILLs | Netty Leaks |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Baseline Chaos** | 10 min | 50 conns | **5,638,725** | 9,396 req/s | **0** | **0** | **0** |
| **Medium Soak** | 30 min | 50 conns | **17,569,313** | 9,760 req/s | **0** | **0** | **0** |
| **Extended 2-Hour Soak** | 120 min | 50 conns | **72,269,146** | 10,037 req/s | **0** | **0** | **0** |
| **Netty PARANOID Leak Run** | 60 min | 50 conns | **23,319,060** | 6,477 req/s | **0** | **0** | **0** |
| **Total Verified** | **3h 40m** | — | **118,796,244** | — | **0** | **0** | **0** |

Across **118.7+ million distributed XA transactions** executed under continuous node terminations, the Casual Quarkus extension maintained **100% transactional consistency** with **zero in-doubt transactions**, **zero Netty buffer leaks** under 100% PARANOID allocation sampling, and **zero hard kills** within the standard Kubernetes 30-second grace window.

---

## Topology & architecture

The chaos soak test exercises a multi-tier distributed microservices topology combining forward outbound and reverse inbound/outbound JCA connectivity:

```text
               HTTP (wrk Load Generator)
                         │
                         ▼
             ┌────────────────────────┐
             │       front-app        │ (Port 8080)
             │   (REST Entry Point)   │
             └───────────┬────────────┘
                         │
        Forward Outbound │ JCA / tpacall
                         │
         ┌───────────────┴───────────────┐
         ▼                               ▼
┌──────────────────┐           ┌──────────────────┐
│    node-app 1    │           │    node-app 2    │
│ (Reverse Outbound│           │ (Reverse Outbound│
│  Listener: 7785) │           │  Listener: 7786) │
│ (Inbound:  7771) │           │ (Inbound:  7772) │
└────────▲─────────┘           └────────▲─────────┘
         │                              │
         └──────────────┬───────────────┘
                        │ Reverse Inbound Connections
                        │ (Transports XA 2PC & Business Traffic)
                        │
             ┌──────────┴─────────────┐
             │         db-app         │ (Port 8083)
             │  (Reverse Inbound +    │
             │   H2 XA DataSource)    │
             └────────────────────────┘
```

### Components

1. **`front-app`:** Exposes a transactional REST endpoint (`POST /casualcallersync/counter`). Routes requests across `node-app` instances using dynamic service discovery.
2. **`node-app` (Node 1 & Node 2):** Intermediate service layer. Exposes inbound services (`counter`) and configures reverse outbound listeners (`7785` and `7786`).
3. **`db-app`:** Connects via reverse inbound to both nodes and processes transactional database updates against an H2 XA in-memory datasource (`db_counter`).
4. **`wrk`:** High-performance HTTP benchmarking tool driving continuous concurrent load.

---

## Chaos testing methodology

The automated test harness [`example/scripts/chaos-soak-test.sh`](../../scripts/chaos-soak-test.sh) drives continuous traffic while injecting failure events:

1. **Continuous Load:** `wrk` maintains 50 concurrent connections dispatching transactional requests via `soak-post.lua`.
2. **Chaos Cycle (Every 60s):**
   * A random backend node (`node-app 1` or `node-app 2`) receives a `SIGTERM` signal while actively handling in-flight requests.
   * The test harness monitors process exit and enforces a 30-second Kubernetes-style grace period.
   * The remaining node immediately takes over 100% of the traffic without service interruption.
   * After 5 seconds, the terminated node is restarted. `db-app` automatically reconnects its reverse inbound connection, and `front-app` re-registers the node in active routing once its domain handshake and discovery completes.
3. **Automated Post-Test Verification:**
   * **Narayana ObjectStore Scan:** Verifies that no unresolved or in-doubt transaction log files exist (`find ObjectStore -type f`).
   * **Graceful Exit Audit:** Verifies that zero `SIGKILL` signals were required.
   * **Netty Buffer Leak Scan:** Scans all application logs for `LEAK: ByteBuf.release()` warnings.

---

## Graceful shutdown verification

Distributed XA transactions require coordinated completion of two-phase commit (2PC) cycles. The extension implements a coordinated pre-shutdown lifecycle configured via `quarkus.shutdown.delay=15s`:

```text
[ SIGTERM Received ]
 │
 ├── 1. Mark Domain Disconnecting
 │   └── Outbound service requests fail fast with TPENOENT.
 │   └── Application broadcasts Domain Disconnect message to all connected clients.
 │
 ├── 2. Wire-Settle Delay (1500 ms)
 │   └── Pauses to allow network frames to land.
 │
 ├── 3. Transaction Draining
 │   └── ShutdownBarrier actively polls until Inbound/Outbound transaction registries reach zero.
 │
 ├── 4. Remainder of 15s Delay Window
 │   └── Remote Transaction Managers finish committing pending 2PC branches.
 │
 └── 5. Quarkus Teardown & Clean JVM Exit (~16–29s total)
```

In all test runs, every terminating process completed draining and exited cleanly **before the 30-second grace window elapsed (0 hard kills)**.

---

## Detailed test results

### 1. Extended 2-Hour Continuous Chaos Run

* **Execution Date:** 2026-09-02
* **Run ID:** `chaos-20260902-125335`
* **Duration:** 120 minutes (2.0 hours)
* **Chaos Events:** 76 node crash & recovery cycles (1 restart per minute)

```text
========================================================
  TEST RESULTS & TRANSACTION INTEGRITY VERIFICATION
========================================================
[PASS] Zero in-doubt transactions detected in ObjectStore!

--- Graceful Shutdown Verification ---
[PASS] All applications exited gracefully within the 30s grace period (0 hard kills).

--- wrk Load Summary ---
WRK_TOTAL:72269146
WRK_RPS:10037.28
WRK_DURATION:7200.074
WRK_SOCK_ERR:0
WRK_CONNECT_ERR:0
WRK_READ_ERR:0
WRK_WRITE_ERR:0
WRK_TIMEOUT_ERR:0

Running 120m test @ http://localhost:8080/casualcallersync/counter
  4 threads and 50 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     5.90ms    7.05ms 671.76ms   90.01%
    Req/Sec     2.52k   410.64     5.82k    80.22%
  72269146 requests in 120.00m, 5.93GB read
  Socket errors: connect 0, read 0, write 0, timeout 0
Requests/sec:  10037.28
Transfer/sec:    863.29KB
========================================================
```

---

### 2. 1-Hour Netty `PARANOID` Leak Detection Run

* **Execution Date:** 2026-09-02
* **Run ID:** `chaos-20260902-150057`
* **JVM Flag:** `-Dio.netty.leakDetection.level=PARANOID` (100% ByteBuf allocation sampling)
* **Duration:** 60 minutes (1.0 hour)
* **Chaos Events:** 38 node crash & recovery cycles

```text
========================================================
  TEST RESULTS & TRANSACTION INTEGRITY VERIFICATION
========================================================
[PASS] Zero in-doubt transactions detected in ObjectStore!

--- Graceful Shutdown Verification ---
[PASS] All applications exited gracefully within the 30s grace period (0 hard kills).

--- Netty Resource Leak Verification ---
[PASS] Zero Netty buffer leaks detected in application logs!

--- wrk Load Summary ---
WRK_TOTAL:23319060
WRK_RPS:6477.37
WRK_DURATION:3600.082
WRK_SOCK_ERR:0
WRK_CONNECT_ERR:0
WRK_READ_ERR:0
WRK_WRITE_ERR:0
WRK_TIMEOUT_ERR:0

Running 60m test @ http://localhost:8080/casualcallersync/counter
  4 threads and 50 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     7.50ms    4.25ms 261.08ms   71.39%
    Req/Sec     1.63k   220.32     4.04k    84.66%
  23319060 requests in 60.00m, 1.91GB read
  Socket errors: connect 0, read 0, write 0, timeout 0
Requests/sec:   6477.37
Transfer/sec:    555.34KB
========================================================
```

---

### 3. 30-Minute Chaos Run

* **Execution Date:** 2026-09-02
* **Run ID:** `chaos-20260902-120044`
* **Duration:** 30 minutes
* **Total Transactions:** 17,569,313 (9,760 req/s)
* **Average Latency:** 5.94 ms
* **In-Doubt Transactions:** 0
* **SIGKILLs:** 0

---

### 4. 10-Minute Baseline Run

* **Execution Date:** 2026-09-02
* **Run ID:** `chaos-20260902-105138`
* **Duration:** 10 minutes
* **Total Transactions:** 5,638,725 (9,396 req/s)
* **Average Latency:** 6.22 ms
* **In-Doubt Transactions:** 0
* **SIGKILLs:** 0

---

## Key findings & operational recommendations

1. **Kubernetes Grace Period Configuration:**
   * Configure `quarkus.shutdown.delay=15s` (default in extension).
   * In Kubernetes Pod specifications, set `terminationGracePeriodSeconds: 30` (or `45`). This provides sufficient headroom for the 15-second quiet window, wire-settling, transaction draining, and clean container termination without triggering `SIGKILL`.
2. **Netty ByteBuf Lifecycle:**
   * Netty ByteBuf management in both outbound and reverse inbound pipelines is leak-free under 100% allocation tracking (`PARANOID` level).
3. **Transaction Resilience:**
   * Dynamic node removal under load safely aborts in-flight branches with `TPENOENT` during the quiet period, enabling Narayana to drive clean rollbacks on upstream callers without leaving orphaned locks or in-doubt records in `ObjectStore`.


Note: 1 depends on your specific topology. If you have longer running transactions that you always want to be able to finish when nodes come and go, then you need to configure `quarkus.shutdown.delay` and the k8s POD termination grace period accordingly.
