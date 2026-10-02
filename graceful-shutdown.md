[//]: # (-*- coding: utf-8-unix -*-)

# Casual Quarkus extension: graceful shutdown

The Casual Quarkus extension coordinates Casual XA work with the two-phase Quarkus shutdown lifecycle. This coordination lets connected domains stop sending service traffic while existing XA branches finish.

For the Quarkus lifecycle contract, see [Application initialization and termination](https://quarkus.io/guides/lifecycle/#graceful-shutdown).

## Shutdown lifecycle

When the process receives `SIGTERM`, Quarkus runs two sequential phases.

### Phase 1: Delay

During the delay phase, Quarkus performs the following actions:

* If SmallRye Health is present, the readiness check reports `DOWN` and its HTTP endpoint returns `503`.
* The HTTP server continues accepting and processing requests.
* Quarkus fires `ShutdownDelayInitiatedEvent`.
* After all synchronous event observers return, Quarkus waits for `quarkus.shutdown.delay` to elapse.

`CasualShutdownDelayHandler` observes `ShutdownDelayInitiatedEvent`. The observer blocks shutdown while it performs the Casual shutdown sequence. Its execution time is additional to `quarkus.shutdown.delay`.

```text
Phase 1 duration = Casual handler duration + quarkus.shutdown.delay

Casual handler duration = wire-settle delay + transaction-drain duration
```

When the drain deadline expires during a polling sleep, the handler can return up to one poll interval after the configured timeout.

The handler performs these steps:

1. It marks the local domain as disconnecting. New outbound service and queue calls fail with `TPENOENT`.
2. It sends a domain disconnect message to connected clients. Clients stop routing new service and queue calls to the domain while continuing to allow XA coordination calls for existing transactions.
3. It waits for `casual.shutdown.wire-settle-delay-ms`. This delay gives peers time to process the disconnect and lets network messages already in transit arrive.
4. It checks the inbound and outbound transaction registries every `casual.shutdown.drain-poll-interval-ms`.
5. It returns when both registries are empty or when `casual.shutdown.drain-timeout-ms` expires.

If the drain timeout expires, the handler logs the remaining inbound and outbound entry counts and allows shutdown to continue. A value of `0` for `casual.shutdown.drain-timeout-ms` disables the deadline and allows the handler to wait indefinitely.

### Phase 2: Shutdown

After phase 1, Quarkus starts extension and CDI teardown. Quarkus also stops accepting new HTTP requests and waits for supported active requests to finish.

`quarkus.shutdown.timeout` bounds the wait for active requests. Quarkus currently documents graceful request tracking for the HTTP extension. Do not treat this property as a general deadline for every extension cleanup operation.

Methods annotated with `@Shutdown` and observers of `ShutdownEvent` run during this phase. IronJacamar deactivates the resource adapter and closes its connection-management infrastructure during the same teardown phase.

## Timeline

```text
SIGTERM
  |
  |  Phase 1: Delay
  |  Readiness reports DOWN; HTTP continues serving requests
  |
  +-- ShutdownDelayInitiatedEvent
  |     +-- Mark the Casual domain as disconnecting
  |     +-- Send domain disconnect
  |     +-- Wait for the wire-settle delay
  |     +-- Drain Casual XA work until empty or timed out
  |
  +-- Wait for quarkus.shutdown.delay
  |
  |  Phase 2: Shutdown
  |  Stop accepting HTTP requests and begin framework teardown
  |
  +-- Wait for supported active requests, bounded by quarkus.shutdown.timeout
  +-- Run ShutdownEvent observers and @Shutdown methods
  +-- Deactivate IronJacamar and other extensions
  |
Process exits
```

## Transaction registries

The handler checks these registries:

* `CasualInboundTransactionRegistry` tracks active inbound execution contexts.
* `CasualResourceManager` tracks active outbound XA resources.

The logged counts are registry-entry counts. During concurrent updates, they provide an operational snapshot and do not necessarily represent distinct global transactions.

When both registries are empty after the wire-settle delay, the handler does not wait for a drain poll. When work remains, the handler sleeps for the configured poll interval between checks.

## Configuration

The extension currently supplies these defaults:

| Property | Default | Purpose |
| :--- | :--- | :--- |
| `quarkus.shutdown.delay-enabled` | `true` | Enables the Quarkus delay phase and `ShutdownDelayInitiatedEvent`. Keep this build-time property enabled because Casual transaction draining runs from this event. |
| `quarkus.shutdown.delay` | `0s` | Keeps Quarkus in phase 1 after the Casual handler returns. Use a nonzero value when your infrastructure needs time to observe the readiness change before shutdown continues. |
| `casual.shutdown.wire-settle-delay-ms` | `500` | Waits for peers to process domain disconnect and for messages already in transit to arrive. |
| `casual.shutdown.drain-poll-interval-ms` | `200` | Controls how frequently the handler checks the transaction registries. |
| `casual.shutdown.drain-timeout-ms` | `10000` | Limits transaction draining after the wire-settle delay. Set it to `0` to wait indefinitely. |

Your application configures `quarkus.shutdown.timeout`. Quarkus does not provide a shutdown timeout unless you set one.

### Choose the Quarkus delay

`quarkus.shutdown.delay` does not provide the Casual transaction-drain budget. `CasualShutdownDelayHandler` already blocks phase 1 while it drains Casual work.

If your application receives HTTP traffic through readiness-aware infrastructure, configure enough Quarkus delay for that infrastructure to observe the failed readiness check and stop routing traffic. During this delay, Quarkus continues to serve HTTP requests normally.

The extension defaults `quarkus.shutdown.delay` to `0s`. The Casual handler still runs because `quarkus.shutdown.delay-enabled` remains `true`. If the application has a request source that depends on Quarkus readiness propagation, configure a nonzero delay that covers the propagation time.

Do not disable `quarkus.shutdown.delay-enabled`. Without the delay event, Quarkus begins tearing down subsystems before Casual can notify connected domains and drain transactions.

### Choose the wire-settle delay

Choose a wire-settle delay that covers network latency and peer processing time in your deployed topology. A shorter delay reduces shutdown time but can move late service traffic into the transaction-drain period.

Local chaos testing with a `500 ms` delay drained up to 32 inbound and 31 outbound entries without requiring an additional `200 ms` poll. Treat that result as a starting point and validate it under representative network latency before changing the production default.

### Choose the drain timeout

Set `casual.shutdown.drain-timeout-ms` to the maximum time you want to reserve for remaining Casual XA work after the wire-settle delay. If the deadline expires, shutdown proceeds even when registry entries remain.

Use an indefinite timeout only when an external process supervisor can wait indefinitely. In Kubernetes, use a finite timeout so the application retains time for phase 2 before the pod termination grace period expires.

### Choose the Quarkus shutdown timeout

Set `quarkus.shutdown.timeout` from the longest HTTP request that you want Quarkus to finish after phase 2 starts. This timeout does not replace the Casual drain timeout and does not guarantee that arbitrary extension cleanup finishes within the same period.

## Kubernetes termination budget

Configure `terminationGracePeriodSeconds` to cover the worst-case sequential shutdown time plus a safety margin:

```text
wire-settle delay
+ Casual drain timeout
+ one drain poll interval
+ quarkus.shutdown.delay
+ quarkus.shutdown.timeout, when configured
+ framework teardown time
+ safety margin
```

For the common case where the registries become empty during wire settlement and no HTTP request remains active, the process uses only the wire-settle delay, the Quarkus delay, and framework teardown time.

Kubernetes sends `SIGKILL` when the pod termination grace period expires. Make the pod budget larger than the application budget so Quarkus can finish its own shutdown sequence.

## Expected failures during shutdown

After a domain begins disconnecting, a small number of service calls can already be in transit. A downstream call can then return `TPENOENT`, which the intermediate service reports upstream as `TPESVCFAIL`. This response is expected during the interval before every peer has processed domain disconnect.

Existing transactions continue to use XA coordination calls. If service execution fails after resource enlistment, the transaction rolls back rather than retrying the invocation through another connection factory in the same transaction.

## Diagnostic logging

Set the following category to `INFO` to log the initial registry counts, configured timing values, successful drain completion, and drain timeouts:

```properties
quarkus.log.category."se.laz.casual.quarkus.CasualShutdownDelayHandler".level=INFO
```

If you set `quarkus.log.console.level=ERROR`, the console handler filters these records even when the category level is `INFO`. Set the console handler to `INFO` for the diagnostic run:

```properties
quarkus.log.console.level=INFO
```
