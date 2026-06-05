[//]: # (-*- coding: utf-8-unix -*-)

# Casual Quarkus Extension: Graceful Shutdown Architecture

This document details how the Casual Quarkus JCA extension orchestrates a transactionally safe graceful shutdown when an application receives a SIGTERM signal.

## Overview

Distributed XA transactions require strict synchronization via the transaction coordinator (Narayana). When an application is terminated under heavy load, terminating the JVM immediately or blindly cutting network sockets will cause in-doubt transactions and database anomalies.

To prevent this, the Casual extension implements a two-phase graceful shutdown mechanism hooked into the Quarkus lifecycle. 

It ensures that:

* Any connected client is proactively notified to stop routing new traffic to the degrading application.

* The network wire is allowed to settle.

* In-flight XA transactions are given maximum time to finish their Two-Phase Commit (2PC) cycles.

* Late-arriving service requests are failed fast with TPENOENT without corrupting transaction states.

## Timeline

When a SIGTERM is issued to an application, the following sequential phases are executed within the fixed boundaries of the `quarkus.shutdown.delay` window:

```text
[ SIGTERM Received ]
 │
 ├── 1. Sets the application as disconnecting.
 │   └── Application broadcasts a Domain Disconnect message to all connected clients.
 │
 ├── 2. Wire-Settle Phase (Fixed Pause: Default 1500ms)
 │   └── The application intentionally pauses execution. This guarantees that:
 │       - connected clients receive the disconnect and stop routing new service traffic.
 │       - The clients app's 1-second timer-based Connection Validator fires at least once.
 │       - Late packets already physically in-flight on the network wire land safely.
 │
 ├── 3. Transaction Draining Phase (Dynamic: Up to the remainder of the 30s window - or what you set `quarkus.shutdown.delay` to)
 │   └── The ShutdownBarrier wakes up and actively polls internal registries.
 │       - Pending transactions in the Inbound/Outbound registries are allowed to finish.
 │       - Any accidental late outbound service calls are instantly rejected with TPENOENT.
 │
 ▼
[ Hard Limit: e.g., 30s ] ──> Quarkus Delay Expires ──> JVM terminates cleanly.
```

## The Domain Disconnect & Client Throttling

### Shutdown starts

* The application that is going down is set as `disconnecting` and any new outbound calls will return ```TPENOENT`` from the application without calling the actual resource.
* A domain disconnect is sent to all connected clients.
  When a client gets such a message, the state of the network connection is set to disconnecting.
  When a network connection is set as disconnecting, only `XA` calls are allowed to pass through, IE no service, queue calls etc are allowed.

### Client handles the domain disconnect message
All clients run a background timer-based validation bean configured to check pool health. 

It uses standard, low-overhead JCA connection acquisition (`getConnection()`) to verify if the pool is available ( `connected`) and if so, if it is marked as `disconnecting`. If true, or if connection acquisition fails, it safely evicts the pool from the active routing rotation.

Any non `XA` calls fails on the client side.

### The Wire-Settle Delay (casual.shutdown.wire-settle-delay-ms)

Because the validation bean in a connected client operates on a 1-second resolution, the SIGTERM:ed application executes a deliberate fixed pause before evaluating its transaction registries.

Configured by default to `1500ms`, this pause guarantees that the connected clients have completed at least one full validation loop tick, successfully locking the gate against new service traffic before the SIGMTERM:ed application starts its transaction drain.

### Transaction drainage

Once the wire-settle delay expires, the extension activates its ShutdownBarrier. The barrier polls the following internal tracking registries every casual.shutdown.drain-poll-interval-ms (default 200ms):

CasualInboundTransactionRegistry: Tracks active inbound service execution contexts.

CasualResourceManager: Tracks active outbound calls

The barrier keeps the shutdown thread cleanly blocked as long as hasPending() returns true across either registry. Once both registries hit zero, the barrier releases, allowing the application server to tear down resources cleanly.

## Potential Log Entries when SIGTERM:ing during heavy load

During heavy load, you may observe warning logs on downstream dependencies (like a Database Application) indicating an aborted transaction or a rollback failure during service invocation.

This is expected and transactionally safe. When an application is in its draining phase, late-arriving service calls are met with a fast-failing TPENOENT from the network layer. Because Narayana was aware of the transaction initiation upstream but the service branch was rejected, it drives an explicit rollback downstream to clean up resources. However, since no actual service call was made toward that resource - the transaction was in fact never imported.

## Configuration Properties

These are the default settings by the extension, can be overidden in your application.

### Enable the mandatory fixed Quarkus quiet period

`quarkus.shutdown.delay=30s`

### The polling interval (in milliseconds) used by the ShutdownBarrier to check registries

`casual.shutdown.drain-poll-interval-ms=200`

### Fixed pause (in milliseconds) allowing network frames to settle and front-end validators to tick

`casual.shutdown.wire-settle-delay-ms=1500`
