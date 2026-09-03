[//]: # (-*- coding: utf-8-unix -*-)

# Casual Quarkus extension: graceful shutdown

This document details how the Casual Quarkus JCA extension orchestrates a transactionally safe graceful shutdown when an application receives a `SIGTERM` signal.

## Overview

Distributed XA transactions require strict synchronization via the transaction coordinator (Narayana). When an application is terminated under heavy load, terminating the JVM immediately or abruptly closing network sockets causes in-doubt transactions and database inconsistencies.

To prevent this, the Casual extension implements a graceful shutdown mechanism hooked into the Quarkus lifecycle. 

It ensures that:

* Connected clients are notified to stop routing new traffic to the terminating application.
* The network wire is allowed to settle.
* In-flight XA transactions receive sufficient time to finish their two-phase commit (2PC) cycles.
* Late-arriving outbound service requests from the terminating application fail fast with `TPENOENT`.

## Timeline

When a `SIGTERM` signal is delivered to an application, the following sequential phases execute within the `quarkus.shutdown.delay` window:

```text
[ SIGTERM Received ]
 │
 ├── 1. Application marked as disconnecting - any new outbound call returns TPENOENT.
 │   └── Application broadcasts a Domain Disconnect message to all connected clients.
 │
 ├── 2. Wire-Settle Phase (Fixed Pause: Default 1500ms)
 │   └── The application pauses execution to ensure:
 │       - Connected clients receive the disconnect message and stop routing new service traffic.
 │       - The client application's 1-second timer-based Connection Validator fires at least once.
 │       - Late packets already in flight on the network wire land safely.
 │
 ├── 3. Transaction Draining Phase (Dynamic: Up to the remainder of the shutdown delay window)
 │   └── The ShutdownBarrier actively polls internal registries:
 │       - Pending transactions in the Inbound/Outbound registries complete.
 │       - Any late outbound service calls are rejected immediately with TPENOENT.
 │
 ▼
[ Hard Limit: e.g., 15s ] ──> Quarkus Delay Expires ──> JVM terminates cleanly.
```

## Domain disconnect and client throttling

### Shutdown sequence starts

* The terminating application is marked as `disconnecting`. Any new outbound calls return `TPENOENT` immediately without reaching network resources.
* The application sends a domain disconnect message to all connected clients.
* When a client receives this message, it marks the network connection as `disconnecting`. Only XA coordination calls are permitted over a disconnecting connection; service and queue calls are rejected.

### Client handles the domain disconnect message

Clients run a background timer-based validation bean to check pool health. 

The validator uses standard JCA connection acquisition (`getConnection()`) to verify whether the pool is connected and whether it is marked as `disconnecting`. If marked as disconnecting or if connection acquisition fails, the validator evicts the pool from active routing.

### Wire-settle delay (`casual.shutdown.wire-settle-delay-ms`)

Because the validation bean in a connected client operates on a 1-second interval, the terminating application pauses execution for a fixed duration before evaluating its transaction registries.

Configured by default to `1500ms`, this pause guarantees that connected clients complete at least one validation cycle, preventing new service traffic from reaching the terminating application before transaction draining begins.

### Transaction draining

Once the wire-settle delay expires, the extension activates its `ShutdownBarrier`. The barrier polls the following internal tracking registries every `casual.shutdown.drain-poll-interval-ms` (default `200ms`):

* `CasualInboundTransactionRegistry`: Tracks active inbound service execution contexts.
* `CasualResourceManager`: Tracks active outbound calls.

The barrier keeps the shutdown thread blocked while `hasPending()` returns `true` on either registry. Once both registries reach zero, the barrier releases, allowing Quarkus to tear down resources cleanly.

## Potential log entries during termination under heavy load

During heavy load, you might observe warning logs on downstream dependencies (such as a database application) indicating an aborted transaction or a rollback failure during service invocation.

This behavior is expected and transactionally safe. When an application is in its draining phase, late-arriving service calls fail fast with `TPENOENT` from the network layer. Because Narayana was aware of the upstream transaction initiation but the service branch was rejected, it drives an explicit downstream rollback to clean up resources.

## Configuration properties

The following properties configure graceful shutdown behavior:

| Property | Default | Description |
| :--- | :--- | :--- |
| `quarkus.shutdown.delay` | `15s` | Mandatory fixed Quarkus quiet period window. |
| `casual.shutdown.wire-settle-delay-ms` | `1500` | Fixed pause (in milliseconds) allowing network frames to settle and client validators to run. |
| `casual.shutdown.drain-poll-interval-ms` | `200` | Polling interval (in milliseconds) used by `ShutdownBarrier` to check transaction registries. |
| `quarkus.shutdown.delay-enabled` | `true` | Enables Quarkus shutdown delay handling. Do not override this setting. |

> [!NOTE]
> The appropriate value for `quarkus.shutdown.delay` depends on your specific topology and workload. If you have longer running transactions that you always want to be able to finish when nodes come and go, configure `quarkus.shutdown.delay` and the Kubernetes Pod `terminationGracePeriodSeconds` accordingly.

## Shutdown delay vs. shutdown timeout

In Quarkus, `quarkus.shutdown.delay` and `quarkus.shutdown.timeout` govern two distinct, sequential phases of the shutdown lifecycle:

### 1. `quarkus.shutdown.delay` (Pre-shutdown quiet period)

* **What it does:** An intentional pause before Quarkus begins tearing down any components or services.
* **How it works:**
  1. Quarkus receives a `SIGTERM` signal.
  2. Quarkus fires `ShutdownDelayInitiatedEvent` (where `CasualShutdownDelayHandler` broadcasts domain disconnect and drains XA transactions).
  3. Quarkus holds execution and waits for the full duration of the delay (e.g., `15s`). During this quiet window, Kubernetes propagates the pod's `Terminating` status to remove its IP from Endpoints/Ingress (for HTTP entry points), while Casual connected clients receive the Domain Disconnect message and stop routing new service calls to the node.
* **Nature:** Fixed duration (Quarkus sleeps for the configured time).

### 2. `quarkus.shutdown.timeout` (Teardown grace window)

* **What it does:** The maximum deadline/timeout allowed during the actual teardown of resources (HTTP server, CDI beans, IronJacamar JCA pools, JDBC datasources, thread pools).
* **How it works:**
  1. Once the delay phase finishes, Quarkus begins closing active HTTP connections, shutting down pools, and terminating threads.
  2. If all resources finish shutting down in 200 ms, Quarkus exits immediately—it does not wait for the timeout.
  3. If some thread or connection hangs, Quarkus forcefully aborts once `quarkus.shutdown.timeout` expires.
* **Nature:** Upper bound / safety cutoff (non-blocking if teardown completes quickly).

### Summary of the lifecycle

```text
[ SIGTERM Received ]
 │
 ├── Phase 1: Pre-Shutdown Delay (`quarkus.shutdown.delay=15s`)
 │   ├── Fires ShutdownDelayInitiatedEvent (Casual domain disconnect & XA drain)
 │   └── Pauses for remaining delay (allows K8s endpoints & Casual peers to reroute)
 │
 ├── Phase 2: Active Teardown (`quarkus.shutdown.timeout=...`)
 │   ├── Closes HTTP listeners, JCA pools, DataSource, thread pools
 │   └── Exits as soon as teardown finishes (usually ~100–300 ms, capped at timeout)
 │
 ▼
[ JVM Process Exits ]
```

Keeping `quarkus.shutdown.timeout` at `5s`–`15s` ensures that if a resource hangs during teardown, it won't stall the container past the Kubernetes hard-kill boundary (`terminationGracePeriodSeconds`).
