# jdbc-hibernate

Spring Boot 4.1 MVC with Spring Data JPA: Hibernate ORM 7.4, HikariCP, and the PostgreSQL driver
against a `postgres:18` container.

## Run

```sh
docker compose up --build
```

The JVM pauses at `main()` until JMC starts a recording. That flow, the `load` profile, and the
shutdown are the same for every example and are described once in
[Running an example](../README.md#running-an-example). Exercise it with:

```sh
curl 'http://localhost:8080/orders?sku=widget&qty=3'
curl http://localhost:8080/orders/recent
```

Compose starts Postgres first and waits for its healthcheck. The application creates the schema on
startup and keeps no volume, so every `up` starts empty.

## Agent settings

Set in [`compose.yaml`](compose.yaml) through `JAVA_TOOL_OPTIONS`, alongside the shared JMX flags:

```text
-javaagent:/app/verbatime-agent.jar=waitstart=60s,roots=org.springframework.boot.SpringApplication::run+org.springframework.web.servlet.DispatcherServlet::doDispatch
```

| Setting | Value |
|---|---|
| Instrumented classes | Spring, Hibernate, HikariCP, the PostgreSQL driver, and the application |
| `waitstart=60s` | Pauses at the fat jar's `JarLauncher.main` for up to 60 seconds until JMC starts a recording |
| `roots=` | Sets the roots below. You can change them in JMC |

## Roots to try

| Root | What one tree covers |
|---|---|
| `org.springframework.boot.SpringApplication::run` | Application startup, when the recording is started while the JVM is paused at `main()`: context refresh, Hibernate's bootstrap and the schema export, and the application's `StartupRunner`, an `ApplicationRunner` that places and persists one warm-up order, the row with `id` 1. |
| `org.springframework.web.servlet.DispatcherServlet::doDispatch` | One HTTP request: MVC dispatch, the transaction around the service method, the workload, Hibernate's persist and flush with the JDBC statements, commit, and the JSON response. |

The Spring Data repository is a JDK dynamic proxy, which the agent never sees. In the tree, the
service calls straight into Spring Data's `SimpleJpaRepository`.

## Endpoints

| Path | Response |
|---|---|
| `GET /healthz` | `ok` |
| `GET /orders?sku=widget&qty=3` | The stored row as JSON, with its `id`. The row with `id` 1 is the warm-up order from startup |
| `GET /orders/{id}` | That row, or `404` |
| `GET /orders/recent` | The ten most recent rows |
| `GET /orders?sku=widget&qty=500` | `409` from `OutOfStockException`, and nothing is stored |
