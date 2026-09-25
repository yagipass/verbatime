---
description: Where to put -javaagent for fat jars, Spring Boot, Quarkus, Maven Surefire and other runners, and the JMX flags for JDK Mission Control.
---

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
-Dcom.sun.management.jmxremote.host=127.0.0.1
-Dcom.sun.management.jmxremote.authenticate=false
-Dcom.sun.management.jmxremote.ssl=false
-Djava.rmi.server.hostname=localhost
```

`jmxremote.host=127.0.0.1` lets only your machine reach the port. Without it, the JVM listens on
every network interface, and any machine on the same network can connect. `java.rmi.server.hostname`
does not limit this. It only sets the host name the JVM tells JDK Mission Control to connect to.

::: warning
Anyone who can reach this port can control the JVM, with no password. Use these flags only on
your development machine.
:::

## From a container or another host

- **Container:** leave out `jmxremote.host`. A published port arrives on the container's own
  network interface, not its loopback, so the flag would block it. Publish the port to your
  machine only instead, as in `-p 127.0.0.1:7091:7091`, and keep
  `java.rmi.server.hostname=localhost`.
- **Another host:** leave out `jmxremote.host`, which would block JDK Mission Control on your
  machine. Set `java.rmi.server.hostname` to the name JDK Mission Control uses for that host, and
  connect to `<host>:7091`. Use a network you trust.

JDK Mission Control connects to the port, then to the host named in `java.rmi.server.hostname`, so
both must be reachable.
