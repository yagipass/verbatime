# Examples

Ready-to-run setups that record a real Java application with Verbatime. Each one runs in Docker
Compose and builds the agent from this checkout.

You need Docker with Compose v2, and JDK Mission Control with the Verbatime plugin, as described in
[Quick start](../README.md#quick-start).

## Try one

1. Start an example.
   ```sh
   cd spring-boot-mvc
   docker compose up --build
   ```
2. Within 60 seconds, connect JMC to `localhost:7091` and press Start recording. The roots are
   already set. The application waits at `main()` until then, so its startup is recorded too. If
   you do not start a recording, it starts unrecorded after 60 seconds.
3. Send requests. This sends load for 30 seconds over 8 connections.
   ```sh
   docker compose --profile load run --rm load
   ```
4. Press Stop recording, then shut the example down.
   ```sh
   docker compose down -v
   ```

Run one example at a time. They all use port 8080 for HTTP and port 7091 for JMX.

## Pick an example

Every example runs the same small order workload, so their recordings are easy to compare.

### Web servers

| Example | What it shows |
|---|---|
| [`plain-httpserver`](plain-httpserver/) | The JDK's built-in HTTP server, with no framework |
| [`spring-boot-mvc`](spring-boot-mvc/) | Spring Boot MVC on embedded Tomcat |
| [`thymeleaf`](thymeleaf/) | Spring Boot MVC rendering HTML with Thymeleaf |
| [`scheduled-task`](scheduled-task/) | Spring Boot running the work from `@Scheduled` |
| [`undertow`](undertow/) | Embedded Undertow with a blocking handler |
| [`helidon-se`](helidon-se/) | Helidon SE on virtual threads |
| [`micronaut`](micronaut/) | Micronaut on Netty |
| [`quarkus`](quarkus/) | Quarkus REST in JVM mode |

### WAR on an application server

| Example | What it shows |
|---|---|
| [`tomcat-war`](tomcat-war/) | A WAR of plain servlets on Tomcat |
| [`jetty-war`](jetty-war/) | The same WAR on Jetty |
| [`wildfly-war`](wildfly-war/) | The same WAR on WildFly, which loads classes through JBoss Modules |
| [`open-liberty-war`](open-liberty-war/) | The same WAR on Open Liberty, an OSGi runtime on OpenJ9 |

### Databases

Each of these starts PostgreSQL in its own container.

| Example | What it shows |
|---|---|
| [`jdbc-hibernate`](jdbc-hibernate/) | Spring Data JPA with Hibernate and HikariCP |
| [`mybatis`](mybatis/) | A MyBatis mapper |
| [`r2dbc`](r2dbc/) | Reactive database access with Spring Data R2DBC |

### Async and messaging

| Example | What it shows |
|---|---|
| [`spring-boot-webflux`](spring-boot-webflux/) | Spring Boot WebFlux on Reactor Netty |
| [`virtual-threads`](virtual-threads/) | `spring-boot-mvc` on virtual threads |
| [`ktor-coroutines`](ktor-coroutines/) | A Kotlin suspend handler in Ktor that switches dispatchers |
| [`kafka-consumer`](kafka-consumer/) | Spring Boot sending to and consuming from Kafka |
| [`grpc`](grpc/) | A grpc-java unary service |

### Tests and batch jobs

These record by themselves and write the recording to a file. See
[Recording tests and batch jobs](#recording-tests-and-batch-jobs).

| Example | What it shows |
|---|---|
| [`junit-maven`](junit-maven/) | JUnit tests run by Maven Surefire |
| [`junit-gradle`](junit-gradle/) | JUnit tests run by Gradle |
| [`spring-batch`](spring-batch/) | A Spring Batch job that runs and exits |

## Where to put -javaagent

Each server reads JVM options from its own place. The examples show where.

| Server | Where | Example |
|---|---|---|
| Fat jar, or plain `java` | `JAVA_TOOL_OPTIONS` | [`plain-httpserver`](plain-httpserver/README.md#agent-settings) |
| Spring Boot | `JAVA_TOOL_OPTIONS` | [`spring-boot-mvc`](spring-boot-mvc/README.md#how-the-agent-is-added) |
| Helidon SE | `JAVA_TOOL_OPTIONS` | [`helidon-se`](helidon-se/README.md#agent-settings) |
| Quarkus | `JAVA_TOOL_OPTIONS` | [`quarkus`](quarkus/README.md#agent-settings) |
| Micronaut | `JAVA_TOOL_OPTIONS` | [`micronaut`](micronaut/README.md#agent-settings) |
| Tomcat | `CATALINA_OPTS` | [`tomcat-war`](tomcat-war/README.md#agent-settings) |
| Jetty | `JAVA_OPTIONS` | [`jetty-war`](jetty-war/README.md#agent-settings) |
| WildFly | `JAVA_OPTS` | [`wildfly-war`](wildfly-war/README.md#agent-settings) |
| Open Liberty | `jvm.options` | [`open-liberty-war`](open-liberty-war/README.md#agent-settings) |
| Maven Surefire | `argLine` | [`junit-maven`](junit-maven/README.md#agent-settings) |
| Gradle | `tasks.test.jvmArgs` | [`junit-gradle`](junit-gradle/README.md#agent-settings) |

The JMX flags from [Quick start](../README.md#quick-start) and `roots=` go in the same place. By
default, everything except the JDK and the agent is instrumented, including the server and the
framework.

## Recording tests and batch jobs

In `junit-maven`, `junit-gradle`, and `spring-batch`, the JVM exits as soon as its work is done.
That is too fast to press Start recording, so the agent alone records from startup to exit. No JMX
port is needed.

- `record=startup` starts the recording before `main()` and stops it when the JVM exits.
- `roots=` names the methods to record.
- `out=` writes the recording to the example's `recordings/` directory. Each run overwrites it.

Open the file in JMC with File > Open, or read it with the [`vbtm`](../modules/cli/README.md)
command-line tool.

```sh
vbtm sessions recordings/spring-batch.vbtm --sort dur
vbtm tree recordings/spring-batch.vbtm 1
```

## Options

Set these when you run `docker compose up`.

| Variable | What it does |
|---|---|
| `VERBATIME_EXTRA` | Adds agent arguments |
| `JMX_HOST` | The host name JMC connects to, instead of `localhost` |
| `VERBATIME_JVM_EXTRA` | Adds JVM options. Only in `open-liberty-war` |

If you set one, set it for the `load` command too. Otherwise Compose restarts the application, and
the recording with it.

## Notes

- Applications run on JDK 26. `tomcat-war`, `jetty-war`, `wildfly-war`, and `open-liberty-war` run
  on JDK 25, because their server images ship only LTS JDKs. Open Liberty runs on OpenJ9.
- The Kafka broker runs in its own JDK 21 container and is not recorded.
- `jetty-war`, `wildfly-war`, and `open-liberty-war` have no sources of their own. They deploy
  the WAR built from `tomcat-war`.
