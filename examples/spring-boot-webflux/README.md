# spring-boot-webflux

Spring Boot 4.1 WebFlux on Reactor Netty. The point of this example is what a reactive server does
to the "one root execution is one tree" model: the agent records one tree per thread, and a
reactive request may run on more than one.

## Run

```sh
docker compose up --build
```

The JVM pauses at `main()` until JMC starts a recording. That flow, the `load` profile, and the
shutdown are the same for every example and are described once in
[Running an example](../README.md#running-an-example). Exercise it with:

```sh
curl 'http://localhost:8080/orders?sku=widget&qty=3'
curl 'http://localhost:8080/orders/async?sku=widget&qty=3'
```

## Agent settings

Set in [`compose.yaml`](compose.yaml) through `JAVA_TOOL_OPTIONS`, alongside the shared JMX flags:

```text
-javaagent:/app/verbatime-agent.jar=waitstart=60s,roots=org.springframework.boot.SpringApplication::run+reactor.netty.http.server.HttpServerOperations::onInboundNext
--enable-native-access=ALL-UNNAMED
```

| Setting | Value |
|---|---|
| Instrumented classes | Spring, Reactor, Netty, and the application |
| `waitstart=60s` | Pauses at the fat jar's `JarLauncher.main` for up to 60 seconds until JMC starts a recording |
| `roots=` | Sets the roots below. You can change them in JMC |

## Two endpoints, two shapes

`GET /orders` runs the workload inside `Mono.fromSupplier` and therefore on the event loop thread
`reactor-http-nio-N` that received the request. This is deliberately blocking, because it makes the
whole request one tree.

`GET /orders/async` subscribes on `Schedulers.boundedElastic()`, so the WebFlux dispatch runs on the
event loop and the workload on a `boundedElastic-N` thread. With the request root below, the tree
ends when the pipeline is assembled, and the workload's time on the other thread is not in it.

## Roots to try

| Root | What one tree covers |
|---|---|
| `org.springframework.boot.SpringApplication::run` | Application startup, when the recording is started while the JVM is paused at `main()`: context refresh, Reactor Netty, and the application's `StartupRunner`, an `ApplicationRunner` that places one warm-up order. |
| `reactor.netty.http.server.HttpServerOperations::onInboundNext` | The event-loop half of a request. Reactor Netty subscribes to the WebFlux pipeline inside this call, so for `/orders` it is the entire request, and for `/orders/async` everything except the workload. |

## Endpoints

| Path | Response |
|---|---|
| `GET /healthz` | `ok` |
| `GET /orders?sku=widget&qty=3` | `{"sku":"widget","qty":3,"cents":...,"txId":"..."}` on the event loop |
| `GET /orders/async?sku=widget&qty=3` | The same, computed on `boundedElastic` |
| `GET /orders?sku=widget&qty=500` | `409` from `OutOfStockException` |
