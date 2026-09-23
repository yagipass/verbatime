# plain-httpserver

The JDK's `com.sun.net.httpserver.HttpServer` serving the workload from a fixed pool of eight
platform threads named `http-worker`. It is the baseline: there is no framework, and the server
itself lives under `com.sun`, which the agent never instruments, so a request tree starts at the
application's handler.

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
-javaagent:/app/verbatime-agent.jar=waitstart=60s,roots=io.github.yagipass.verbatime.examples.httpserver.Main::main+io.github.yagipass.verbatime.examples.httpserver.OrderHandler::handle
```

| Setting | Value |
|---|---|
| Instrumented classes | The application only. The server itself is JDK code, which the agent never instruments |
| `waitstart=60s` | Pauses at `Main.main` for up to 60 seconds until JMC starts a recording |
| `roots=` | Sets the roots below. You can change them in JMC |

## Roots to try

| Root | What one tree covers |
|---|---|
| `io.github.yagipass.verbatime.examples.httpserver.Main::main` | Server startup, when the recording is started while the JVM is paused at `main()`: `Main.warmUp` places one order, then the server is created and started. |
| `io.github.yagipass.verbatime.examples.httpserver.OrderHandler::handle` | One HTTP request: query parsing, the workload, and writing the response. The highest frame visible to the agent, since the server itself is JDK code. |

## Endpoints

| Path | Response |
|---|---|
| `GET /healthz` | `ok` |
| `GET /orders?sku=widget&qty=3` | `sku=widget qty=3 cents=... tx=...` |
| `GET /orders?sku=widget&qty=500` | `409` from `OutOfStockException` |
