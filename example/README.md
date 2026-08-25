[//]: # (-*- coding: utf-8-unix -*-)
# Example project

## Applications

* `example-app`: An application to test calls to and from Casual or another `example-app` instance, including custom handlers.
* `db-app`, `node-app`, `front-app`: Applications used for [Soak testing](soak-testing.md).

## Example app

Build and run the example:

```bash
cd example
CASUAL_CONFIG_FILE=$(pwd)/config/casual-config.json CASUAL_FIELD_TABLE=$(pwd)/config/casual-fields.json ./gradlew :example-app:quarkusDev
```

Alternatively, when running from the packaged uber-jar:
```bash
CASUAL_CONFIG_FILE=$(pwd)/config/casual-config.json CASUAL_FIELD_TABLE=$(pwd)/config/casual-fields.json java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 -jar example-app/build/example-app-1.0.0-runner.jar
```

To run the examples without an external Casual instance, start two `example-app` instances as peers:

```sh
CASUAL_CONFIG_FILE=$(pwd)/config/casual-config.json CASUAL_FIELD_TABLE=$(pwd)/config/casual-fields.json ./gradlew :example-app:quarkusDev
```

and

```sh
QUARKUS_PROFILE=peer CASUAL_CONFIG_FILE=$(pwd)/config/casual-config-domain-two.json CASUAL_FIELD_TABLE=$(pwd)/config/casual-fields.json ./gradlew :example-app:quarkusDev
```

Alternatively, when running from packaged uber-jars:

```sh
CASUAL_CONFIG_FILE=$(pwd)/config/casual-config.json CASUAL_FIELD_TABLE=$(pwd)/config/casual-fields.json java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 -jar example-app/build/example-app-1.0.0-runner.jar
```

and

```sh
QUARKUS_PROFILE=peer CASUAL_CONFIG_FILE=$(pwd)/config/casual-config-domain-two.json CASUAL_FIELD_TABLE=$(pwd)/config/casual-fields.json java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5006 -jar example-app/build/example-app-1.0.0-runner.jar
```

Test the fielded service:
```sh
$curl 'localhost:8080/casual/simpleObject?id=42&name=bob'
SimpleObject{id=42, name='bob'}
```

Test the echo service:
```sh
$curl -X POST -d @curl-data -H 'Content-Type: application/casual-x-octet' http://localhost:8080/casual/echo
Bazinga!
```

Test the sum service, which uses the custom `JsonBufferHandler`:
```sh
curl -X POST -d @sum.json -H 'Content-Type: application/casual-x-octet' http://localhost:8080/casual/sum?bufferType=.json/
```

The example application running with the `peer` profile exposes port 8000 instead of 8080. When modifying custom buffer handlers or SPI extensions, restart the application before testing with `quarkusDev`, because handlers are discovered at build time.

## Reverse outbound and reverse inbound

Reverse Outbound and Reverse Inbound are symmetric features. You can pair two `example-app` instances: one listens (Reverse Outbound) and the other connects to it (Reverse Inbound). Once connected, the listening instance can call services exposed on the connecting instance as if it had a standard outbound connection towards it.

1. Start the reverse outbound instance listening on port `7780` (`casual-config-reverse-outbound.json`) with its `casual` pool backed by the reverse pool (`revout` profile):

```sh
QUARKUS_PROFILE=revout CASUAL_CONFIG_FILE=$(pwd)/config/casual-config-reverse-outbound.json CASUAL_FIELD_TABLE=$(pwd)/config/casual-fields.json ./gradlew :example-app:quarkusDev
```

2. Start the reverse inbound instance connecting to `localhost:7780` with two connections (`casual-config-reverse-inbound.json`):

```sh
QUARKUS_PROFILE=peer CASUAL_CONFIG_FILE=$(pwd)/config/casual-config-reverse-inbound.json CASUAL_FIELD_TABLE=$(pwd)/config/casual-fields.json ./gradlew :example-app:quarkusDev
```

3. Call a service on the reverse outbound instance to send a request over the connection established by the reverse inbound instance:

```sh
$curl -X POST -d @curl-data -H 'Content-Type: application/casual-x-octet' http://localhost:8080/casual/echo
Bazinga!
```

Calls through `casual-caller` (`/casualcaller` instead of `/casual`) use the per-instance connection factory entries. With $N$ connected reverse inbound instances, `casual-caller` manages $N$ distinct virtual pools, each with its own service discovery, validity state, and failover priority.
