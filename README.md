# casual-quarkus

A [Quarkus](https://quarkus.io/) extension for integrating with [Casual](https://github.com/casualcore/casual) middleware via [IronJacamar](https://docs.quarkiverse.io/quarkus-ironjacamar/dev/) (JCA).

Provides both **inbound** (expose CDI beans as Casual services) and **outbound** (call external Casual services) connectivity.

Currently wraps up Casual JCA 3.4.2.

## Getting Started

### Add the dependency

**Gradle:**
```groovy
implementation 'se.laz.casual:casual-quarkus:1.0.0
```

**Maven:**
```xml
<dependency>
    <groupId>se.laz.casual</groupId>
    <artifactId>casual-quarkus</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Define a service

Annotate any CDI bean method with `@CasualService` to expose it as a Casual service:

```java
@ApplicationScoped
public class MyService {

    @CasualService(name = "myService", category = "business")
    public InboundResponse handle(InboundRequest request) {
        byte[] payload = request.getBuffer().getBytes().get(0);
        // business logic ...
        return InboundResponse.createBuilder()
                .buffer(OctetBuffer.of(result))
                .build();
    }
}
```

Services are discovered at **build time** via Jandex and registered during runtime init through a Quarkus recorder -- no runtime classpath scanning.

### Configure the resource adapter in your user application

In `application.properties`:

```properties
# Resource adapter (at least one pool must be named "casual" for inbound to work)
quarkus.ironjacamar.casual.ra.kind=casual
quarkus.ironjacamar.casual.ra.config.host=my-casual-host
quarkus.ironjacamar.casual.ra.config.port=7771
quarkus.ironjacamar.casual.ra.config.inbound-server-port=7772
quarkus.ironjacamar.casual.ra.config.network-connection-pool-size=1
quarkus.ironjacamar.casual.ra.config.network-connection-pool-name=casual-pool
# we do not want calls to block a whole thread
quarkus.virtual-threads.enabled=true
```

You also need a `casual-config.json` pointed to by the `CASUAL_CONFIG_FILE` environment variable:

```json
{
    "domain": {
        "name": "quarkus-casual-domain"
    },
    "unmanaged": true,
    "outbound":{
        "useEpoll": true
    },
    "inbound": {
        "useEpoll": true,
        "startup": {
            "mode": "immediate"
        }
    },
    "eventServer":{
        "portNumber": 7698,
        "useEpoll": true
    }
}
```

### Make outbound calls

Inject the connection factory to call external Casual services:

```java
@Inject
@Identifier("casual")
CasualConnectionFactory connectionFactory;

public ServiceReturn<CasualBuffer> callService(String serviceName, CasualBuffer payload) {
    try (CasualConnection connection = connectionFactory.getConnection()) {
        return connection.tpcall(serviceName, payload, Flag.of());
    }
}
```
This is a very simple example, please see the example application for a more complete example which is heaviliy async and thus also makes use
of ```tpacall``` instead of tpcall.


### Multiple outbound pools

Additional pools can have any name. Only one pool needs to be named `casual` for inbound/reverse-inbound to work:

```properties
quarkus.ironjacamar.casual.ra.kind=casual
quarkus.ironjacamar.casual.ra.config.host=host-a
quarkus.ironjacamar.casual.ra.config.port=7771
quarkus.ironjacamar.casual.ra.config.inbound-server-port=7772

quarkus.ironjacamar.other-pool.ra.kind=casual
quarkus.ironjacamar.other-pool.ra.config.host=host-b
quarkus.ironjacamar.other-pool.ra.config.port=7771
```

## Example Application

The [`example/`](example/) directory contains a standalone Quarkus application that demonstrates:

- **Inbound services** -- `EchoServiceImpl`, `ReverseServiceImpl`, and `FieldedServiceImpl` exposed as Casual services via `@CasualService`
- **Outbound calls** -- a REST endpoint (`POST /casual/{serviceName}`) that uses `CasualConnectionFactory` with non-blocking `tpacall` and Mutiny `Uni<Response>`
- **A single outbound pool** -- one outbound pool configuration showing named pool setup
- **Virtual threads** -- enabled for non-blocking service handling
- **casual config file** -- showing basic usage, including reverse inbound. See [https://github.com/casualcore/casual-java](casual-java) for documentation
- **example fielded file** -- used in the fielded test service
- **user defined handlers** -- showing how a user application can implement their own ServiceHandler, BufferHandler and ServiceHandlerExtension

The example app consumes the extension from Maven Local. Build and install the extension first:

```bash
./gradlew clean build publishToMavenLocal
```

Then build and run the example:

```bash
cd example
CASUAL_CONFIG_FILE=$(pwd)/casual-config.json CASUAL_FIELD_TABLE=$(pwd)/casual-fields.json ./gradlew quarkusDev ./gradlew quarkusDev
```

or ( if you build a fat jar and want to test that)
```bash
CASUAL_CONFIG_FILE=$(pwd)/casual-config.json CASUAL_FIELD_TABLE=$(pwd)/casual-fields.json java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 -jar example-app/build/example-app-1.0.0-runner.jar
```

To run the examples with no casual, you can start two instances of the casual quarkus example-app such as:
```sh
CASUAL_CONFIG_FILE=$(pwd)/casual-config.json CASUAL_FIELD_TABLE=$(pwd)/casual-fields.json ./gradlew quarkusDev
```

and

```sh
QUARKUS_PROFILE=peer CASUAL_CONFIG_FILE=$(pwd)/casual-config-domain-two.json CASUAL_FIELD_TABLE=$(pwd)/casual-fields.json ./gradlew quarkusDev
```

or when using the fat jar:
```sh
CASUAL_CONFIG_FILE=$(pwd)/casual-config.json CASUAL_FIELD_TABLE=$(pwd)/casual-fields.json java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 -jar example-app/build/example-app-1.0.0-runner.jar
```

and

```sh
QUARKUS_PROFILE=peer CASUAL_CONFIG_FILE=$(pwd)/casual-config-domain-two.json CASUAL_FIELD_TABLE=$(pwd)/casual-fields.json java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5006 -jar example-app/build/example-app-1.0.0-runner.jar
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

Note that the example application with the ```peer``` profile is exposing port 8000 instead of 8080.
Also note that if you want to run the examples that uses their own buffer handlers etc, you need to use the fat jar version as SPI will not work for
the user application types when using ```quarkusDev```.


## Architecture

```
quarkus-casual/             (runtime module)
quarkus-casual-deployment/  (deployment module)
```

### Build-time (deployment module)

`CasualProcessor` uses Jandex to discover `@CasualService` annotations and produces `CasualServiceBuildItem`s. A `@Record(RUNTIME_INIT)` build step converts these into `CasualServiceDescriptor`s and calls `CasualServiceRecorder.registerServices()`, which resolves CDI bean instances and registers them in `CasualQuarkusServiceRegistry`.

### Runtime

| Class | Role |
|---|---|
| `CasualServiceRecorder` | Recorder that registers build-time-discovered services at runtime init |
| `CasualQuarkusServiceRegistry` | Holds service name to (bean, method) mappings |
| `CasualQuarkusServiceHandler` | SPI `ServiceHandler` (priority LEVEL_3) that dispatches inbound calls to CDI beans |
| `CasualQuarkusResourceAdapterFactory` | IronJacamar `ResourceAdapterFactory` creating RA instances, connection factories, and activation specs |
| `CasualQuarkusResourceAdapter` | `ResourceAdapter` managing lifecycle; ensures only one inbound server starts across multiple pools |
| `CasualMessageEndpoint` | CDI `@ResourceEndpoint` handling the Casual inbound protocol |

### Inbound request flow

```
Casual client
  -> inbound server
  -> CasualMessageEndpoint
  -> CasualQuarkusServiceHandler (LEVEL_3, preferred)
  -> service method
  -> InboundResponse back to client
```

## Building the extension

```bash
./gradlew clean build
```

Install to Maven Local for consumption by application projects:

```bash
./gradlew clean build publishToMavenLocal
```

Requires Java 25+.

## Related

- [Casual middleware](https://github.com/casualcore/casual)
- [Quarkus IronJacamar extension](https://docs.quarkiverse.io/quarkus-ironjacamar/dev/)
- [Quarkus extension guide](https://quarkus.io/guides/writing-extensions)
