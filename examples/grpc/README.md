# grpc

A gRPC server on grpc-java 1.84 with `grpc-netty-shaded`, packaged as a thin `app.jar` with its
dependencies in `/app/lib` and run with `java -jar`. It serves one unary RPC, `Orders/PlaceOrder`,
backed by the workload. There is no HTTP/1.1 endpoint: the healthcheck only checks that the port
accepts connections, and load comes from `grpcurl` instead of `oha`. The server also registers
the reflection service, so `grpcurl` needs no `.proto` file.

## Run

```sh
docker compose up --build
```

The JVM pauses at `main()` until JMC starts a recording. That flow, the `load` profile, and the
shutdown are the same for every example and are described once in
[Running an example](../README.md#running-an-example). Exercise it with the commands below. The
last one runs 30 seconds of calls in a loop.

```sh
docker run --rm --network host fullstorydev/grpcurl:v1.9.3-alpine -plaintext localhost:8080 list
docker run --rm --network host fullstorydev/grpcurl:v1.9.3-alpine -plaintext \
    -d '{"sku":"widget","qty":3}' localhost:8080 verbatime.Orders/PlaceOrder
docker compose --profile load run --rm load
```

## Agent settings

Set in [`compose.yaml`](compose.yaml) through `JAVA_TOOL_OPTIONS`, alongside the shared JMX flags:

```text
-javaagent:/app/verbatime-agent.jar=waitstart=60s,roots=io.github.yagipass.verbatime.examples.grpc.Main::main+io.grpc.stub.ServerCalls$UnaryServerCallHandler$UnaryServerCallListener::onHalfClose
--enable-native-access=ALL-UNNAMED
```

| Setting | Value |
|---|---|
| Instrumented classes | grpc-java, the shaded Netty under `io.grpc.netty.shaded`, protobuf, the generated stubs, and the application |
| `waitstart=60s` | Pauses at `Main.main` for up to 60 seconds until JMC starts a recording |
| `roots=` | Sets the roots below. You can change them in JMC |

## Roots to try

| Root | What one tree covers |
|---|---|
| `io.github.yagipass.verbatime.examples.grpc.Main::main` | Server startup, when the recording is started while the JVM is paused at `main()`: `Main.warmUp` placing one order, building the server with the three services, and the bind. `main` then blocks in `awaitTermination` for as long as the server runs, so the tree is flushed as unclosed when the recording is stopped. |
| `io.grpc.stub.ServerCalls$UnaryServerCallHandler$UnaryServerCallListener::onHalfClose` | One unary call on `grpc-default-executor-N`: the service method with the workload, `onNext` with the response serialization, and `onCompleted`. An out-of-stock order ends with `Status.FAILED_PRECONDITION`. |

## RPC

| Call | Response |
|---|---|
| `verbatime.Orders/PlaceOrder {"sku":"widget","qty":3}` | `{"sku":"widget","qty":3,"cents":"...","txId":"..."}` |
| `verbatime.Orders/PlaceOrder {"sku":"widget","qty":500}` | `FailedPrecondition` with the `OutOfStockException` message |
| `grpc.health.v1.Health/Check` | `SERVING` |
