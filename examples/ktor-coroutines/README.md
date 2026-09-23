# ktor-coroutines

Ktor 3.5 on Netty, written in Kotlin 2.4, compiled with `kotlin-maven-plugin`, and packaged as a
thin `app.jar` with its dependencies in `/app/lib` and run with `java -jar`. The `/orders` handler
is a `suspend` function that moves the workload to `Dispatchers.IO` with `withContext` and resumes
afterwards. The point of the example is what coroutines do to the "one root execution is one
tree" model: a suspending call returns at every suspension point and is resumed as a new call, on
whichever thread the dispatcher picks.

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
-javaagent:/app/verbatime-agent.jar=waitstart=60s,roots=io.github.yagipass.verbatime.examples.ktor.MainKt::main+kotlinx.coroutines.DispatchedTask::run
--enable-native-access=ALL-UNNAMED
```

| Setting | Value |
|---|---|
| Instrumented classes | Ktor, kotlinx.coroutines, the Kotlin standard library, Netty, and the application |
| `waitstart=60s` | Pauses at `MainKt.main`, the class Kotlin generates for the top-level `main` function, for up to 60 seconds until JMC starts a recording |
| `roots=` | Sets the roots below. You can change them in JMC |

## Roots to try

| Root | What one tree covers |
|---|---|
| `io.github.yagipass.verbatime.examples.ktor.MainKt::main` | Server startup, when the recording is started while the JVM is paused at `main()`: creating the engine, installing the module, the `ApplicationStarted` event that places one warm-up order, and the bind. `start(wait = true)` then blocks for as long as the server runs, so the tree is flushed as unclosed when the recording is stopped. |
| `kotlinx.coroutines.DispatchedTask::run` | One resumption of a coroutine on the thread that runs it. A `/orders` request is two trees here: the workload on `DefaultDispatcher-worker-N`, which is `Dispatchers.IO`, and `respondText` with the unwinding of the pipeline back on Ktor's call dispatcher, `eventLoopGroupProxy-4-N`. The handler up to `withContext` has its own root, `io.ktor.server.netty.http1.NettyHttp1Handler::handleRequest$lambda$0`, which for `/healthz` holds the whole request. |

The handler up to `withContext` is not a dispatched task: Ktor starts the call's coroutine
undispatched inside a Netty task on the call dispatcher. Two small trees per connection on the
I/O threads `eventLoopGroupProxy-3-N` are Netty's own resumptions. A `suspend` function cannot be
the root: at a suspension point it returns `COROUTINE_SUSPENDED` to its caller, so a root on the
handler would end there, and the rest of the request is a fresh `invokeSuspend` call on another
thread. `OrderService::placeOrder` is the single-tree alternative that shows only the workload.

## Endpoints

| Path | Response |
|---|---|
| `GET /healthz` | `ok` |
| `GET /orders?sku=widget&qty=3` | `sku=widget qty=3 cents=... tx=...` |
| `GET /orders?sku=widget&qty=500` | `409` from `OutOfStockException` |
