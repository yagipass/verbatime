# helidon-se

Helidon SE 4.5's `WebServer`, packaged as a thin `app.jar` with its dependencies in `/app/lib` and
run with `java -jar`. It serves every connection on its own virtual thread and has no reactive
layer: a handler is a plain method that blocks. The example registers one `HttpService` whose
`beforeStart` hook places the warm-up order, and shows what one long-lived virtual thread per
connection does to the recording's thread list.

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
-javaagent:/app/verbatime-agent.jar=waitstart=60s,roots=io.github.yagipass.verbatime.examples.helidon.Main::main+io.helidon.webserver.http1.Http1Connection::route
```

| Setting | Value |
|---|---|
| Instrumented classes | Helidon and the application |
| `waitstart=60s` | Pauses at `Main.main` for up to 60 seconds until JMC starts a recording |
| `roots=` | Sets the roots below. You can change them in JMC |

## Roots to try

| Root | What one tree covers |
|---|---|
| `io.github.yagipass.verbatime.examples.helidon.Main::main` | Server startup, when the recording is started while the JVM is paused at `main()`: building the server and its routing, `OrdersService.beforeStart` placing one warm-up order, and the bind. |
| `io.helidon.webserver.http1.Http1Connection::route` | One HTTP request on the connection's virtual thread: request and response setup, routing, the handler with the workload, and sending the response. The per-connection `handle` method loops over keep-alive requests and would never close. |

A connection is one virtual thread for its whole life, so under load each of `oha`'s eight
connections becomes one thread with many trees.

## Endpoints

| Path | Response |
|---|---|
| `GET /healthz` | `ok` |
| `GET /orders?sku=widget&qty=3` | `sku=widget qty=3 cents=... tx=...` |
| `GET /orders?sku=widget&qty=500` | `409` from `OutOfStockException` |
