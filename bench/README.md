# Overhead benchmark

Measures how much the agent slows down a real HTTP request. It runs by hand and is not part of
`mvn verify`.

## What it compares

The benchmark runs [`examples/jdbc-hibernate`](../examples/jdbc-hibernate/) three ways, with
everything instrumented, which is the agent's default.

| Variant | JVM |
|---|---|
| A | No agent |
| B | Agent attached, not recording |
| D | Agent recording `DispatcherServlet::doDispatch` |

It sends load to `GET /healthz`, which goes through the framework only, and `GET /orders/recent`,
which runs a Hibernate query and returns ten entities as JSON.

## Run

You need Docker with Compose v2, JDK 25, and free ports 8080 and 7091. From the repository root:

```sh
nix develop .#agent
mvn -q -pl modules/format package -DskipTests
java -cp modules/format/target/classes bench/Bench.java --quick
```

`--quick` takes a few minutes and checks the setup. Drop it for the full run, which takes about an
hour. Results go to `bench/results/<timestamp>/`. Open `summary.md` there.

## Reading the summary

- Each cell is the median over rounds, with the range in brackets.
- The Δ columns show B or D minus A, so they are the agent's cost per request.
- CPU ms/req is the most stable number. Latency is noisier.
- Expect A ≤ B ≤ D. Anything else is noise.

## Options

| Option | Default |
|---|---|
| `--rounds` | 5 |
| `--duration`, `--warmup` | 30 s each |
| `--rate` | 200 requests per second |
| `--variants` | `A,B,D`. `A` is required |
| `--root` | `org.springframework.web.servlet.DispatcherServlet::doDispatch` |
| `--pin`, `--no-pin` | Pins CPUs as `app=0-3,db=4-5,load=6-7` |
| `--with-orders` | Also loads `GET /orders`, which sleeps 11 ms per request |

## Notes

- Docker Desktop adds VM noise. Compare within one run, and use a Linux host for numbers to quote.
- It measures one request through Spring MVC and Hibernate. Tight loops of tiny methods and many
  threads recording at once are not covered.
