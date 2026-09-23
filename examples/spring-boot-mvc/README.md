# spring-boot-mvc

A Spring Boot MVC application on embedded Tomcat, packaged as a fat jar and run with `java -jar`.
It shows how to add the agent to any fat-jar deployment.

## Run

```sh
docker compose up --build
curl 'http://localhost:8080/orders?sku=widget&qty=3'
```

Connect JMC as described in [Try one](../README.md#try-one).

## Roots to try

Both are set by `roots=` in [`compose.yaml`](compose.yaml). You can change them in JMC.

| Root | One tree is |
|---|---|
| `org.springframework.web.servlet.DispatcherServlet::doDispatch` | One HTTP request, from handler mapping through the controller to the JSON response |
| `org.springframework.boot.SpringApplication::run` | Application startup, if you start recording while the application waits at `main()` |

## How the agent is added

Through `JAVA_TOOL_OPTIONS` in [`compose.yaml`](compose.yaml):

```text
-javaagent:/app/verbatime-agent.jar=waitstart=60s,roots=org.springframework.web.servlet.DispatcherServlet::doDispatch+org.springframework.boot.SpringApplication::run
```

## Notes

- `GET /orders?sku=widget&qty=500` returns `409`. The `OutOfStockException` is mapped by a
  `@RestControllerAdvice`.
- The startup tree includes `StartupRunner`, which places one warm-up order.
- `GET /healthz` returns `ok`.
