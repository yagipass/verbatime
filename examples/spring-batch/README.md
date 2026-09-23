# spring-batch

Spring Boot 4.1 with Spring Batch 6, packaged as a fat jar and run with `java -jar`. The job has
one chunk-oriented step that reads five order lines, runs the workload for each in the processor,
and writes the receipts to Postgres in chunks of two. There is no HTTP server: the JVM starts, runs
the job, and exits, so the unit of work is the job or one step. The run is over too quickly to start
a recording by hand, so the agent records it by itself with `record=startup` and writes it to a file
on the host, which is then opened in JMC from disk. No JMX and no JMC are involved while the job
runs.

## Run

```sh
docker compose up --build
```

Compose starts Postgres first and waits for its healthcheck. The application JVM starts recording in
the agent's `premain`, the job runs to completion within a few seconds, and the agent's shutdown
hook closes the recording as the JVM exits, leaving it in the file below, as described in
[Examples whose JVM exits on its own](../README.md#examples-whose-jvm-exits-on-its-own):

```text
recordings/spring-batch.vbtm
```

Every run truncates the recording file and writes a new job instance through `RunIdIncrementer`.
Postgres keeps running after the application exits, until `docker compose down -v`.

## Agent settings

Set in [`compose.yaml`](compose.yaml) through `JAVA_TOOL_OPTIONS`:

```text
-javaagent:/app/verbatime-agent.jar=record=startup,roots=org.springframework.boot.SpringApplication::run,out=/work/recordings/spring-batch.vbtm
```

| Setting | Value |
|---|---|
| Instrumented classes | Spring, Spring Batch, HikariCP, the PostgreSQL driver, and the application |
| `record=startup` | Records from the agent's `premain` until the JVM exits, with no JMX control registered. `roots=` and `out=` are required in this mode |
| `roots=…SpringApplication::run` | The whole run in one tree. It begins before the Spring context exists, which is exactly what `record=startup` makes reachable |
| `out=/work/recordings/spring-batch.vbtm` | Writes the recording to `./recordings/` on the host through a bind mount. The default spool would be deleted when the JVM exits |

## Roots to try

Edit `roots=` in [`compose.yaml`](compose.yaml) and run the command again.

| Root | What one tree covers |
|---|---|
| `org.springframework.boot.SpringApplication::run` | The whole run: context refresh with the DataSource and the Batch schema, the application's `StartupRunner`, an `ApplicationRunner` that places one warm-up order and is ordered before the job runner, and the job itself, launched by Spring Boot's `JobLauncherApplicationRunner`. The default here. |
| `org.springframework.batch.core.step.AbstractStep::execute` | One step execution: for each chunk, the reads, the processor calls with the workload, the JDBC batch insert, the chunk transaction, and the `StepExecution` bookkeeping in the job repository. |

Several roots at once are written with `+`, as in `roots=a.B::m+c.D::n`.

## Driving it from JMC instead

To pick roots interactively, drop `record=startup` and `roots=` and use the default
`record=ondemand` with `waitstart=60s` and the JMX flags, as the server examples do. The JVM then
pauses at the fat jar's `JarLauncher.main` until JMC starts a recording, and port 7091 has to be
published from `compose.yaml`. If no recording is started within the window, the job runs
unrecorded and the container exits with status 0.

## Job

| Item | Value |
|---|---|
| Job | `placeOrders`, one step, `RunIdIncrementer` |
| Step | `placeOrders`, chunk size 2, `ListItemReader` of 5 lines, processor `OrderService.placeOrder`, `JdbcBatchItemWriter` into `orders` |
| Tables | `orders` from `schema.sql`, and the `BATCH_*` tables from Spring Batch's own schema initializer |
