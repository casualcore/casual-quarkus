[//]: # (-*- coding: utf-8-unix -*-)
# Soak testing

## The applications

### Database application (db-app)

Uses an in-memory database.

To verify whether there are any in-doubt transactions:
```sh
find ObjectStore -type f 2>/dev/null
```

Run as:
```sh
CASUAL_CALLER_CONFIG_FILE=$(pwd)/config/caller-config.json CASUAL_CONFIG_FILE=$(pwd)/config/casual-config-db.json CASUAL_FIELD_TABLE=$(pwd)/config/casual-fields.json java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5020 -jar db-app/build/db-app-1.0.0-runner.jar &> logs/db.log
```

### Node application (node-app)

Connects to the database application.

Run two instances:
```sh
CASUAL_CALLER_CONFIG_FILE=$(pwd)/config/caller-config.json CASUAL_CONFIG_FILE=$(pwd)/config/casual-config-node-one.json CASUAL_FIELD_TABLE=$(pwd)/config/casual-fields.json java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 -jar node-app/build/node-app-1.0.0-runner.jar &> logs/node1.log
```

```sh
QUARKUS_PROFILE=two CASUAL_CALLER_CONFIG_FILE=$(pwd)/config/caller-config.json CASUAL_CONFIG_FILE=$(pwd)/config/casual-config-node-two.json CASUAL_FIELD_TABLE=$(pwd)/config/casual-fields.json java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5006 -jar node-app/build/node-app-1.0.0-runner.jar &> logs/node2.log
```

### Front application (front-app)

Connects to the node applications.

Run as:
```sh
CASUAL_CALLER_CONFIG_FILE=$(pwd)/config/caller-config.json CASUAL_CONFIG_FILE=$(pwd)/config/casual-config-front.json CASUAL_FIELD_TABLE=$(pwd)/config/casual-fields.json java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5010 -jar front-app/build/front-app-1.0.0-runner.jar &> logs/front.log
```

## Running the soak test

Install `wrk` before running the soak test. The execution scripts are located under `scripts/`:

Run as:
```sh
./scripts/soak-test.sh
```

### Chaos testing

While the soak test is running, simulate an unexpected crash by terminating one of the running node processes (e.g. `kill %1`). Check the logs for errors and confirm that there are zero in-doubt transactions.

For details on shutdown coordination, see [Graceful shutdown](../graceful-shutdown.md).

## Soak testing with reverse outbound

This topology reverses the node-to-database hop. Each node configures a Reverse Outbound listener (`casual-config-node-{one,two}-reverse.json`), and the database application connects to both nodes via Reverse Inbound (`casual-config-db-reverse.json`). The `db_counter` calls, including XA two-phase commit traffic, travel over the connections established by the database application. The front-to-node hop remains unchanged.

Run the database application:
```sh
CASUAL_CALLER_CONFIG_FILE=$(pwd)/config/caller-config.json CASUAL_CONFIG_FILE=$(pwd)/config/casual-config-db-reverse.json CASUAL_FIELD_TABLE=$(pwd)/config/casual-fields.json java -jar db-app/build/db-app-1.0.0-runner.jar &> logs/db.log
```

Run the node applications with the `reverse` profile:
```sh
QUARKUS_PROFILE=reverse CASUAL_CALLER_CONFIG_FILE=$(pwd)/config/caller-config.json CASUAL_CONFIG_FILE=$(pwd)/config/casual-config-node-one-reverse.json CASUAL_FIELD_TABLE=$(pwd)/config/casual-fields.json java -jar node-app/build/node-app-1.0.0-runner.jar &> logs/node1.log
```

```sh
QUARKUS_PROFILE=reverse,two CASUAL_CALLER_CONFIG_FILE=$(pwd)/config/caller-config.json CASUAL_CONFIG_FILE=$(pwd)/config/casual-config-node-two-reverse.json CASUAL_FIELD_TABLE=$(pwd)/config/casual-fields.json java -jar node-app/build/node-app-1.0.0-runner.jar &> logs/node2.log
```

Run the front application as before, then start the soak test script.

### Automated chaos soak test harness

To run automated continuous load while randomly terminating and restarting nodes:

```sh
# Run 1-hour chaos test with 50 connections
./scripts/chaos-soak-test.sh 1h 50 60 30 random-node

# Run with Netty PARANOID leak detection
JAVA_OPTS="-Dio.netty.leakDetection.level=PARANOID" ./scripts/chaos-soak-test.sh 1h 50 60 30 random-node
```

The script automatically launches the topology, generates transactional load via `wrk`, executes chaos events at the configured interval, performs an orderly graceful shutdown, scans `ObjectStore` for in-doubt transactions, audits graceful process exits, and checks application logs for Netty buffer leaks.

For detailed benchmarks and verification results, see [Chaos and Soak Test Results](test-results/chaos/README.md).

