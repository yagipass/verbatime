# jetty-war

The WAR from [`tomcat-war`](../tomcat-war/) deployed as `ROOT.war` on the official `jetty:12.1`
image. It carries a `ServletContextListener`, a `Filter`, a `loadOnStartup` servlet, and a lazily
initialized servlet. This directory has no sources of its own: the Dockerfile builds `tomcat-war`,
copies its WAR, and enables the `ee11-deploy` and `ee11-annotations` modules so that WARs in
`/var/lib/jetty/webapps` are deployed and their `@WebServlet`, `@WebFilter`, and `@WebListener`
annotations are scanned. The example is about Jetty's own startup and request pipeline and about
adding the agent the way the Jetty image expects it, through `JAVA_OPTIONS`.

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

Use `down` and `up`, not `stop` and `start`, after changing `VERBATIME_EXTRA` or `JMX_HOST`: the
image's entrypoint expands `JAVA_OPTIONS` through `start.jar` into `/var/lib/jetty/jetty.start` on
the container's first start and reuses that file afterwards.

## Agent settings

Set in [`compose.yaml`](compose.yaml) through `JAVA_OPTIONS`, alongside the shared JMX flags:

```text
-javaagent:/var/lib/jetty/verbatime-agent.jar=waitstart=60s,roots=org.eclipse.jetty.xml.XmlConfiguration::main+org.eclipse.jetty.server.Server::handle
```

| Setting | Value |
|---|---|
| Instrumented classes | Jetty and the WAR |
| `waitstart=60s` | Pauses at `org.eclipse.jetty.xml.XmlConfiguration.main`, the main class of the command that `start.jar` expands, for up to 60 seconds until JMC starts a recording |
| `roots=` | Sets the roots below. You can change them in JMC |

## Roots to try

| Root | What one tree covers |
|---|---|
| `org.eclipse.jetty.xml.XmlConfiguration::main` | Server startup, when the recording is started while the JVM is paused at `main()`: XML wiring of the server and connectors, the deployer scanning `webapps/`, the `WebAppContext` start with annotation scanning, `StartupListener`, `TimingFilter.init`, and `OrderServlet.init`. |
| `org.eclipse.jetty.server.Server::handle` | One HTTP request on a `qtp...` thread: context and servlet handler selection, the filter chain, the servlet, and the response commit. |

## Endpoints

| Path | Response |
|---|---|
| `GET /healthz` | `ok` |
| `GET /orders?sku=widget&qty=3` | `sku=widget qty=3 cents=... tx=...` |
| `GET /orders?sku=widget&qty=500` | `409` from `OutOfStockException` |
