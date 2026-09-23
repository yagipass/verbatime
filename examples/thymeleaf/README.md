# thymeleaf

Spring Boot 4.1 MVC with `spring-boot-starter-thymeleaf`, packaged as a fat jar and run with
`java -jar`. `GET /orders` runs the workload and renders the receipt as an HTML page. The other
Spring examples answer JSON, while this one shows what server-side template rendering adds to a request
tree, including the one-time template parse on the first request.

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

Thymeleaf caches templates, so only the first request's tree contains the parse. Set
`spring.thymeleaf.cache=false` to see the parse in every tree.

## Agent settings

Set in [`compose.yaml`](compose.yaml) through `JAVA_TOOL_OPTIONS`, alongside the shared JMX flags:

```text
-javaagent:/app/verbatime-agent.jar=waitstart=60s,roots=org.springframework.boot.SpringApplication::run+org.springframework.web.servlet.DispatcherServlet::doDispatch
```

| Setting | Value |
|---|---|
| Instrumented classes | Spring, embedded Tomcat, Thymeleaf, and the application |
| `waitstart=60s` | Pauses at the fat jar's `JarLauncher.main` for up to 60 seconds until JMC starts a recording |
| `roots=` | Sets the roots below. You can change them in JMC |

## Roots to try

| Root | What one tree covers |
|---|---|
| `org.springframework.boot.SpringApplication::run` | Application startup, when the recording is started while the JVM is paused at `main()`: context refresh, the `SpringTemplateEngine` and its resolvers, embedded Tomcat, and the application's `StartupRunner`, an `ApplicationRunner` that places one warm-up order. |
| `org.springframework.web.servlet.DispatcherServlet::doDispatch` | One HTTP request: handler mapping, the controller and workload, view resolution, and `ThymeleafView.render` writing the page. The first request's tree also contains parsing `order.html`, while later trees only render. |

## Endpoints

| Path | Response |
|---|---|
| `GET /healthz` | `ok` |
| `GET /orders?sku=widget&qty=3` | An HTML page with the receipt and one line per unit |
| `GET /orders?sku=widget&qty=500` | `409` from `OutOfStockException`, rendered as the `error.html` page |
