# undertow

Embedded Undertow 2.4 with a plain `HttpHandler`, no servlet layer and no framework, packaged as a
thin `app.jar` with its dependencies in `/app/lib` and run with `java -jar`. Undertow parses
requests on XNIO I/O threads and, for `/orders`, hands the exchange to a worker thread through a
`BlockingHandler`. This example shows a request that is two trees because of that hand-off.

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
-javaagent:/app/verbatime-agent.jar=waitstart=60s,roots=io.github.yagipass.verbatime.examples.undertow.Main::main+io.undertow.server.Connectors::executeRootHandler
```

| Setting | Value |
|---|---|
| Instrumented classes | Undertow, XNIO, and the application |
| `waitstart=60s` | Pauses at `Main.main` for up to 60 seconds until JMC starts a recording |
| `roots=` | Sets the roots below. You can change them in JMC |

## Roots to try

| Root | What one tree covers |
|---|---|
| `io.github.yagipass.verbatime.examples.undertow.Main::main` | Server startup, when the recording is started while the JVM is paused at `main()`: `Main.warmUp` placing one order, the XNIO worker creation, and the listener bind. |
| `io.undertow.server.Connectors::executeRootHandler` | One request, as two trees. The tree on `XNIO-1 I/O-N` holds the routing to the `BlockingHandler` and the dispatch to the worker, and the tree on `XNIO-1 task-N` holds the handler with the workload and the response. `/healthz` stays on the I/O thread and is one tree. |

## Endpoints

| Path | Response |
|---|---|
| `GET /healthz` | `ok` |
| `GET /orders?sku=widget&qty=3` | `sku=widget qty=3 cents=... tx=...` |
| `GET /orders?sku=widget&qty=500` | `409` from `OutOfStockException` |
