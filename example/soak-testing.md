[//]: # (-*- coding: utf-8-unix -*-)
# Soak testing

## The applications

### DB Application
Uses an in memory db

To see if there are any indoubt transactions:
```sh
find ObjectStore -type f 2>/dev/null
```

Run as:
```sh
CASUAL_CALLER_CONFIG_FILE=$(pwd)/config/caller-config.json CASUAL_CONFIG_FILE=$(pwd)/config/casual-config-db.json CASUAL_FIELD_TABLE=$(pwd)/config/casual-fields.json java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5020 -jar db-app/build/db-app-1.0.0-runner.jar &> logs/db.log
```

### Node application

Connects to the DB application

Run two instances such as:
```sh
CASUAL_CALLER_CONFIG_FILE=$(pwd)/config/caller-config.json CASUAL_CONFIG_FILE=$(pwd)/config/casual-config-node-one.json CASUAL_FIELD_TABLE=$(pwd)/config/casual-fields.json java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 -jar node-app/build/node-app-1.0.0-runner.jar &> logs/node1.log
```

```sh
QUARKUS_PROFILE=two CASUAL_CALLER_CONFIG_FILE=$(pwd)/config/caller-config.json CASUAL_CONFIG_FILE=$(pwd)/config/casual-config-node-two.json CASUAL_FIELD_TABLE=$(pwd)/config/casual-fields.json java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5006 -jar node-app/build/node-app-1.0.0-runner.jar &> logs/node2.log
```

### Front application

Connects to the node applications

Run as:
```sh
CASUAL_CALLER_CONFIG_FILE=$(pwd)/config/caller-config.json CASUAL_CONFIG_FILE=$(pwd)/config/casual-config-front.json CASUAL_FIELD_TABLE=$(pwd)/config/casual-fields.json java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5010 -jar front-app/build/front-app-1.0.0-runner.jar &> logs/front.log
```

## Soak test

You need to have `wrk` installed for the soak testing.
The scripts are under ./scripts.

Run as:
```sh
./scripts/soak-test.sh
```

### Chaos testing

Send one of the node apps to background and then kill it such as:
```sh
kill %1
```

while the soak testing is running.

Then check the logs for any errors, there should also be zero indoubt transactions.

See [graceful shutdown](../graceful-shutdown.md)

## Soak testing with reverse outbound

The same topology but with the node to db hop reversed - each node configures a reverse outbound
listener (`casual-config-node-{one,two}-reverse.json`) and the db application connects to both of them
(`casual-config-db-reverse.json`). The `db_counter` calls, including the XA two phase commit traffic, then
travel over connections established by the db application. The front to node hop is unchanged.

Run the db application:
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

The front application runs exactly as before, then run the soak test as before.

### Chaos testing with reverse outbound

While the soak test is running:

- kill one of the node applications - the db application loses its reverse inbound connections towards it and
  reconnects with backoff once the node is back, the front fails over to the other node in the meantime.
- kill the db application - the reverse pools in both nodes empty out and calls fail as when casual is down,
  they are refilled when the db application is restarted and reconnects.
- bring the system down gracefully, in order: front, nodes, db.

After each scenario, check the logs for errors and verify that there are zero indoubt transactions in the
db application:
```sh
find ObjectStore -type f 2>/dev/null
```
The command must return nothing - anything else means a transaction was left in doubt.
