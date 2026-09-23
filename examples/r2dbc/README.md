# r2dbc

Spring Boot 4.1 WebFlux with Spring Data R2DBC and the reactive PostgreSQL driver, packaged as a
fat jar and run with `java -jar` against a `postgres:18` container. It is the reactive counterpart
of [`jdbc-hibernate`](../jdbc-hibernate/). The request, the workload, and the insert are one
reactive pipeline, and the database round trip completes on whatever thread the driver resumes
on, so a request is more than one tree.

## Run

```sh
docker compose up --build
```

Compose starts Postgres first and waits for its healthcheck. The application JVM then pauses at
`main()` until JMC starts a recording. That flow, the `load` profile, and the shutdown are the
same for every example and are described once in
[Running an example](../README.md#running-an-example). Exercise it with:

```sh
curl 'http://localhost:8080/orders?sku=widget&qty=3'
curl http://localhost:8080/orders/recent
```

The application creates the schema from `schema.sql` on startup with `spring.sql.init.mode=always`,
run through R2DBC, and keeps no volume, so every `up` starts empty.

## Agent settings

Set in [`compose.yaml`](compose.yaml) through `JAVA_TOOL_OPTIONS`, alongside the shared JMX flags:

```text
-javaagent:/app/verbatime-agent.jar=waitstart=60s,roots=org.springframework.boot.SpringApplication::run+reactor.netty.http.server.HttpServerOperations::onInboundNext
--enable-native-access=ALL-UNNAMED
```

| Setting | Value |
|---|---|
| Instrumented classes | Spring, Reactor, Netty, Spring Data R2DBC, the PostgreSQL R2DBC driver, and the application |
| `waitstart=60s` | Pauses at the fat jar's `JarLauncher.main` for up to 60 seconds until JMC starts a recording |
| `roots=` | Sets the roots below. You can change them in JMC |

## Roots to try

| Root | What one tree covers |
|---|---|
| `org.springframework.boot.SpringApplication::run` | Application startup, when the recording is started while the JVM is paused at `main()`: context refresh, the R2DBC connection factory and the schema script, Reactor Netty, and the application's `StartupRunner`, an `ApplicationRunner` that places one warm-up order and blocks on its insert. |
| `reactor.netty.http.server.HttpServerOperations::onInboundNext` | The event-loop half of a request on `reactor-http-nio-N`: WebFlux dispatch, the controller, the workload, deliberately blocking inside `Mono.fromSupplier`, and issuing the insert. The driver's response, the entity mapping, the JSON encoding, and the response write run on the driver's own `reactor-tcp-nio-N` thread and are not in this tree. A second, tiny tree on the same thread handles the request's last HTTP content. |

The other half is reachable with `reactor.netty.channel.ChannelOperations::onInboundNext`, the
inbound handler of the driver's TCP connection: every batch of backend messages the driver reads
is one tree on `reactor-tcp-nio-N`, and the one carrying the inserted row continues through
`MappingR2dbcConverter`, `JacksonJsonEncoder`, and `HttpServerOperations.send`.

## Endpoints

| Path | Response |
|---|---|
| `GET /healthz` | `ok` |
| `GET /orders?sku=widget&qty=3` | The stored row as JSON, with its `id` |
| `GET /orders/{id}` | That row, or `404` |
| `GET /orders/recent` | The ten most recent rows |
| `GET /orders?sku=widget&qty=500` | `409` from `OutOfStockException`, and nothing is stored |
