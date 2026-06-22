[//]: # (-*- coding: utf-8-unix -*-)
# casual-quarkus

A [Quarkus](https://quarkus.io/) extension for integrating with [Casual](https://github.com/casualcore/casual) middleware via [IronJacamar](https://docs.quarkiverse.io/quarkus-ironjacamar/dev/) (JCA).

Provides both **inbound**, **reverse inbound** (expose CDI beans as Casual services) and **outbound** (call external Casual services) connectivity.

Currently wraps up `Casual JCA 3.4.7` and `Casual Caller 3.3.3`

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
        "unmanaged": true,
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

When using `casual-caller` you can also use a configuration file, pointed to by the `CASUAL_CALLER_CONFIG_FILE` environment variable, in case you want to set transactions as sticky - multiple calls in the same transaction uses the same pool in case the services are available there.

```json
{
  "transactionStickyEnabled": true
}
```

### Make outbound calls

Inject the connection factory to call external Casual services:

```java
@Inject
@Identifier("casual")
CasualConnectionFactory connectionFactory;

public ServiceReturn<CasualBuffer> callService(String serviceName, CasualBuffer payload) 
{
    try (CasualConnection connection = connectionFactory.getConnection()) 
    {
        return connection.tpcall(serviceName, payload, Flag.of(AtmiFlags.NOFLAG));
    }
}
```
This is a very simple example, please see the example application for a more complete example which is heaviliy async and thus also makes use
of ```tpacall``` instead of tpcall.


### Multiple outbound pools

Additional pools can have any name. Only one pool `needs` to be named `casual`:

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

The [`example/`](example/README.md) directory contains standalone Quarkus applications that demonstrates:

- **Inbound services** -- `EchoServiceImpl`, `ReverseServiceImpl`, `FieldedServiceImpl` and `SumServiceImpl` exposed as Casual services via `@CasualService`
- **Outbound calls** -- a REST endpoint (`POST /casual/{serviceName}`) that uses `CasualConnectionFactory` with non-blocking `tpacall` and Mutiny `Uni<Response>`
- **A single outbound pool** -- one outbound pool configuration showing named pool setup
- **Virtual threads** -- enabled for non-blocking service handling
- **casual config file** -- showing basic usage. See [https://github.com/casualcore/casual-java](casual-java) for documentation
- **example fielded file** -- used in the fielded test service
- **user defined handlers** -- showing how a user application can implement their own ServiceHandler, BufferHandler and ServiceHandlerExtension
- **applications used for soak testing**

The example app consumes the extension from Maven Local. Build and install the extension first:

```bash
./gradlew clean build publishToMavenLocal
```

## XA note for quarkus

At REST endpoints and at exposed java services `@CasualService`:
```java
@Transactional(Transactional.TxType.REQUIRED)
```
By default, quarkus REST endpoints are non transactional.

## Performance Tuning for Quarkus

To achieve the best results with Casual JCA in Quarkus, we recommend the following configuration based on example app soak tests:

* Virtual Threads: Enable them! It allows the extension to handle thousands of concurrent requests without the memory overhead of platform threads.

* The Acquisition Bridge: If you see "Pinned Thread" warnings, ensure getConnection() is called via a platform executor (like Infrastructure.getDefaultExecutor()) to keep the IronJacamar pool logic away from the Virtual Thread carrier threads.

* Logical vs. Physical Connections: Don't be afraid to set a high JCA max-pool-size (e.g., 1000+). Because Casual JCA multiplexes over a few physical Netty connections, these "connections" are just lightweight logical handles.


## Gracful shutdown

How graceful shutdown works is documented [here](graceful-shutdown.md)


## Architecture

```
quarkus-casual/             (runtime module)
quarkus-casual-deployment/  (deployment module)
```

### Build-time (deployment module)

`CasualProcessor` uses Jandex to discover `@CasualService` annotations and produces `CasualServiceBuildItem`s. A `@Record(RUNTIME_INIT)` build step converts these into `CasualServiceDescriptor`s and calls `CasualServiceRecorder.registerServices()`, which resolves CDI bean instances and registers them in `CasualQuarkusServiceRegistry`.

It also finds SPI implementations at build time and produces `CasualSPIBuildItem`s. These are then registered at runtime via a build step that converts these into `CasualSPIDescriptor`s
and calls `CasualSPIRecorder.registerSpiImplementations` that pre registers the SPI implementations into Casual JCA's handler factories.
The handlers that can be overriden this way by a user application are:
* `se.laz.casual.jca.inbound.handler.buffer.BufferHandler`
* `se.laz.casual.jca.inbound.handler.service.ServiceHandler`
* `se.laz.casual.jca.inbound.handler.service.extension.ServiceHandlerExtension`
* `se.laz.casual.api.buffer.type.fielded.marshalling.FieldedMarshaller`


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
  -> CasualQuarkusServiceHandler
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

## Upgrading the Quarkus version

The Quarkus version is defined in two places:

1. **`versions.gradle`** — the `quarkus_version` variable, used by all build scripts for the platform BOM and deployment dependencies.
2. **`settings.gradle`** — the plugin version in the `pluginManagement` block. This must be a literal string because Gradle requires `pluginManagement` to be evaluated before any scripts are applied.

When upgrading, update both files to the same version. There is intentionally no `gradle.properties` in the extension — that file is reserved for local publishing credentials (signing keys, Maven Central tokens) and is never checked in.

## Related

- [Casual middleware](https://github.com/casualcore/casual)
- [Casual JCA](https://github.com/casualcore/casual-java)
- [Casual Caller](https://github.com/casualcore/casual-caller)
- [Quarkus IronJacamar extension](https://docs.quarkiverse.io/quarkus-ironjacamar/dev/)

