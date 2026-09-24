# Adding the agent

Add `-javaagent` to the JVM that runs your code. Where it goes depends on how that JVM starts.

## Where to put -javaagent

| Runner | Where | Example |
|---|---|---|
| Fat jar, or plain `java` | `JAVA_TOOL_OPTIONS` | [`spring-batch`](https://github.com/yagipass/verbatime/tree/main/examples/spring-batch#agent-settings) |
| Spring Boot | `JAVA_TOOL_OPTIONS` | [`spring-boot-mvc`](https://github.com/yagipass/verbatime/tree/main/examples/spring-boot-mvc#how-the-agent-is-added) |
| Helidon SE | `JAVA_TOOL_OPTIONS` | [`helidon-se`](https://github.com/yagipass/verbatime/tree/main/examples/helidon-se#agent-settings) |
| Quarkus | `JAVA_TOOL_OPTIONS` | [`quarkus`](https://github.com/yagipass/verbatime/tree/main/examples/quarkus#agent-settings) |
| Micronaut | `JAVA_TOOL_OPTIONS` | [`micronaut`](https://github.com/yagipass/verbatime/tree/main/examples/micronaut#agent-settings) |
| Maven Surefire | `argLine` | [`junit-maven`](https://github.com/yagipass/verbatime/tree/main/examples/junit-maven#agent-settings) |
| Gradle | `tasks.test.jvmArgs` | [`junit-gradle`](https://github.com/yagipass/verbatime/tree/main/examples/junit-gradle#agent-settings) |
| Tomcat | `CATALINA_OPTS` | [`tomcat-war`](https://github.com/yagipass/verbatime/tree/main/examples/tomcat-war#agent-settings) |
| Jetty | `JAVA_OPTIONS` | [`jetty-war`](https://github.com/yagipass/verbatime/tree/main/examples/jetty-war#agent-settings) |
| WildFly | `JAVA_OPTS` | [`wildfly-war`](https://github.com/yagipass/verbatime/tree/main/examples/wildfly-war#agent-settings) |
| Open Liberty | `jvm.options` | [`open-liberty-war`](https://github.com/yagipass/verbatime/tree/main/examples/open-liberty-war#agent-settings) |

`JAVA_TOOL_OPTIONS` applies to every JVM started with it, build tools included. Set it only for
the application.

## JMX flags

To record [with JDK Mission Control](../jmc.md), add these flags in the same place.

```text
-Dcom.sun.management.jmxremote.port=7091
-Dcom.sun.management.jmxremote.rmi.port=7091
-Dcom.sun.management.jmxremote.authenticate=false
-Dcom.sun.management.jmxremote.ssl=false
-Djava.rmi.server.hostname=localhost
```

::: warning
Anyone who can reach this port can control the JVM, with no password. Use these flags only on
your development machine.
:::

## From a container or another host

- **Container:** publish the port to your machine only, as in `-p 127.0.0.1:7091:7091`. Keep
  `java.rmi.server.hostname=localhost`.
- **Another host:** set `java.rmi.server.hostname` to the name JDK Mission Control uses for that
  host, and connect to `<host>:7091`. Use a network you trust.

JDK Mission Control connects to the port, then to the host named in `java.rmi.server.hostname`, so
both must be reachable.
