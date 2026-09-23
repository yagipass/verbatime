# kafka-consumer

Spring Boot 4.1 with `spring-boot-starter-kafka` against a single-node Kafka 4.3 broker in KRaft
mode, packaged as a fat jar and run with `java -jar`. `POST /orders` publishes a message, and a
`@KafkaListener` consumes it and runs the workload. The unit of work here is one consumed record,
not an HTTP request: the request only enqueues.

## Run

```sh
docker compose up --build
```

Compose starts the broker first and waits for its healthcheck. The application JVM then pauses at
`main()` until JMC starts a recording. That flow, the `load` profile, and the shutdown are the
same for every example and are described once in
[Running an example](../README.md#running-an-example). Exercise it with:

```sh
curl -X POST 'http://localhost:8080/orders?sku=widget&qty=3'
curl http://localhost:8080/stats
docker compose --profile load run --rm load
```

The load run sends 2,000 POSTs over 8 connections instead of running for 30 seconds: the request only enqueues, so
`oha` publishes far faster than the single consumer drains, and the consumer handles under a
hundred records per second because the workload sleeps in `Work.io`. Expect the drain to take
about half a minute after the load run ends, and `/stats` shows the progress.

## Agent settings

Set in [`compose.yaml`](compose.yaml) through `JAVA_TOOL_OPTIONS`, alongside the shared JMX flags:

```text
-javaagent:/app/verbatime-agent.jar=waitstart=60s,roots=org.springframework.boot.SpringApplication::run+org.springframework.kafka.listener.KafkaMessageListenerContainer$ListenerConsumer::doInvokeRecordListener
```

| Setting | Value |
|---|---|
| Instrumented classes | Spring, embedded Tomcat, the Kafka client, and the application. The broker runs in its own container and is not instrumented |
| `waitstart=60s` | Pauses at the fat jar's `JarLauncher.main` for up to 60 seconds until JMC starts a recording |
| `roots=` | Sets the roots below. You can change them in JMC |

## Roots to try

| Root | What one tree covers |
|---|---|
| `org.springframework.boot.SpringApplication::run` | Application startup, when the recording is started while the JVM is paused at `main()`: context refresh, `KafkaAdmin` creating the `orders` topic, the listener container start, and the application's `StartupRunner`, an `ApplicationRunner` that places one warm-up order. The consumer thread itself starts outside this tree. |
| `org.springframework.kafka.listener.KafkaMessageListenerContainer$ListenerConsumer::doInvokeRecordListener` | One consumed record on the container thread `...KafkaListenerEndpointContainer#0-0-C-1`: the listener adapter, argument conversion, the `@KafkaListener` method with the workload, and error handling. The offset commit happens after the poll batch, outside this tree. |

A `DispatcherServlet::doDispatch` tree of the `POST` shows only `KafkaTemplate.send` handing the
record to the producer's I/O thread, while the workload runs on the consumer thread.

## Endpoints

| Path | Response |
|---|---|
| `GET /healthz` | `ok` |
| `POST /orders?sku=widget&qty=3` | `202 queued widget:3`, and the consumer then places the order |
| `POST /orders?sku=widget&qty=500` | `202`, and the consumer then rejects it with `OutOfStockException` and counts it |
| `GET /stats` | `{"placed":N,"rejected":M}` |
