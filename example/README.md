[//]: # (-*- coding: utf-8-unix -*-)
# Example project

## Applications

`example-app` - an application that you can use for testing calls towards another `example-app` or to/from casual.
You can also use it to test using your own handlers.

`db`, `node`, `front` - applications that are used in [soak testing](soak-testing.md)

## Example app

Build and run the example:

```bash
cd example
CASUAL_CONFIG_FILE=$(pwd)/config/casual-config.json CASUAL_FIELD_TABLE=$(pwd)/config/casual-fields.json ./gradlew :example-app:quarkusDev
```

or ( if you build a fat jar and want to test that)
```bash
CASUAL_CONFIG_FILE=$(pwd)/config/casual-config.json CASUAL_FIELD_TABLE=$(pwd)/config/casual-fields.json java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 -jar example-app/build/example-app-1.0.0-runner.jar
```

To run the examples with no casual, you can start two instances of the casual quarkus example-app such as:
```sh
CASUAL_CONFIG_FILE=$(pwd)/config/casual-config.json CASUAL_FIELD_TABLE=$(pwd)/config/casual-fields.json ./gradlew :example-app:quarkusDev
```

and

```sh
QUARKUS_PROFILE=peer CASUAL_CONFIG_FILE=$(pwd)/config/casual-config-domain-two.json CASUAL_FIELD_TABLE=$(pwd)/config/casual-fields.json ./gradlew :example-app:quarkusDev
```

or when using the fat jar:
```sh
CASUAL_CONFIG_FILE=$(pwd)/config/casual-config.json CASUAL_FIELD_TABLE=$(pwd)/config/casual-fields.json java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 -jar example-app/build/example-app-1.0.0-runner.jar
```

and

```sh
QUARKUS_PROFILE=peer CASUAL_CONFIG_FILE=$(pwd)/config/casual-config-domain-two.json CASUAL_FIELD_TABLE=$(pwd)/config/casual-fields.json java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5006 -jar example-app/build/example-app-1.0.0-runner.jar
```

You can then test, for instance the fielded service, as such:
```sh
$curl 'localhost:8080/casual/simpleObject?id=42&name=bob'
SimpleObject{id=42, name='bob'}
```

Or to test the echo service:
```sh
$curl -X POST -d @curl-data -H 'Content-Type: application/casual-x-octet' http://localhost:8080/casual/casual%2fexample%2fjava%2fecho
Bazinga!
```

To test the sum service, which uses the user applications ```JsonBufferHandler```:
```sh
curl -X POST -d @sum.json -H 'Content-Type: application/casual-x-octet' http://localhost:8080/casual/sum?bufferType=.json/
```


Note that the example application with the `peer` profile is exposing port 8000 instead of 8080.
Also note that if you want to run the examples that uses their own buffer handlers etc, you need to restart the application when you have changed the handlers even when running with `quarkusDev`. 
This since the handlers are found during build time of the user application.

There's a third example configuration file `casual-config-reverse-inbound.json` if you want to test reverse inbound.
Note, it needs a reverse outbound to connect to and currently only casual can provide that.
