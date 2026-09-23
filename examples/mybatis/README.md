# mybatis

Spring Boot 4.1 MVC with MyBatis through `mybatis-spring-boot-starter` 4.1, packaged as a fat jar
and run with `java -jar` against a `postgres:18` container. It is the SQL-mapper counterpart of
[`jdbc-hibernate`](../jdbc-hibernate/): the same endpoints and the same `orders` table. The
persistence path is a mapper interface with annotated SQL instead of an ORM, so the tree between
the service and the JDBC driver is much shallower.

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

The application creates the schema from `schema.sql` on startup with `spring.sql.init.mode=always`
and keeps no volume, so every `up` starts empty.

## Agent settings

Set in [`compose.yaml`](compose.yaml) through `JAVA_TOOL_OPTIONS`, alongside the shared JMX flags:

```text
-javaagent:/app/verbatime-agent.jar=waitstart=60s,roots=org.springframework.boot.SpringApplication::run+org.springframework.web.servlet.DispatcherServlet::doDispatch
```

| Setting | Value |
|---|---|
| Instrumented classes | Spring, embedded Tomcat, MyBatis, HikariCP, the PostgreSQL driver, and the application |
| `waitstart=60s` | Pauses at the fat jar's `JarLauncher.main` for up to 60 seconds until JMC starts a recording |
| `roots=` | Sets the roots below. You can change them in JMC |

## Roots to try

| Root | What one tree covers |
|---|---|
| `org.springframework.boot.SpringApplication::run` | Application startup, when the recording is started while the JVM is paused at `main()`: context refresh, the `SqlSessionFactory`, HikariCP, the schema script, and the application's `StartupRunner`, an `ApplicationRunner` that places and stores one warm-up order, the row with `id` 1. |
| `org.springframework.web.servlet.DispatcherServlet::doDispatch` | One HTTP request: MVC dispatch, the transaction around the service method, the workload, the mapper call through `MapperProxy` and `SqlSessionTemplate` down to the JDBC insert, commit, and the JSON response. |

Unlike a Spring Data repository, MyBatis's mapper proxy is visible: the interface itself is a JDK
proxy, which the agent never sees, but the very next frame is MyBatis's own `MapperProxy.invoke`.

## Endpoints

| Path | Response |
|---|---|
| `GET /healthz` | `ok` |
| `GET /orders?sku=widget&qty=3` | The stored row as JSON, with its `id`. The row with `id` 1 is the warm-up order from startup |
| `GET /orders/{id}` | That row, or `404` |
| `GET /orders/recent` | The ten most recent rows |
| `GET /orders?sku=widget&qty=500` | `409` from `OutOfStockException`, and nothing is stored |
