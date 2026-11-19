[//]: # (-*- coding: utf-8-unix -*-)
# Example project

## Applications

* `example-app`: An application to test calls to and from Casual or another `example-app` instance, including custom handlers.
* `db-app`, `node-app`, `front-app`: Applications used for [soak testing](soak-testing.md) and
  [automated chaos testing](scripts/chaos-soak-test.md).

## Example app

### Run one application in loopback mode

To run the examples without an external Casual instance, use the `loopback` profile. This profile connects the normal
outbound pool to the same application's inbound listener on port `7772`:

```bash
cd example
QUARKUS_PROFILE=loopback CASUAL_CONFIG_FILE=$(pwd)/config/casual-config.json CASUAL_FIELD_TABLE=$(pwd)/config/casual-fields.json ./gradlew :example-app:quarkusDev
```

Alternatively, from the `example` directory, build the fast JAR from the current sources and run it:

```bash
./gradlew :example-app:quarkusBuild
QUARKUS_PROFILE=loopback CASUAL_CONFIG_FILE=$(pwd)/config/casual-config.json CASUAL_FIELD_TABLE=$(pwd)/config/casual-fields.json java --enable-native-access=ALL-UNNAMED -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 -jar example-app/build/quarkus-app/quarkus-run.jar
```

Requests issued through the application's outbound connection return through its inbound listener and invoke the CDI
Casual services in the same process. This mode is useful for testing services, custom handlers, transactions, and
`quarkusDev` hot reload with one application.

### Run two applications as peer domains

To test communication across two Casual domains, start two `example-app` instances as peers:

```sh
CASUAL_CONFIG_FILE=$(pwd)/config/casual-config.json CASUAL_FIELD_TABLE=$(pwd)/config/casual-fields.json ./gradlew :example-app:quarkusDev
```

and

```sh
QUARKUS_PROFILE=peer CASUAL_CONFIG_FILE=$(pwd)/config/casual-config-domain-two.json CASUAL_FIELD_TABLE=$(pwd)/config/casual-fields.json ./gradlew :example-app:quarkusDev
```

Alternatively, run the packaged fast JARs:

```sh
CASUAL_CONFIG_FILE=$(pwd)/config/casual-config.json CASUAL_FIELD_TABLE=$(pwd)/config/casual-fields.json java --enable-native-access=ALL-UNNAMED -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 -jar example-app/build/quarkus-app/quarkus-run.jar
```

and

```sh
QUARKUS_PROFILE=peer CASUAL_CONFIG_FILE=$(pwd)/config/casual-config-domain-two.json CASUAL_FIELD_TABLE=$(pwd)/config/casual-fields.json java --enable-native-access=ALL-UNNAMED -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5006 -jar example-app/build/quarkus-app/quarkus-run.jar
```

Test the fielded service:
```sh
curl 'localhost:8080/casual/simpleObject?id=42&name=bob'
SimpleObject{id=42, name='bob'}
```

Test the echo service:
```sh
curl -X POST -d @curl-data -H 'Content-Type: application/casual-x-octet' http://localhost:8080/casual/echo
Bazinga!
```

Test the sum service, which uses the custom `JsonBufferHandler`:
```sh
curl -X POST -d @sum.json -H 'Content-Type: application/casual-x-octet' http://localhost:8080/casual/sum?bufferType=.json/
```

The example application running with the `peer` profile exposes port 8000 instead of 8080. When modifying custom buffer handlers or SPI extensions, restart the application before testing with `quarkusDev`, because handlers are discovered at build time.

### Intercept an inbound service call

The [`callMetadata` service method](example-app/src/main/java/se/laz/casual/example/CallMetadataService.java)
uses [`@ShouldLog`](example-app/src/main/java/se/laz/casual/example/ShouldLog.java), a CDI interceptor binding.
The [`ServiceLoggingInterceptor`](example-app/src/main/java/se/laz/casual/example/ServiceLoggingInterceptor.java)
declares the same binding and uses `@Priority` to enable the interceptor. Its `@AroundInvoke` method logs the method
name and calls `context.proceed()` to continue the invocation.

Start the example in [loopback mode](#run-one-application-in-loopback-mode), then run:

```sh
curl -fsS -H 'breadcrumb: 68c3d69f-5e44-4131-bf7b-345a882736ca' http://localhost:8080/casualcallersync/call-metadata
```

The HTTP response contains the same UUID. In the receiving application's log, look for
`Invoking service method callMetadata` at `INFO` level. The binding is on the inbound service method, so with two
peer applications you see this entry on the peer that handles the Casual call.

## Propagate per-call metadata

Casual Quarkus activates the CDI request context while it invokes an inbound Casual service. You can therefore use a
`@RequestScoped` bean to make metadata available throughout one service call.

The [`user-handlers`](user-handlers) project demonstrates this pattern:

* [`ExampleServiceHandlerExtension`](user-handlers/src/main/java/se/laz/casual/example/user/handlers/ExampleServiceHandlerExtension.java)
  reads a `breadcrumb` UUID from the service-call headers. Headers require Casual protocol version 1.5 or later.
* [`CallMetadata`](user-handlers/src/main/java/se/laz/casual/example/user/handlers/CallMetadata.java) stores the
  breadcrumb for the current call.
* [`CallMetadataService`](example-app/src/main/java/se/laz/casual/example/CallMetadataService.java)
  injects `CallMetadata` and reads the breadcrumb without depending on the inbound request buffer.

The extension initializes `CallMetadata` in `ServiceHandlerExtension.before()`. Casual Quarkus keeps the request
context active through the service invocation and `ServiceHandlerExtension.after()`, then destroys the request-scoped
instance. Use the same pattern for correlation identifiers, tenant information, authentication context, locale, or
other call-scoped data.

See [`CallMetadataPropagationTest`](user-handlers/src/test/java/se/laz/casual/example/user/handlers/CallMetadataPropagationTest.java)
for the header extraction example. [`CallMetadataEndToEndTest`](example-app/src/test/java/se/laz/casual/example/CallMetadataEndToEndTest.java)
passes the header through a real protocol 1.5 service call and verifies the value returned by the inbound service.

From the `example` directory, run the end-to-end test with the following command:

```sh
./gradlew :example-app:test --tests se.laz.casual.example.CallMetadataEndToEndTest
```

### Understand the CDI lifecycle

`ExampleServiceHandlerExtension` is an application-scoped CDI bean. When CDI creates it, CDI calls the injected
`setCallMetadata()` method once. The injected value is a proxy for the request-scoped `CallMetadata` bean, not the
metadata instance for a specific service call. Quarkus can create the application-scoped bean lazily, but CDI always
completes injection before invoking any method on the bean.

For each inbound service call, Casual Quarkus performs the following steps:

1. `CasualQuarkusServiceHandler` activates a CDI request context.
2. `ExampleServiceHandlerExtension.before()` reads the breadcrumb header and calls `CallMetadata.setBreadcrumb()`.
   The proxy resolves to the `CallMetadata` instance associated with the active request context.
3. `CallMetadataService` reads through its injected `CallMetadata` proxy, which resolves to the same instance.
4. `ExampleServiceHandlerExtension.after()` runs while the request context remains active.
5. `CasualQuarkusServiceHandler` terminates the request context and CDI destroys that call's `CallMetadata` instance.

The next inbound call reuses the application-scoped extension and its proxy. Because the call has a new request
context, the proxy resolves to a new `CallMetadata` instance.

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
curl -X POST -d @curl-data -H 'Content-Type: application/casual-x-octet' http://localhost:8080/casualcaller/echo
Bazinga!
```

Calls through `casual-caller` use per-instance connection factory entries. With $N$ connected reverse inbound instances, `casual-caller` manages $N$ distinct virtual pools, each with its own service discovery, validity state, and failover priority.

The example Gradle build adds `--enable-native-access=ALL-UNNAMED` to every `quarkusDev` application JVM. This option
allows Netty to load its native epoll transport without restricted-native-access warnings on JDK 24 or later.
 
## Soak and chaos testing

For instructions on running the load and failure injection harnesses, see [Soak testing](soak-testing.md).

For the automated topology launcher, failure injection, and transaction-integrity checks, see
[Run the chaos soak test](scripts/chaos-soak-test.md).
