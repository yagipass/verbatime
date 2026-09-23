# virtual-threads

The [`spring-boot-mvc`](../spring-boot-mvc/) example with `spring.threads.virtual.enabled=true`:
Spring Boot 4.1 MVC on embedded Tomcat 11, packaged as a fat jar and run with `java -jar`. Tomcat
serves every request on a new virtual thread instead of a pooled platform thread. The agent
records one tree per root execution per thread, so this example shows what that model looks like
when threads are cheap and never reused.

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
-javaagent:/app/verbatime-agent.jar=waitstart=60s,roots=org.springframework.boot.SpringApplication::run+org.springframework.web.servlet.DispatcherServlet::doDispatch
```

| Setting | Value |
|---|---|
| Instrumented classes | Spring, embedded Tomcat, Jackson, and the application |
| `waitstart=60s` | Pauses at the fat jar's `JarLauncher.main` for up to 60 seconds until JMC starts a recording |
| `roots=` | Sets the roots below. You can change them in JMC |

## Roots to try

| Root | What one tree covers |
|---|---|
| `org.springframework.boot.SpringApplication::run` | Application startup, when the recording is started while the JVM is paused at `main()`: context refresh, embedded Tomcat with its virtual-thread executor, and the application's `StartupRunner`, an `ApplicationRunner` that places one warm-up order on the platform `main` thread. |
| `org.springframework.web.servlet.DispatcherServlet::doDispatch` | One HTTP request through MVC on its own virtual thread `tomcat-handler-N`: handler mapping, the handler adapter, the controller and workload, and message conversion of the JSON response. |

Every connection is served on a fresh virtual thread, so the recording's thread list grows with
the request count. A thread name only repeats when a keep-alive connection's next request has
already arrived by the time the previous response is written: Tomcat then serves it on the same
thread, which the `load` profile triggers for a few percent of its requests. `Work.io` sleeps
inside the workload, which unmounts the virtual thread and remounts it later, so the tree's wall
time spans that gap. On JDK 24 and later the sleep does not pin the carrier thread.

## Endpoints

| Path | Response |
|---|---|
| `GET /healthz` | `ok` |
| `GET /orders?sku=widget&qty=3` | `{"sku":"widget","qty":3,"cents":...,"txId":"..."}` |
| `GET /orders?sku=widget&qty=500` | `409` from `OutOfStockException`, mapped by a `@RestControllerAdvice` |
