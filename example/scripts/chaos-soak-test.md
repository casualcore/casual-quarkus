# Run the chaos soak test

The chaos soak test runs transactional HTTP load through the front application while it repeatedly sends `SIGTERM`
to backend applications. After the load finishes, the test shuts down the complete topology and checks graceful exits,
transaction stores, and application logs.

## Prerequisites

Before you run the test, install these commands:

- A JDK that matches the project build. The project currently targets Java 25.
- `bash`
- `curl`
- `find`
- `wrk`

Keep these local ports available:

- HTTP: `8080`, `8081`, `8082`, and `8083`
- Casual inbound and reverse outbound: `7771`, `7772`, `7785`, and `7786`
- Casual event servers: `7691` through `7694`

The harness runs the packaged Quarkus fast JARs. From the repository root, build the extension and example
applications:

```bash
./gradlew clean build publishToMavenLocal
cd example
./gradlew clean build
cd ..
```

## Run the test

From the repository root, run:

```bash
./example/scripts/chaos-soak-test.sh 2h 50 60 30 random-node
```

For a short high-churn verification run, use:

```bash
RESTART_PAUSE=1 ./example/scripts/chaos-soak-test.sh 10m 50 5 20 random-node
```

The positional arguments are:

| Position | Default | Description |
| :--- | :--- | :--- |
| `duration` | `2h` | Sets the `wrk` load duration, such as `10m`, `1h`, or `2h`. |
| `concurrency` | `50` | Sets the number of concurrent HTTP connections. |
| `chaos interval` | `60` | Sets the number of seconds between chaos cycles. |
| `grace period` | `30` | Sets how many seconds the harness waits after `SIGTERM` before it sends `SIGKILL`. |
| `mode` | `random-node` | Selects `random-node` or `all`. |

Set `RESTART_PAUSE` to a positive number of seconds to control the delay between a process exit and its relaunch. The
default is `5`. The rolling restart in `all` mode uses its own four-second pauses.

Use the modes as follows:

- `random-node` restarts either node application. The database and front applications remain running during the load.
- `all` cycles through node restarts, database restarts, and a rolling restart of both nodes.

The reverse topology uses [`casual-config-db-reverse.json`](../config/casual-config-db-reverse.json). The database
application opens one reverse inbound connection to each node. Each node exposes a reverse outbound listener for the
database connection. The front-to-node hop uses the standard outbound connections.

## Configure logging

Pass additional JVM system properties through `JAVA_OPTS`. For example, run with ERROR as the default application log
level while retaining the harness's focused shutdown diagnostics:

```bash
JAVA_OPTS='-Dquarkus.log.level=ERROR' \
  ./example/scripts/chaos-soak-test.sh 10m 50 5 20 random-node
```

The harness sets these categories to INFO:

- `se.laz.casual.network.outbound.DomainDisconnectHandler`
- `se.laz.casual.network.outbound.NettyNetworkConnection`
- `se.laz.casual.quarkus.CasualShutdownDelayHandler`

These categories report domain disconnects and transaction draining. If you set `quarkus.log.console.level` to
`ERROR`, the console handler suppresses their INFO messages. You can override an individual category by specifying its
level in `JAVA_OPTS`.

To enable Netty paranoid leak detection, run:

```bash
JAVA_OPTS='-Dio.netty.leakDetection.level=PARANOID' \
  ./example/scripts/chaos-soak-test.sh 1h 50 60 30 random-node
```

## Understand the result

Each run creates a unique directory under `example/logs/`. The directory contains:

- Application logs for the front, node, and database applications.
- `wrk.log` with throughput and latency results.
- `wrk_errors.log` with non-successful HTTP responses.
- `ObjectStore/` with separate transaction stores for `front`, `node1`, `node2`, and `db`.
- `in-doubt-files.txt` with any files that remain in those transaction stores after shutdown.

The script fails when any of these conditions occurs:

- The load generator fails or records no successful request.
- No chaos event occurs.
- An application exits unexpectedly or does not recover within the readiness window.
- A transaction-store file remains after orderly shutdown.
- An application exceeds the grace period and requires `SIGKILL`.
- A Netty leak warning appears in an application log.

Non-2xx responses during a shutdown window remain diagnostic results and do not fail the run. A request can already be
in transit when a node starts disconnecting. A downstream `TPENOENT` can then become `TPESVCFAIL` at the front
application. See [Graceful shutdown](../../graceful-shutdown.md#expected-failures-during-shutdown) for the expected
failure sequence.

Readiness checks require all four HTTP listeners to respond and require one successful transactional request through
the front application. The checks do not prove that each node individually serves traffic. Each recovery check has a
120-second deadline.

If a run stops before orderly shutdown, inspect its preserved log directory. The exit handler stops remaining
processes, but that cleanup does not provide a successful transaction-store verification.
