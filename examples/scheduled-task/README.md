# scheduled-task

Spring Boot 4.1 with `@EnableScheduling`, packaged as a fat jar and run with `java -jar`. A
`@Scheduled(fixedDelay = 1000, initialDelay = 2000)` method runs the workload once a second on
Spring's scheduler thread. There is no `/orders` endpoint and no load service, because the scheduler is
the load. The unit of work is one tick.

## Run

```sh
docker compose up --build
```

The JVM pauses at `main()` until JMC starts a recording. That flow and the shutdown are the same
for every example and are described once in
[Running an example](../README.md#running-an-example). Watch it with:

```sh
curl http://localhost:8080/stats
```

Ticks never stop, so stop the recording after a few seconds.

## Agent settings

Set in [`compose.yaml`](compose.yaml) through `JAVA_TOOL_OPTIONS`, alongside the shared JMX flags:

```text
-javaagent:/app/verbatime-agent.jar=waitstart=60s,roots=org.springframework.boot.SpringApplication::run+io.github.yagipass.verbatime.examples.scheduledtask.ScheduledOrders::placeScheduledOrder
```

| Setting | Value |
|---|---|
| Instrumented classes | Spring, embedded Tomcat, and the application |
| `waitstart=60s` | Pauses at the fat jar's `JarLauncher.main` for up to 60 seconds until JMC starts a recording |
| `roots=` | Sets the roots below. You can change them in JMC |

## Roots to try

| Root | What one tree covers |
|---|---|
| `org.springframework.boot.SpringApplication::run` | Application startup, when the recording is started while the JVM is paused at `main()`: context refresh, registration of the scheduled method by `ScheduledAnnotationBeanPostProcessor`, embedded Tomcat, and the application's `StartupRunner`, an `ApplicationRunner` that places one warm-up order. |
| `io.github.yagipass.verbatime.examples.scheduledtask.ScheduledOrders::placeScheduledOrder` | One tick on `scheduling-1`: the workload for that tick's order. `fixedDelay` counts from the end of the previous tick, so trees are one second plus the workload apart. |

## Endpoints

| Path | Response |
|---|---|
| `GET /healthz` | `ok` |
| `GET /stats` | `{"ticks":N}`, the number of scheduled orders placed so far |
