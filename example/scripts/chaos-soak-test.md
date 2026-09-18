# Run the chaos soak test

To verify that no in-doubt transaction records remain after repeated node restarts under transactional load, build the example applications and install `java`, `curl`, and `wrk`. Keep the example application ports available for the test.

From the repository root, run the script:

```bash
./example/scripts/chaos-soak-test.sh 2h 50 60 30 random-node
```

The arguments specify duration, concurrent connections, interval between chaos cycles in seconds, shutdown grace period in seconds, and mode. Allow enough duration for at least one chaos event.

To change the pause between process exit and relaunch for individual application restarts, set `RESTART_PAUSE` to a positive integer in seconds. The default is 5 seconds. The rolling restart sequence in `all` mode retains its 4-second pauses. For example, `RESTART_PAUSE=1 ./example/scripts/chaos-soak-test.sh 10m 50 5 20 random-node` uses a 1-second pause and a 20-second shutdown grace period.

- `random-node` restarts either node application. The database application stays running during the load phase.
- `all` retains the broader sequence that also restarts the database application and performs rolling node restarts.

Each run creates a unique directory under `example/logs/`. Its `ObjectStore` directory contains separate stores for `front`, `node1`, `node2`, and `db`. Each application reuses its directory and transaction-manager node name across restarts. The script sets absolute filesystem store paths through the [Quarkus transaction-manager configuration](https://quarkus.io/guides/transaction/). Previous runs and their records are preserved.

The script requires successful load-generator completion, at least one chaos event, and at least one successful load response. Failed requests during shutdown windows remain diagnostic information; they do not independently fail the test.

The script enables INFO logging for `DomainDisconnectHandler` and `CasualShutdownDelayHandler`, including when you set the default log level to ERROR. These categories report the remote domain and channel when disconnect arrives, and separate inbound and outbound transaction-entry counts when shutdown starts or draining times out. Inbound counts are approximate during concurrent updates; the two counts do not represent distinct global transactions. To override either category, set its level in `JAVA_OPTS`.

Readiness checks require all application HTTP listeners to respond and a successful transactional response through the front end. They do not establish that each node individually serves traffic. Recovery checks use bounded requests and a 120-second retry window; a request already in progress can finish after that window.

After orderly shutdown, the script scans each configured store and writes remaining file paths to `in-doubt-files.txt`. Missing or inaccessible stores and scan errors fail verification. Remaining store files, forced shutdowns, and detected Netty leak warnings also fail the run. An empty store scan checks for remaining transaction records; it does not reconcile database contents.

If the run fails before orderly shutdown, inspect the preserved application logs and stores. The exit handler stops the processes, but that cleanup does not constitute a successful transaction-store verification.
