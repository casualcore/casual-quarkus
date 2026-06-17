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
