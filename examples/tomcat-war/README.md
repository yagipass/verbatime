# tomcat-war

A WAR of plain Servlet API classes, deployed as `ROOT.war` on the official `tomcat:11.0` image.
There is no framework in the WAR on purpose: this example is about Tomcat's own startup and request
pipeline and about adding the agent the way a Tomcat deployment does it, through `CATALINA_OPTS`.

The WAR carries one of each thing Tomcat initializes while deploying a web application:

| Class | Registered as | Initialized in |
|---|---|---|
| `StartupListener` | `@WebListener` `ServletContextListener` | `StandardContext.listenerStart`: places one warm-up order and stores the `OrderService` in the servlet context |
| `TimingFilter` | `@WebFilter("/*")` | `StandardContext.filterStart`: `init()` at deployment, then `doFilter` around every request, adding `X-Elapsed-Micros` and `X-Uptime-Millis` response headers |
| `OrderServlet` | `@WebServlet(urlPatterns = "/orders", loadOnStartup = 1)` | `StandardContext.loadOnStartup`: `init()` at deployment picks the `OrderService` up from the context |
| `HealthServlet` | `@WebServlet("/healthz")`, no `loadOnStartup` | Lazily, on its first request, under `StandardWrapper.allocate` |

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

`catalina.sh` applies `CATALINA_OPTS` only to the server process, which `run` and `start` launch, unlike
`JAVA_OPTS`, which is also read by `catalina.sh stop`. Tomcat logs the options it was started with,
so the agent line appears in the container log as a `Command line argument:` entry.

## Agent settings

Set in [`compose.yaml`](compose.yaml) through `CATALINA_OPTS`, alongside the shared JMX flags:

```text
-javaagent:/usr/local/tomcat/verbatime-agent.jar=waitstart=60s,roots=org.apache.catalina.startup.Bootstrap::main+org.apache.catalina.connector.CoyoteAdapter::service
```

| Setting | Value |
|---|---|
| Instrumented classes | Tomcat from the common loader and the WAR from the webapp loader |
| `waitstart=60s` | Pauses at `Bootstrap.main` for up to 60 seconds until JMC starts a recording |
| `roots=` | Sets the roots below. You can change them in JMC |

## Roots to try

| Root | What one tree covers |
|---|---|
| `org.apache.catalina.startup.Bootstrap::main` | Server startup, when the recording is started while the JVM is paused at `main()`: `Catalina.load`, which parses `server.xml` and initializes the connectors, followed by `Catalina.start`, which starts the services and deploys the WAR with `StartupListener`, `TimingFilter.init`, and `OrderServlet.init` under `StandardContext.startInternal`. |
| `org.apache.catalina.connector.CoyoteAdapter::service` | One HTTP request: request mapping, the valve pipeline with the filter chain and the servlet, and the response flush. |

The startup tree never closes: after startup, `Catalina.start` blocks in `await()` on the main
thread for as long as the server runs, so the session is flushed as unclosed when the recording is
stopped. Startup is complete where `LifecycleBase.start` returns under `Catalina.start`, right
before the `Server startup in [...] milliseconds` log call. Everything after that is the wait.

The WAR is deployed on the main thread only because the host's `startStopThreads` is at its
default of 1, which makes Tomcat run deployment inline. With a larger value, deployment moves to a
pool thread and leaves the startup tree.

## Endpoints

| Path | Response |
|---|---|
| `GET /healthz` | `ok` |
| `GET /orders?sku=widget&qty=3` | `sku=widget qty=3 cents=... tx=...` |
| `GET /orders?sku=widget&qty=500` | `409` from `OutOfStockException` |
