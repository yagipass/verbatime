# quarkus

Quarkus 3.39 in JVM mode with `quarkus-rest-jackson`, packaged as the default fast-jar in
`target/quarkus-app/` and run with `java -jar quarkus-run.jar`. Quarkus generates a large part of
its wiring at build time: ArC beans, client proxies, and the REST handler chain. This example
shows what a trace looks like when much of the framework is generated code.

## Run

```sh
docker compose up --build
```

The JVM pauses at `main()` until JMC starts a recording. That flow, the `load` profile, and the
shutdown are the same for every example and are described once in
[Running an example](../README.md#running-an-example). Exercise it with:

```sh
curl 'http://localhost:8080/orders?sku=widget&qty=3'
```

## Agent settings

Set in [`compose.yaml`](compose.yaml) through `JAVA_TOOL_OPTIONS`, alongside the shared JMX flags:

```text
-javaagent:/app/verbatime-agent.jar=waitstart=60s,roots=io.quarkus.runtime.Application::start+org.jboss.resteasy.reactive.common.core.AbstractResteasyReactiveContext::run
--enable-native-access=ALL-UNNAMED
```

| Setting | Value |
|---|---|
| Instrumented classes | Quarkus, Vert.x, Netty, the generated ArC and REST classes, and the application |
| `waitstart=60s` | Pauses at the fast-jar's `QuarkusEntryPoint.main` for up to 60 seconds until JMC starts a recording |
| `roots=` | Sets the roots below. You can change them in JMC |

## Roots to try

| Root | What one tree covers |
|---|---|
| `io.quarkus.runtime.Application::start` | Application startup, when the recording is started while the JVM is paused at `main()`: runtime initialization of every extension, ArC, the Vert.x HTTP server, and the application's `Warmup` bean, whose `StartupEvent` observer places one warm-up order. |
| `org.jboss.resteasy.reactive.common.core.AbstractResteasyReactiveContext::run` | The REST handler chain for one request. The resource method is blocking, so a request is two trees: routing and the blocking hand-off on `vert.x-eventloop-thread-N`, then parameter extraction, the resource method and workload, Jackson, and the response write on `executor-thread-N`. |

## Endpoints

| Path | Response |
|---|---|
| `GET /healthz` | `ok` |
| `GET /orders?sku=widget&qty=3` | `{"sku":"widget","qty":3,"cents":...,"txId":"..."}` |
| `GET /orders?sku=widget&qty=500` | `409` from `OutOfStockException`, mapped by a `@ServerExceptionMapper` |
