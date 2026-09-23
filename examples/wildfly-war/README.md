# wildfly-war

The WAR from [`tomcat-war`](../tomcat-war/) deployed on the official WildFly 41 image. It carries
a `ServletContextListener`, a `Filter`, a `loadOnStartup` servlet, and a lazily initialized
servlet. This directory has no sources of its own: the Dockerfile builds `tomcat-war` and copies
its WAR into the deployment scanner's directory. The example is about a modular application
server: JBoss Modules gives every module its own class loader, which changes what the agent needs,
and the agent goes in through `JAVA_OPTS`, the variable `standalone.sh` reads.

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

Set in [`compose.yaml`](compose.yaml) through `JAVA_OPTS`, alongside the shared JMX flags:

```text
-javaagent:/opt/jboss/wildfly/verbatime-agent.jar=waitstart=60s,roots=org.jboss.as.server.Main::main+io.undertow.servlet.handlers.ServletInitialHandler::dispatchRequest,exclude=org.jboss.modules+org.jboss.marshalling
-Djboss.modules.system.pkgs=org.jboss.byteman,org.jboss.logmanager,org.wildfly.common,io.smallrye.common,io.github.yagipass.verbatime.agent.probe
-Djava.net.preferIPv4Stack=true -Djava.awt.headless=true -Xms64m -Xmx512m
```

| Setting | Value |
|---|---|
| Instrumented classes | Everything except the JDK, JBoss Modules, and JBoss Marshalling, which `exclude=org.jboss.modules+org.jboss.marshalling` leaves out. That covers WildFly's subsystems, Undertow, Infinispan, and the WAR. Instrumented JBoss Modules class loaders define classes twice and fail with `LinkageError: attempted duplicate class definition`. JBoss Marshalling's `FieldSetter.get` inspects the calling class on the stack and only allows the field's own class. With the agent it sees the wrapper method in `FieldSetter` itself, throws `SecurityException: Cannot get field from someone else's class`, and the Infinispan cache container for Hibernate fails to start |
| `waitstart=60s` | Pauses at `org.jboss.modules.Main.main`, the main class of `jboss-modules.jar`, for up to 60 seconds until JMC starts a recording. The gate works on an excluded class, because only instrumentation is skipped |
| `roots=` | Sets the roots below. You can change them in JMC |
| `jboss.modules.system.pkgs` | Packages that module class loaders resolve from the boot class path. Instrumented code calls the agent's `probe` classes, which the agent puts on the boot class path, so that package must be listed, or every instrumented class fails with `NoClassDefFoundError` |
| `standalone.conf`, written by the Dockerfile | Puts WildFly's LogManager and the `wildfly-common`, `smallrye-common`, and `jboss-logging` jars it depends on onto the boot class path with `-Xbootclasspath/a:`, sets `java.util.logging.manager`, and sets `sun.util.logging.disableCallerCheck=true`. The JDK's JMX agent and the verbatime MBean initialize `java.util.logging` before JBoss Modules could load the LogManager, which WildFly otherwise reports as `WFLYLOG0078`. On JDK 9+, `java.util.logging` also bypasses the installed LogManager for boot-loaded callers unless the caller check is disabled |

Setting `JAVA_OPTS` in the environment replaces the defaults from `standalone.conf`, so the
defaults are repeated in `compose.yaml`.

## Roots to try

| Root | What one tree covers |
|---|---|
| `org.jboss.as.server.Main::main` | Server startup, when the recording is started while the JVM is paused at `main()`: argument parsing, the bootstrap, and the start of the service container on the `main` thread. `jboss-modules` calls this entry point after resolving the boot module. `org.jboss.modules.Main`, the JVM's main class, is excluded from instrumentation and cannot be a root. Subsystem start and the WAR deployment with `StartupListener`, `TimingFilter.init`, and `OrderServlet.init` run on `MSC service thread 1-N` threads and are not in this tree. |
| `io.undertow.servlet.handlers.ServletInitialHandler::dispatchRequest` | One HTTP request on `default task-N`: the servlet request context, the filter chain, the servlet, and the response. The XNIO I/O thread's parsing and dispatch half is a separate tree. |

## Endpoints

| Path | Response |
|---|---|
| `GET /healthz` | `ok` |
| `GET /orders?sku=widget&qty=3` | `sku=widget qty=3 cents=... tx=...` |
| `GET /orders?sku=widget&qty=500` | `409` from `OutOfStockException` |
