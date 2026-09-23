# micronaut

Micronaut 5.1 with `micronaut-http-server-netty`, built with `micronaut-parent`, packaged as a
shaded jar, and run with `java -jar`. Micronaut resolves dependency injection at compile time
through annotation processors, so there is no reflection-based wiring at startup. This example
shows the generated `$Definition` classes in the trees as ordinary code.

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
-javaagent:/app/verbatime-agent.jar=waitstart=60s,roots=io.micronaut.runtime.Micronaut::start+io.micronaut.http.server.netty.RoutingInBoundHandler::accept
--enable-native-access=ALL-UNNAMED
```

| Setting | Value |
|---|---|
| Instrumented classes | Micronaut, Netty, the generated bean definitions, and the application |
| `waitstart=60s` | Pauses at `Application.main` for up to 60 seconds until JMC starts a recording |
| `roots=` | Sets the roots below. You can change them in JMC |

## Roots to try

| Root | What one tree covers |
|---|---|
| `io.micronaut.runtime.Micronaut::start` | Application startup, when the recording is started while the JVM is paused at `main()`: the application context start, bean creation from the precomputed definitions, the `StartupEvent` that makes the application's `Warmup` bean place one order, and the Netty bind. |
| `io.micronaut.http.server.netty.RoutingInBoundHandler::accept` | One HTTP request on `default-eventLoopGroup-2-N`: route matching, argument binding, the controller with the workload, and writing the response. |

The controller stays on the event loop on purpose. With `@ExecuteOn(TaskExecutors.BLOCKING)` the
workload would move to a virtual thread and the tree would end at the hand-off.

## Endpoints

| Path | Response |
|---|---|
| `GET /healthz` | `ok` |
| `GET /orders?sku=widget&qty=3` | `sku=widget qty=3 cents=... tx=...` |
| `GET /orders?sku=widget&qty=500` | `409` from `OutOfStockException`, mapped by an `@Error` handler |
