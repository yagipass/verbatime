# junit-maven

Two JUnit 6 tests of the workload, run by Maven Surefire 3.6 with the agent attached to the forked
test JVM. The unit of work is one test method, or one whole test run. The fork exits as soon as the
tests are done, too fast for anyone to start a recording by hand, so the agent records the run by
itself with `record=startup` and writes it to a file on the host, which is then opened in JMC from
disk. No JMX and no JMC are involved while the tests run.

## Run

```sh
docker compose run --rm --build test
```

Maven first installs the shared `workload` module, then runs the tests. The forked JVM starts
recording in the agent's `premain`, the two tests run in well under a second, and the agent's
shutdown hook closes the recording as the fork exits, leaving it in the file below, as described in
[Examples whose JVM exits on its own](../README.md#examples-whose-jvm-exits-on-its-own):

```text
recordings/junit-maven.vbtm
```

The `closed at shutdown` line does not reach Maven's output, because Surefire stops forwarding the
fork's output once the tests are reported, but the file is complete. `docker compose down -v` also
removes the `m2` volume that caches the Maven repository.

## Agent settings

Set in [`pom.xml`](pom.xml) as Surefire's `argLine`, through the `verbatime-docker` profile:

```text
-javaagent:/work/verbatime-agent.jar=record=startup,roots=org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor::execute,out=/work/recordings/junit-maven.vbtm
```

| Setting | Value |
|---|---|
| Instrumented classes | Surefire's booter, the JUnit Platform and Jupiter engine, the workload, and the test class |
| `record=startup` | Records from the agent's `premain` until the JVM exits, with no JMX control registered. `roots=` and `out=` are required in this mode |
| `roots=…TestMethodTestDescriptor::execute` | One tree per test method. The root is resolved when the Jupiter engine class is loaded, which is well after `premain` |
| `out=/work/recordings/junit-maven.vbtm` | Writes the recording to `./recordings/` on the host through a bind mount. The default spool would be deleted when the fork exits |
| `forkCount=1`, `reuseForks=true` | One forked JVM for the whole run. More forks would truncate the same file, while `forkCount=0` runs the tests inside the Maven JVM and ignores `argLine` |

Surefire's `argLine` is written as `@{argLine}` over an `argLine` property, so a coverage tool that
also sets `argLine`, as JaCoCo does, is composed with the agent instead of overwriting it. The
recording path must be absolute, because the fork's working directory is the module directory. On
the host, `mvn test` after `mvn -f ../workload/pom.xml install` runs the tests without the agent,
because the profile is active only where `/work/verbatime-agent.jar` exists.

## Roots to try

Edit `roots=` in [`pom.xml`](pom.xml) and run the command again.

| Root | What one tree covers |
|---|---|
| `org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor::execute` | One test method on the `main` thread: extensions, `@BeforeEach`, the test itself with the workload, and `@AfterEach`. The `rejectsOutOfStock` tree carries the `OutOfStockException` thrown in `InventoryRepository.reserve` under `assertThrows`. The default here, and it records two trees per run. |
| `org.junit.platform.launcher.core.EngineExecutionOrchestrator::execute` | The whole test run in one tree: engine and test discovery results, the test class, both test methods, and the listeners. |

Several roots at once are written with `+`, as in `roots=a.B::m+c.D::n`.

## Driving it from JMC instead

To pick roots interactively, drop `record=startup` and `roots=` and use the default
`record=ondemand` with `waitstart=60s` and the JMX flags, as the server examples do. The fork then
pauses at `org.apache.maven.surefire.booter.ForkedBooter.main` until JMC starts a recording, port
7091 has to be published with `docker compose run --service-ports`, and the JMC connection drops when
the fork exits, so the recording is still read from `recordings/junit-maven.vbtm` afterwards.

## Tests

| Test | What it does |
|---|---|
| `placesOrder` | `OrderService.placeOrder("widget", 3)` and asserts the receipt |
| `rejectsOutOfStock` | `placeOrder("widget", InventoryRepository.STOCK + 1)` and asserts `OutOfStockException` |
