# open-liberty-war

The WAR from [`tomcat-war`](../tomcat-war/) deployed on the official Open Liberty 26.0 image,
which runs on OpenJ9 rather than HotSpot. It carries a `ServletContextListener`, a `Filter`, a
`loadOnStartup` servlet, and a lazily initialized servlet. This directory has no sources of its
own: the Dockerfile builds `tomcat-war` and copies its WAR next to the server configuration. The
example is about an OSGi-based application server and about Liberty's way of adding JVM options: a
`jvm.options` file instead of an environment variable. OpenJ9 reads `-javaagent` and the JMX flags
exactly like HotSpot, only garbage-collector flags differ, and Liberty also loads its own
`ws-javaagent.jar`, so the JVM runs two agents.

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

Set in [`config/`](config/), which the image copies to `/config`, through Liberty's `jvm.options`,
alongside the shared JMX flags:

```text
-javaagent:/config/verbatime-agent.jar=waitstart=60s,roots=com.ibm.ws.kernel.boot.cmdline.EnvCheck::main+com.ibm.ws.webcontainer.WebContainer::handleRequest
```

| File | Contents |
|---|---|
| `jvm.options` | The agent line above and the JMX flags, one option per line |
| `bootstrap.properties` | `org.osgi.framework.bootdelegation=io.github.yagipass.verbatime.agent.probe`. OSGi bundle class loaders do not delegate unknown packages to the boot class path, where the agent puts its `probe` classes. Without this, every instrumented class fails with `NoClassDefFoundError` |
| `server.xml` | The `servlet-6.1` feature, the HTTP endpoint on port 8080, and the WAR at context root `/` |

| Setting | Value |
|---|---|
| Instrumented classes | The Liberty kernel and features, and the WAR |
| `waitstart=60s` | Pauses at `com.ibm.ws.kernel.boot.cmdline.EnvCheck.main`, the main class of `ws-server.jar`, for up to 60 seconds until JMC starts a recording |
| `roots=` | Sets the roots below. You can change them in JMC |

`VERBATIME_JVM_EXTRA` goes into `JVM_ARGS`, which the `server` script appends after `jvm.options`.

## Roots to try

| Root | What one tree covers |
|---|---|
| `com.ibm.ws.kernel.boot.cmdline.EnvCheck::main` | Server startup, when the recording is started while the JVM is paused at `main()`: the kernel boot and the OSGi framework launch on the `main` thread, which then waits for as long as the server runs. Feature and application start, with `StartupListener`, `TimingFilter.init`, and `OrderServlet.init`, run on `Default Executor-thread-N` threads and are not in this tree. |
| `com.ibm.ws.webcontainer.WebContainer::handleRequest` | One HTTP request on `Default Executor-thread-N`: virtual host and context lookup, the filter chain, the servlet, and the response. |

## Endpoints

| Path | Response |
|---|---|
| `GET /healthz` | `ok` |
| `GET /orders?sku=widget&qty=3` | `sku=widget qty=3 cents=... tx=...` |
| `GET /orders?sku=widget&qty=500` | `409` from `OutOfStockException` |
