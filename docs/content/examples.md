# Examples

Ready-to-run applications in
[`examples/`](https://github.com/yagipass/verbatime/tree/main/examples) to try Verbatime on. Each
one runs in Docker Compose and builds the agent from the repository.

You need Docker with Compose v2, and JDK Mission Control with the
[Verbatime plugin](./reference/jmc-plugin.md).

## Try one

1. Start an example.
   ```sh
   cd examples/spring-boot-mvc
   docker compose up --build
   ```
2. Within 60 seconds, connect JDK Mission Control to `localhost:7091` and press Start recording.
   The roots are already set, and startup is recorded too.
3. Send load for 30 seconds.
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
| [`plain-httpserver`](https://github.com/yagipass/verbatime/tree/main/examples/plain-httpserver) | The JDK's built-in HTTP server, with no framework |
| [`spring-boot-mvc`](https://github.com/yagipass/verbatime/tree/main/examples/spring-boot-mvc) | Spring Boot MVC on embedded Tomcat |
| [`thymeleaf`](https://github.com/yagipass/verbatime/tree/main/examples/thymeleaf) | Spring Boot MVC rendering HTML with Thymeleaf |
| [`scheduled-task`](https://github.com/yagipass/verbatime/tree/main/examples/scheduled-task) | Spring Boot running the work from `@Scheduled` |
| [`undertow`](https://github.com/yagipass/verbatime/tree/main/examples/undertow) | Embedded Undertow with a blocking handler |
| [`helidon-se`](https://github.com/yagipass/verbatime/tree/main/examples/helidon-se) | Helidon SE on virtual threads |
| [`micronaut`](https://github.com/yagipass/verbatime/tree/main/examples/micronaut) | Micronaut on Netty |
| [`quarkus`](https://github.com/yagipass/verbatime/tree/main/examples/quarkus) | Quarkus REST in JVM mode |

### WAR on an application server

| Example | What it shows |
|---|---|
| [`tomcat-war`](https://github.com/yagipass/verbatime/tree/main/examples/tomcat-war) | A WAR of plain servlets on Tomcat |
| [`jetty-war`](https://github.com/yagipass/verbatime/tree/main/examples/jetty-war) | The same WAR on Jetty |
| [`wildfly-war`](https://github.com/yagipass/verbatime/tree/main/examples/wildfly-war) | The same WAR on WildFly, which loads classes through JBoss Modules |
| [`open-liberty-war`](https://github.com/yagipass/verbatime/tree/main/examples/open-liberty-war) | The same WAR on Open Liberty, an OSGi runtime on OpenJ9 |

### Databases

Each of these starts PostgreSQL in its own container.

| Example | What it shows |
|---|---|
| [`jdbc-hibernate`](https://github.com/yagipass/verbatime/tree/main/examples/jdbc-hibernate) | Spring Data JPA with Hibernate and HikariCP |
| [`mybatis`](https://github.com/yagipass/verbatime/tree/main/examples/mybatis) | A MyBatis mapper |
| [`r2dbc`](https://github.com/yagipass/verbatime/tree/main/examples/r2dbc) | Reactive database access with Spring Data R2DBC |

### Async and messaging

| Example | What it shows |
|---|---|
| [`spring-boot-webflux`](https://github.com/yagipass/verbatime/tree/main/examples/spring-boot-webflux) | Spring Boot WebFlux on Reactor Netty |
| [`virtual-threads`](https://github.com/yagipass/verbatime/tree/main/examples/virtual-threads) | `spring-boot-mvc` on virtual threads |
| [`ktor-coroutines`](https://github.com/yagipass/verbatime/tree/main/examples/ktor-coroutines) | A Kotlin suspend handler in Ktor that switches dispatchers |
| [`kafka-consumer`](https://github.com/yagipass/verbatime/tree/main/examples/kafka-consumer) | Spring Boot sending to and consuming from Kafka |
| [`grpc`](https://github.com/yagipass/verbatime/tree/main/examples/grpc) | A grpc-java unary service |

### Tests and batch jobs

These record from startup to exit and write the recording to their `recordings/` directory. See
[Recording without JDK Mission Control](./guide/recording-without-jmc.md).

| Example | What it shows |
|---|---|
| [`junit-maven`](https://github.com/yagipass/verbatime/tree/main/examples/junit-maven) | JUnit tests run by Maven Surefire |
| [`junit-gradle`](https://github.com/yagipass/verbatime/tree/main/examples/junit-gradle) | JUnit tests run by Gradle |
| [`spring-batch`](https://github.com/yagipass/verbatime/tree/main/examples/spring-batch) | A Spring Batch job that runs and exits |

## Options

Set these when you run `docker compose up`.

| Variable | What it does |
|---|---|
| `VERBATIME_EXTRA` | Adds agent arguments |
| `JMX_HOST` | The host name JDK Mission Control connects to, instead of `localhost` |
| `VERBATIME_JVM_EXTRA` | Adds JVM options. Only in `open-liberty-war` |

Set the same values for the `load` command too. Otherwise Compose restarts the application, and
the recording with it.
